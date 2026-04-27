package com.tom.hqspeaker.client.audio;

import com.tom.hqspeaker.HQSpeakerMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class StreamingAudioSource {

    
    public interface MetadataListener {
        void onMetadata(String rawTitle, String stationName, String genre, String description);
    }

    public enum StreamType {
        MP3_STREAM,
        HLS_STREAM,
        TS_STREAM
    }

    private final String url;
    private final StreamType type;
    private final float volume;
    private MetadataListener metadataListener;

    
    private volatile String icyName        = "";
    private volatile String icyGenre       = "";
    private volatile String icyDescription = "";

    
    public static final int PREBUFFER_CHUNKS = 16;

    
    private final BlockingQueue<byte[]> pcmQueue = new LinkedBlockingQueue<>(200);
    private final AtomicBoolean running  = new AtomicBoolean(false);
    private final AtomicBoolean stopped  = new AtomicBoolean(false);
    private final AtomicReference<AudioFormat> detectedFormat = new AtomicReference<>();

    private Thread streamThread;

    
    private HLSPlaylistParser.Playlist currentPlaylist;
    private int  currentSegmentIndex = 0;
    private long lastPlaylistFetch   = 0;

    public StreamingAudioSource(String url, StreamType type, float volume) {
        this.url    = url;
        this.type   = type;
        this.volume = volume;
    }

    public void setMetadataListener(MetadataListener l) { this.metadataListener = l; }

    
    public void start() {
        if (running.compareAndSet(false, true)) {
            stopped.set(false);
            streamThread = new Thread(this::streamLoop, "StreamingAudio-" + type);
            streamThread.setDaemon(true);
            streamThread.start();
            HQSpeakerMod.log("StreamingAudio: Started " + type + " from " + url);
        }
    }

    public void stop() {
        stopped.set(true);
        running.set(false);
        if (streamThread != null) streamThread.interrupt();
        pcmQueue.clear();
        HQSpeakerMod.log("StreamingAudio: Stopped");
    }

    public boolean isRunning() { return running.get() && !stopped.get(); }
    public AudioFormat getFormat() { return detectedFormat.get(); }
    public boolean hasData()      { return !pcmQueue.isEmpty(); }
    public int     getQueueSize() { return pcmQueue.size(); }

    
    private static final ByteBuffer EMPTY_SENTINEL = ByteBuffer.allocateDirect(0).asReadOnlyBuffer();

    public ByteBuffer readPCM(int maxBytes) {
        if (!isRunning() && pcmQueue.isEmpty()) return null; 

        byte[] data = pcmQueue.poll(); 
        if (data == null) return EMPTY_SENTINEL; 

        
        int len = Math.min(data.length, maxBytes);
        return ByteBuffer.wrap(data, 0, len);
    }

    
    private void streamLoop() {
        try {
            switch (type) {
                case MP3_STREAM -> streamMP3();
                case HLS_STREAM -> streamHLS();
                case TS_STREAM  -> streamTS();
            }
        } catch (Exception e) {
            if (!stopped.get()) {
                HQSpeakerMod.error("StreamingAudio: Stream error — " + e.getMessage());
            }
        } finally {
            running.set(false);
        }
    }

    
    private void streamMP3() throws IOException {
        
        
        HttpURLConnection conn = openConnection(url);

        String metaIntStr = conn.getHeaderField("icy-metaint");
        int metaInterval  = (metaIntStr != null) ? safeParseInt(metaIntStr, 0) : 0;
        HQSpeakerMod.log("StreamingAudio: icy-metaint=" + metaInterval);

        InputStream rawStream   = conn.getInputStream();
        InputStream audioStream = (metaInterval > 0)
                ? new IcyInputStream(rawStream, metaInterval)
                : new BufferedInputStream(rawStream, 65536);

        javazoom.jl.decoder.Bitstream  bitstream = new javazoom.jl.decoder.Bitstream(audioStream);
        javazoom.jl.decoder.Decoder    decoder   = new javazoom.jl.decoder.Decoder();

        
        int srcChannels = 2;
        float srcRate   = 44100f;
        boolean formatDetected = false;

        try {
            while (!stopped.get()) {
                javazoom.jl.decoder.Header header;
                try {
                    header = bitstream.readFrame();
                } catch (javazoom.jl.decoder.BitstreamException e) {
                    
                    HQSpeakerMod.warn("StreamingAudio: Bitstream sync error, resyncing — " + e.getMessage());
                    continue;
                }

                if (header == null) {
                    
                    HQSpeakerMod.log("StreamingAudio: MP3 stream ended (server EOF)");
                    break;
                }

                
                if (!formatDetected) {
                    srcRate     = header.frequency();
                    srcChannels = (header.mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL) ? 1 : 2;
                    AudioFormat monoFmt = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            srcRate, 16, 1, 2, srcRate, false);
                    detectedFormat.set(monoFmt);
                    formatDetected = true;
                    HQSpeakerMod.log(String.format(
                            "StreamingAudio: JLayer MP3 → %.0f Hz %d ch", srcRate, srcChannels));
                }

                
                javazoom.jl.decoder.SampleBuffer output;
                try {
                    output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                } catch (javazoom.jl.decoder.DecoderException e) {
                    HQSpeakerMod.warn("StreamingAudio: Frame decode error — " + e.getMessage());
                    bitstream.closeFrame();
                    continue;
                }

                
                short[] samples   = output.getBuffer();
                int     frameLen  = output.getBufferLength(); 
                int     channels  = srcChannels;
                byte[]  mono      = new byte[(frameLen / channels) * 2];
                int     outIdx    = 0;

                for (int i = 0; i < frameLen; i += channels) {
                    
                    long sum = 0;
                    for (int c = 0; c < channels; c++) {
                        sum += samples[i + c];
                    }
                    short s = (short)(sum / channels);
                    mono[outIdx++] = (byte)(s & 0xFF);          
                    mono[outIdx++] = (byte)((s >> 8) & 0xFF);   
                }

                queuePCM(mono);
                bitstream.closeFrame();
            }
        } finally {
            try { bitstream.close(); } catch (Exception ignored) {}
            try { rawStream.close(); } catch (IOException ignored) {}
            flushRemainingPCM(); 
        }
    }

    
    private void streamHLS() throws Exception {
        while (!stopped.get()) {
            long now = System.currentTimeMillis();
            if (currentPlaylist == null ||
                    (currentPlaylist.isLive() && now - lastPlaylistFetch > 5000)) {

                currentPlaylist   = HLSPlaylistParser.fetchAndParse(url);
                lastPlaylistFetch = now;

                if (currentPlaylist.type == HLSPlaylistParser.PlaylistType.MASTER) {
                    HLSPlaylistParser.VariantStream variant =
                            HLSPlaylistParser.selectBestVariant(currentPlaylist.variants, 128_000);
                    if (variant == null) {
                        HQSpeakerMod.error("StreamingAudio: No suitable HLS variant");
                        return;
                    }
                    HQSpeakerMod.log("StreamingAudio: HLS variant " + variant.bandwidth + " bps");
                    currentPlaylist = HLSPlaylistParser.fetchAndParse(variant.url);
                }
            }

            List<HLSPlaylistParser.MediaSegment> segs = currentPlaylist.segments;
            while (currentSegmentIndex < segs.size() && !stopped.get()) {
                HLSPlaylistParser.MediaSegment seg = segs.get(currentSegmentIndex);
                if (seg.url.toLowerCase().endsWith(".aac")) {
                    playAACSegment(seg.url);
                } else {
                    playTSSegment(seg.url);
                }
                currentSegmentIndex++;
            }

            if (currentPlaylist.isLive()) {
                Thread.sleep((long)(currentPlaylist.targetDuration * 500));
                currentPlaylist = null;
            } else {
                break;
            }
        }
    }

    
    private void streamTS() throws IOException {
        HttpURLConnection conn = openConnection(url);
        try (InputStream is = conn.getInputStream()) {
            TSDemuxer demuxer = new TSDemuxer();
            List<TSDemuxer.AudioFrame> frames = demuxer.demux(is);
            HQSpeakerMod.log("StreamingAudio: Demuxed " + frames.size() + " TS frames");
            for (TSDemuxer.AudioFrame frame : frames) {
                if (stopped.get()) break;
                byte[] pcm = decodeAudioFrame(frame);
                if (pcm != null) queuePCM(pcm);
            }
        }
    }

    private void playTSSegment(String segUrl) {
        try {
            List<TSDemuxer.AudioFrame> frames = TSDemuxer.fetchAndDemux(segUrl);
            for (TSDemuxer.AudioFrame frame : frames) {
                if (stopped.get()) break;
                byte[] pcm = decodeAudioFrame(frame);
                if (pcm != null) queuePCM(pcm);
            }
        } catch (Exception e) {
            HQSpeakerMod.warn("StreamingAudio: TS segment failed — " + e.getMessage());
        }
    }

    private void playAACSegment(String segUrl) {
        try {
            HttpURLConnection conn = openConnection(segUrl);
            byte[] data;
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
                data = baos.toByteArray();
            }

            AudioInputStream ais = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(data));
            AudioFormat fmt = ais.getFormat();
            if (detectedFormat.get() == null) {
                detectedFormat.set(new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        fmt.getSampleRate(), 16, 1, 2, fmt.getSampleRate(), false));
            }
            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(), 16, fmt.getChannels(),
                    fmt.getChannels() * 2, fmt.getSampleRate(), false);
            byte[] pcm = AudioSystem.getAudioInputStream(pcmFmt, ais).readAllBytes();
            queuePCM(toMono16LE(pcm, 0, pcm.length, fmt.getChannels(), fmt.isBigEndian()));
        } catch (Exception e) {
            HQSpeakerMod.warn("StreamingAudio: AAC segment failed — " + e.getMessage());
        }
    }

    
    private byte[] decodeAudioFrame(TSDemuxer.AudioFrame frame) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(frame.data));
            AudioFormat fmt = ais.getFormat();
            if (detectedFormat.get() == null) {
                detectedFormat.set(new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        fmt.getSampleRate(), 16, 1, 2, fmt.getSampleRate(), false));
            }
            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(), 16, fmt.getChannels(),
                    fmt.getChannels() * 2, fmt.getSampleRate(), false);
            byte[] raw = AudioSystem.getAudioInputStream(pcmFmt, ais).readAllBytes();
            return toMono16LE(raw, 0, raw.length, fmt.getChannels(), false);
        } catch (UnsupportedAudioFileException e) {
            return frame.data; 
        } catch (Exception e) {
            HQSpeakerMod.warn("StreamingAudio: frame decode failed — " + e.getMessage());
            return null;
        }
    }

    
    private static byte[] toMono16LE(byte[] src, int off, int len,
                                     int channels, boolean bigEndian) {
        if (channels <= 0) channels = 1;
        int frames = len / (channels * 2);

        if (channels == 1 && !bigEndian) {
            byte[] out = new byte[frames * 2];
            System.arraycopy(src, off, out, 0, frames * 2);
            return out;
        }

        byte[] out = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            long sum = 0;
            for (int ch = 0; ch < channels; ch++) {
                int idx = off + (i * channels + ch) * 2;
                short s;
                if (bigEndian) {
                    
                    s = (short)(((src[idx] & 0xFF) << 8) | (src[idx + 1] & 0xFF));
                } else {
                    s = (short)((src[idx] & 0xFF) | ((src[idx + 1] & 0xFF) << 8));
                }
                sum += s;
            }
            short mono = (short)(sum / channels);
            out[i * 2]     = (byte)(mono & 0xFF);
            out[i * 2 + 1] = (byte)((mono >> 8) & 0xFF);
        }
        return out;
    }

    
    private static final int CHUNK_BYTES = 9600; 
    private final byte[] accumulator = new byte[CHUNK_BYTES * 2]; 
    private int accumLen = 0;

    private void flushAccumulator(boolean force) {
        int start = 0;
        while (accumLen - start >= CHUNK_BYTES || (force && start < accumLen)) {
            if (stopped.get()) return;
            int len = Math.min(CHUNK_BYTES, accumLen - start);
            byte[] chunk = new byte[len];
            System.arraycopy(accumulator, start, chunk, 0, len);
            start += len;
            try {
                if (!pcmQueue.offer(chunk, 50, TimeUnit.MILLISECONDS)) {
                    HQSpeakerMod.warn("StreamingAudio: PCM queue full, dropping chunk");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        if (start > 0 && start < accumLen) {
            System.arraycopy(accumulator, start, accumulator, 0, accumLen - start);
        }
        accumLen = (start >= accumLen) ? 0 : accumLen - start;
    }

    private void queuePCM(byte[] pcm) {
        if (pcm == null || pcm.length == 0) return;

        
        if (Math.abs(volume - 1.0f) > 0.001f) {
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                short s = (short)((pcm[i] & 0xFF) | ((pcm[i + 1] & 0xFF) << 8));
                long scaled = (long)(s * volume);
                s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
                pcm[i]     = (byte)(s & 0xFF);
                pcm[i + 1] = (byte)((s >> 8) & 0xFF);
            }
        }

        
        int needed = accumLen + pcm.length;
        if (needed > accumulator.length) {
            
            flushAccumulator(false);
        }

        
        int src = 0;
        while (src < pcm.length) {
            int space = accumulator.length - accumLen;
            int copy  = Math.min(space, pcm.length - src);
            System.arraycopy(pcm, src, accumulator, accumLen, copy);
            accumLen += copy;
            src += copy;
            flushAccumulator(false);
        }
    }

    
    private void flushRemainingPCM() {
        if (accumLen > 0) flushAccumulator(true);
    }

    
    private HttpURLConnection openConnection(String urlStr) throws IOException {
        URL u = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("User-Agent", "HQSpeaker-Streaming/1.0");
        conn.setRequestProperty("Icy-MetaData", "1");
        conn.setInstanceFollowRedirects(false);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + urlStr);

        
        String n = conn.getHeaderField("icy-name");
        String g = conn.getHeaderField("icy-genre");
        String d = conn.getHeaderField("icy-description");
        if (n != null) icyName        = n;
        if (g != null) icyGenre       = g;
        if (d != null) icyDescription = d;
        return conn;
    }

    
    private void notifyMetadata(String rawTitle, String station, String genre, String desc) {
        MetadataListener l = metadataListener;
        if (l == null) return;
        try { l.onMetadata(rawTitle, station, genre, desc); }
        catch (Exception e) { HQSpeakerMod.warn("StreamingAudio: metadata listener: " + e); }
    }

    
    private int readAtLeast(InputStream is, byte[] buf, int minBytes) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = is.read(buf, total, buf.length - total);
            if (r == -1) break;
            total += r;
            if (total >= minBytes) break; 
        }
        return total;
    }

    private static int safeParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    
    private final class IcyInputStream extends FilterInputStream {
        private final int metaInterval;
        private int audioByteCount = 0; 

        IcyInputStream(InputStream in, int metaInterval) {
            super(new BufferedInputStream(in, 65536));
            this.metaInterval = metaInterval;
        }

        @Override
        public int read() throws IOException {
            
            if (audioByteCount < metaInterval) {
                int b = super.read();
                if (b != -1) audioByteCount++;
                return b;
            }
            checkMetaBoundary();
            int b = super.read();
            if (b != -1) audioByteCount++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkMetaBoundary();
            
            int canRead = Math.min(len, metaInterval - audioByteCount);
            if (canRead <= 0) canRead = len; 
            int n = super.read(b, off, canRead);
            if (n > 0) audioByteCount += n;
            return n;
        }

        private void checkMetaBoundary() throws IOException {
            if (audioByteCount < metaInterval) return;
            int lenByte = super.read();
            audioByteCount = 0;
            if (lenByte <= 0) return; 

            int metaBytes = lenByte * 16;
            
            if (metaBytes > 4096) {
                HQSpeakerMod.warn("StreamingAudio: ICY meta block too large (" + metaBytes + "), skipping");
                long remaining = metaBytes;
                while (remaining > 0) {
                    long skipped = super.skip(remaining);
                    if (skipped <= 0) break;
                    remaining -= skipped;
                }
                return;
            }

            byte[] meta = new byte[metaBytes];
            int got = 0;
            while (got < metaBytes) {
                int r = super.read(meta, got, metaBytes - got);
                if (r == -1) break;
                got += r;
            }

            String metaStr = new String(meta, 0, got,
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            if (metaStr.isEmpty()) return;

            HQSpeakerMod.log("StreamingAudio: ICY metadata = " + metaStr);

            
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("StreamTitle='([^']*)'").matcher(metaStr);
            if (m.find()) {
                String rawTitle = m.group(1).trim();
                notifyMetadata(rawTitle, icyName, icyGenre, icyDescription);
            }
        }
    }
}
