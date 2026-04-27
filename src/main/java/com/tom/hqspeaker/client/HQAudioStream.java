package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.client.audio.SharedStreamingGroup;
import com.tom.hqspeaker.client.audio.StreamingAudioSource;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import net.minecraft.client.sounds.AudioStream;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HQAudioStream implements AudioStream {

    public static final int SAMPLE_RATE = 48_000;

    private static final ExecutorService DECODER =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HQSpeaker-Decoder");
            t.setDaemon(true);
            return t;
        });

    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private volatile javax.sound.sampled.AudioFormat audioFormat = null;
    private volatile boolean hasRealData = false;
    private volatile boolean closed      = false;
    private volatile boolean decoding    = false;

    
    private StreamingAudioSource streamingSource = null;
    private SharedStreamingGroup.Tap sharedStreamingTap = null;
    private volatile boolean isStreaming = false;
    private volatile boolean streamReady = false;

    private static final int MAX_DECODED_PCM_BYTES = 64 * 1024 * 1024;
    private static final int MAX_QUEUE_CHUNKS = 64;

    
    public boolean hasRealData() {
        return hasRealData || (sharedStreamingTap != null && sharedStreamingTap.hasData()) || (streamingSource != null && streamingSource.hasData());
    }

    
    public boolean isStreamReady() {
        if (!isStreaming) return hasRealData;
        if (streamReady) return true;
        int q = sharedStreamingTap != null ? sharedStreamingTap.getQueueSize() : (streamingSource != null ? streamingSource.getQueueSize() : 0);
        javax.sound.sampled.AudioFormat sfmt = sharedStreamingTap != null ? sharedStreamingTap.getFormat() : (streamingSource != null ? streamingSource.getFormat() : null);
        if (sfmt != null
                && q >= StreamingAudioSource.PREBUFFER_CHUNKS) {
            streamReady = true;
            HQSpeakerMod.log("HQAudioStream: prebuffer ready, format=" + sfmt);
        }
        return streamReady;
    }

    public boolean isDrained() {
        if (isStreaming) {
            boolean anyBackend = sharedStreamingTap != null || streamingSource != null;
            if (!anyBackend) return true;
            boolean running = (sharedStreamingTap != null && sharedStreamingTap.isRunning())
                || (streamingSource != null && streamingSource.isRunning());
            boolean hasDataQueued = (sharedStreamingTap != null && sharedStreamingTap.hasData())
                || (streamingSource != null && streamingSource.hasData());
            return !running && !hasDataQueued;
        }
        if (decoding) return false;
        if (!closed)  return false;
        synchronized (queue) { return queue.isEmpty(); }
    }

    
    public void push(HQSpeakerAudioPacket packet) {
        if (closed) return;
        switch (packet.format) {
            case PCM_S16LE  -> pushPCM(packet.data, null);
            case OGG_VORBIS -> submitDecode(packet.data, "ogg");
            case MP3        -> submitDecode(packet.data, "mp3");
            case AUDIO_FILE -> submitDecode(packet.data, detectPackedAudioLabel(packet.data));
            case MP3_STREAM, HLS_STREAM, TS_STREAM -> startStreaming(packet);
        }
    }

    public boolean isEmpty() {
        if (isStreaming) {
            return (sharedStreamingTap == null || !sharedStreamingTap.hasData()) && (streamingSource == null || !streamingSource.hasData());
        }
        synchronized (queue) { return queue.isEmpty(); }
    }

    
    @Override
    public javax.sound.sampled.AudioFormat getFormat() {
        
        
        if (isStreaming) {
            javax.sound.sampled.AudioFormat fmt = sharedStreamingTap != null ? sharedStreamingTap.getFormat() : (streamingSource != null ? streamingSource.getFormat() : null);
            if (fmt != null) return fmt;
        }
        javax.sound.sampled.AudioFormat fmt = audioFormat;
        return fmt != null ? fmt : new javax.sound.sampled.AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    }

    
    private static final ByteBuffer SILENCE_FRAME =
        ByteBuffer.allocateDirect(4096); 

    @Override
    public ByteBuffer read(int maxBytes) throws IOException {
        
        if (isStreaming && (sharedStreamingTap != null || streamingSource != null)) {
            ByteBuffer data = sharedStreamingTap != null ? sharedStreamingTap.readPCM(maxBytes) : streamingSource.readPCM(maxBytes);
            if (data != null) {
                
                if (data.remaining() > 0) hasRealData = true;
                if (data.remaining() > 0) return data;
            } else {
                
                return null;
            }
            
            
            int silenceBytes = Math.min(maxBytes, SILENCE_FRAME.capacity());
            ByteBuffer slice = SILENCE_FRAME.duplicate();
            slice.limit(silenceBytes);
            slice.position(0);
            return slice;
        }

        
        if (!hasRealData) {
            if (closed && !decoding) return null;
            int silenceBytes = Math.min(maxBytes, SILENCE_FRAME.capacity());
            ByteBuffer slice = SILENCE_FRAME.duplicate();
            slice.limit(silenceBytes);
            slice.position(0);
            return slice;
        }
        synchronized (queue) {
            ByteBuffer chunk = queue.peek();
            if (chunk == null) {
                if (closed && !decoding) return null;
                int silenceBytes = Math.min(maxBytes, SILENCE_FRAME.capacity());
            ByteBuffer slice = SILENCE_FRAME.duplicate();
            slice.limit(silenceBytes);
            slice.position(0);
            return slice;
            }
            if (chunk.remaining() <= maxBytes) { queue.poll(); return chunk; }
            byte[] tmp = new byte[maxBytes];
            chunk.get(tmp);
            ByteBuffer head = ByteBuffer.allocateDirect(maxBytes);
            head.put(tmp); head.flip();
            return head;
        }
    }

    @Override
    public void close() {
        closed = true;
        
        
        if (!isStreaming) {
            if (sharedStreamingTap != null) {
                sharedStreamingTap.close();
                sharedStreamingTap = null;
            }
            if (streamingSource != null) {
                streamingSource.stop();
                streamingSource = null;
            }
        }
        synchronized (queue) { queue.clear(); }
    }

    
    public void closeAndStop() {
        closed = true;
        if (sharedStreamingTap != null) {
            sharedStreamingTap.close();
            sharedStreamingTap = null;
        }
        if (streamingSource != null) {
            streamingSource.stop();
            streamingSource = null;
        }
        synchronized (queue) { queue.clear(); }
    }

    
    private void startStreaming(HQSpeakerAudioPacket packet) {
        if (packet.streamUrl == null || packet.streamUrl.isEmpty()) {
            HQSpeakerMod.warn("HQAudioStream: no URL for streaming packet");
            return;
        }
        isStreaming = true;
        streamReady = false;
        hasRealData = true;

        StreamingAudioSource.StreamType streamType = switch (packet.format) {
            case MP3_STREAM -> StreamingAudioSource.StreamType.MP3_STREAM;
            case HLS_STREAM -> StreamingAudioSource.StreamType.HLS_STREAM;
            case TS_STREAM  -> StreamingAudioSource.StreamType.TS_STREAM;
            default         -> StreamingAudioSource.StreamType.MP3_STREAM;
        };

        final java.util.UUID sourceId = packet.source;
        if (packet.syncGroupId != null && packet.syncGroupSize > 1) {
            sharedStreamingTap = SharedStreamingGroup.open(packet.syncGroupId, packet.streamUrl, streamType, packet.volume, sourceId, Math.max(1, packet.syncGroupSize));
            streamingSource = null;
            HQSpeakerMod.log("HQAudioStream: joined shared " + packet.format + " group " + packet.syncGroupId + " from " + packet.streamUrl);
        } else {
            streamingSource = new StreamingAudioSource(packet.streamUrl, streamType, packet.volume);

            
            streamingSource.setMetadataListener((rawTitle, station, genre, desc) -> {
                try {
                    com.tom.hqspeaker.network.HQSpeakerNetwork.sendToServer(
                        new com.tom.hqspeaker.network.IcyMetaPacket(
                            sourceId, rawTitle, station, genre, desc));
                } catch (Exception e) {
                    HQSpeakerMod.warn("HQAudioStream: failed to send ICY meta — " + e.getMessage());
                }
            });

            streamingSource.start();
            HQSpeakerMod.log("HQAudioStream: started " + packet.format + " from " + packet.streamUrl);
        }
    }

    public boolean isStreaming() { return isStreaming; }
    public StreamingAudioSource getStreamingSource() { return streamingSource; }
    public SharedStreamingGroup.Tap getSharedStreamingTap() { return sharedStreamingTap; }

    
    private void submitDecode(byte[] raw, String label) {
        if (raw == null || raw.length == 0 || raw.length > HQSpeakerAudioPacket.MAX_BYTES) {
            HQSpeakerMod.warn("HQAudioStream: rejected invalid " + label + " payload");
            return;
        }
        decoding = true;
        DECODER.submit(() -> {
            try { if (!closed) decode(raw, label); }
            finally { decoding = false; }
        });
    }

    private void pushPCM(byte[] raw, javax.sound.sampled.AudioFormat fmt) {
        ByteBuffer buf = ByteBuffer.allocateDirect(raw.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(raw); buf.flip();
        synchronized (queue) {
            if (queue.size() >= MAX_QUEUE_CHUNKS) {
                HQSpeakerMod.warn("HQAudioStream: dropping PCM chunk; queue full");
                return;
            }
            if (fmt != null && audioFormat == null) audioFormat = fmt;
            else if (audioFormat == null)
                audioFormat = new javax.sound.sampled.AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            queue.add(buf);
            hasRealData = true;
        }
    }


    private static String detectPackedAudioLabel(byte[] raw) {
        if (raw == null || raw.length < 4) return "audio";
        if (raw.length >= 12
                && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F'
                && raw[8] == 'W' && raw[9] == 'A' && raw[10] == 'V' && raw[11] == 'E') return "wav";
        if (raw[0] == 'O' && raw[1] == 'g' && raw[2] == 'g' && raw[3] == 'S') return "ogg";
        if (raw[0] == 'I' && raw[1] == 'D' && raw[2] == '3') return "mp3";
        if ((raw[0] & 0xFF) == 0xFF && ((raw[1] & 0xE0) == 0xE0)) return "mp3";
        if (raw.length >= 12
                && raw[0] == 'F' && raw[1] == 'O' && raw[2] == 'R' && raw[3] == 'M'
                && raw[8] == 'A' && raw[9] == 'I' && raw[10] == 'F') return "aiff";
        if (raw[0] == '.' && raw[1] == 's' && raw[2] == 'n' && raw[3] == 'd') return "au";
        if (raw.length >= 12
                && raw[4] == 'f' && raw[5] == 't' && raw[6] == 'y' && raw[7] == 'p') return "mp4/aac";
        return "audio";
    }

    
    private static final class OggResult {
        final byte[] pcm; final int sampleRate;
        OggResult(byte[] pcm, int sr) { this.pcm = pcm; this.sampleRate = sr; }
    }

    private OggResult decodeOggSTB(byte[] raw) throws Exception {
        ByteBuffer nativeBuf = MemoryUtil.memAlloc(raw.length);
        try {
            nativeBuf.put(raw).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer chBuf = stack.mallocInt(1);
                IntBuffer srBuf = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(nativeBuf, chBuf, srBuf);
                if (pcm == null) throw new Exception("STBVorbis: decode failed");
                try {
                    int ch = chBuf.get(0), sr = srBuf.get(0), n = pcm.remaining();
                    if (ch <= 0 || ch > 8 || sr <= 0 || n <= 0 || (long)n * 2L > MAX_DECODED_PCM_BYTES)
                        throw new Exception("STBVorbis: decoded PCM too large/invalid");
                    if (ch == 1) {
                        byte[] b = new byte[n * 2];
                        for (int i = 0; i < n; i++) {
                            short s = pcm.get(i);
                            b[i*2] = (byte)(s & 0xFF); b[i*2+1] = (byte)((s>>8)&0xFF);
                        }
                        return new OggResult(b, sr);
                    }
                    int frames = n / ch;
                    if ((long)frames * 2L > MAX_DECODED_PCM_BYTES) throw new Exception("STBVorbis: decoded PCM too large");
                    byte[] mono = new byte[frames * 2];
                    for (int i = 0; i < frames; i++) {
                        long sum = 0;
                        for (int c = 0; c < ch; c++) sum += pcm.get(i*ch+c);
                        short m = (short)(sum/ch);
                        mono[i*2] = (byte)(m&0xFF); mono[i*2+1] = (byte)((m>>8)&0xFF);
                    }
                    return new OggResult(mono, sr);
                } finally { MemoryUtil.memFree(pcm); }
            }
        } finally { MemoryUtil.memFree(nativeBuf); }
    }

    private void decode(byte[] raw, String label) {
        try {
            final byte[] monoBytes;
            final int actualRate;

            String kind = "audio".equals(label) ? detectPackedAudioLabel(raw) : label;
            if ("ogg".equals(kind)) {
                OggResult r = decodeOggSTB(raw);
                monoBytes = r.pcm; actualRate = r.sampleRate;
            } else {
                AudioInputStream encoded = AudioSystem.getAudioInputStream(new ByteArrayInputStream(raw));
                javax.sound.sampled.AudioFormat encFmt = encoded.getFormat();
                int srcCh = encFmt.getChannels();
                int srcRate = (int) encFmt.getSampleRate();
                actualRate = srcRate > 0 ? srcRate : 44_100;
                javax.sound.sampled.AudioFormat df = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    actualRate, 16, srcCh, srcCh * 2, actualRate, false);
                byte[] src;
                try (AudioInputStream d = AudioSystem.getAudioInputStream(df, encoded)) {
                    src = d.readAllBytes();
                }
                if (src.length > MAX_DECODED_PCM_BYTES) {
                    HQSpeakerMod.warn("HQAudioStream: decoded " + label + " too large, dropping");
                    return;
                }
                if (src.length == 0 || closed) return;
                if (srcCh == 1) {
                    monoBytes = src;
                } else {
                    if (srcCh <= 0 || srcCh > 8) { HQSpeakerMod.warn("HQAudioStream: invalid channel count " + srcCh); return; }
                    int frames = src.length / (srcCh * 2);
                    if ((long)frames * 2L > MAX_DECODED_PCM_BYTES) { HQSpeakerMod.warn("HQAudioStream: mono decode too large"); return; }
                    monoBytes = new byte[frames * 2];
                    for (int i = 0; i < frames; i++) {
                        long sum = 0;
                        for (int ch = 0; ch < srcCh; ch++) {
                            int idx = i * srcCh * 2 + ch * 2;
                            sum += (short)((src[idx] & 0xFF) | (src[idx+1] << 8));
                        }
                        short m = (short)(sum / srcCh);
                        monoBytes[i*2] = (byte)(m&0xFF); monoBytes[i*2+1] = (byte)((m>>8)&0xFF);
                    }
                }
            }
            javax.sound.sampled.AudioFormat monoFmt = new javax.sound.sampled.AudioFormat(
                javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                actualRate, 16, 1, 2, actualRate, false);
            if (!closed && monoBytes.length > 0) pushPCM(monoBytes, monoFmt);
        } catch (UnsupportedAudioFileException e) {
            HQSpeakerMod.warn("HQAudioStream: unsupported " + label + " — " + e.getMessage());
        } catch (Exception e) {
            HQSpeakerMod.error("HQAudioStream: " + label + " decode failed — " + e);
        }
    }
}
