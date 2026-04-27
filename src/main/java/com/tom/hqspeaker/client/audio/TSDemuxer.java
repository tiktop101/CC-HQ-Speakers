package com.tom.hqspeaker.client.audio;

import com.tom.hqspeaker.HQSpeakerMod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class TSDemuxer {

    
    private static final int TS_PACKET_SIZE = 188;
    private static final int SYNC_BYTE = 0x47;
    
    
    private static final int STREAM_TYPE_AAC = 0x0F;      
    private static final int STREAM_TYPE_MPEG1_AUDIO = 0x03; 
    private static final int STREAM_TYPE_MPEG2_AUDIO = 0x04; 
    private static final int STREAM_TYPE_AC3 = 0x81;      
    private static final int STREAM_TYPE_EAC3 = 0x87;     

    
    private static final int PES_START_CODE_PREFIX = 0x000001;
    private static final int PES_STREAM_ID_AUDIO_MIN = 0xC0;
    private static final int PES_STREAM_ID_AUDIO_MAX = 0xDF;

    private int audioPid = -1;
    private int streamType = -1;
    private boolean patParsed = false;
    private boolean pmtParsed = false;
    
    
    private ByteArrayOutputStream currentPes = new ByteArrayOutputStream();
    private boolean pesStartFound = false;

    public enum AudioFormat {
        UNKNOWN,
        AAC_ADTS,   
        MP3,        
        AC3,        
        EAC3        
    }

    public static class AudioFrame {
        public final byte[] data;
        public final AudioFormat format;
        public final long pts;  
        public final boolean isKeyframe;

        public AudioFrame(byte[] data, AudioFormat format, long pts, boolean isKeyframe) {
            this.data = data;
            this.format = format;
            this.pts = pts;
            this.isKeyframe = isKeyframe;
        }
    }

    
    public List<AudioFrame> demux(byte[] tsData) {
        List<AudioFrame> frames = new ArrayList<>();
        
        int offset = 0;
        while (offset + TS_PACKET_SIZE <= tsData.length) {
            
            if (tsData[offset] != (byte) SYNC_BYTE) {
                offset++;
                continue;
            }

            TSPacket packet = parseTSPacket(tsData, offset);
            if (packet != null) {
                processPacket(packet, frames);
            }
            offset += TS_PACKET_SIZE;
        }

        
        if (currentPes.size() > 0) {
            AudioFrame frame = extractAudioFrame(currentPes.toByteArray());
            if (frame != null) frames.add(frame);
            currentPes.reset();
        }

        return frames;
    }

    
    public List<AudioFrame> demux(InputStream stream) throws IOException {
        List<AudioFrame> frames = new ArrayList<>();
        byte[] packetBuffer = new byte[TS_PACKET_SIZE];
        
        int bytesRead;
        while ((bytesRead = readFully(stream, packetBuffer)) == TS_PACKET_SIZE) {
            
            if (packetBuffer[0] != (byte) SYNC_BYTE) {
                
                int syncByte = stream.read();
                while (syncByte != -1 && syncByte != SYNC_BYTE) {
                    syncByte = stream.read();
                }
                if (syncByte == -1) break;
                packetBuffer[0] = (byte) SYNC_BYTE;
                if (readFully(stream, packetBuffer, 1, TS_PACKET_SIZE - 1) != TS_PACKET_SIZE - 1) {
                    break;
                }
            }

            TSPacket packet = parseTSPacket(packetBuffer, 0);
            if (packet != null) {
                processPacket(packet, frames);
            }
        }

        
        if (currentPes.size() > 0) {
            AudioFrame frame = extractAudioFrame(currentPes.toByteArray());
            if (frame != null) frames.add(frame);
        }

        return frames;
    }

    private TSPacket parseTSPacket(byte[] data, int offset) {
        try {
            int sync = data[offset] & 0xFF;
            if (sync != SYNC_BYTE) return null;

            int pid = ((data[offset + 1] & 0x1F) << 8) | (data[offset + 2] & 0xFF);
            int flags = data[offset + 3] & 0xFF;
            boolean payloadStart = (data[offset + 1] & 0x40) != 0;
            boolean adaptation = (flags & 0x20) != 0;
            boolean hasPayload = (flags & 0x10) != 0;
            int continuity = flags & 0x0F;

            int payloadOffset = offset + 4;
            int adaptationLength = 0;

            if (adaptation) {
                adaptationLength = (data[payloadOffset] & 0xFF) + 1;
                payloadOffset += adaptationLength;
            }

            int payloadLength = TS_PACKET_SIZE - (payloadOffset - offset);
            if (payloadLength < 0) payloadLength = 0;

            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, payloadOffset, payload, 0, payloadLength);

            return new TSPacket(pid, payloadStart, hasPayload, continuity, payload, adaptationLength > 0 ? data[offset + 5] : 0);
        } catch (Exception e) {
            return null;
        }
    }

    private void processPacket(TSPacket packet, List<AudioFrame> frames) {
        
        if (packet.pid == 0) {
            parsePAT(packet.payload);
            return;
        }

        
        if (packet.pid == getPMTPid()) {
            parsePMT(packet.payload);
            return;
        }

        
        if (packet.pid == audioPid && packet.hasPayload) {
            processPESPacket(packet, frames);
        }
    }

    private int pmtPid = -1;

    private int getPMTPid() {
        return pmtPid;
    }

    private void parsePAT(byte[] payload) {
        if (payload.length < 8) return;
        
        int pointer = 0;
        if ((payload[0] & 0xFF) != 0) {
            pointer = payload[0] & 0xFF;
        }
        
        int tableStart = pointer + 1;
        if (tableStart + 8 > payload.length) return;

        int tableId = payload[tableStart] & 0xFF;
        if (tableId != 0x00) return; 

        int sectionLength = ((payload[tableStart + 1] & 0x0F) << 8) | (payload[tableStart + 2] & 0xFF);
        int transportStreamId = ((payload[tableStart + 3] & 0xFF) << 8) | (payload[tableStart + 4] & 0xFF);

        
        int programOffset = tableStart + 8;
        int programsEnd = tableStart + 3 + sectionLength - 4; 

        while (programOffset + 4 <= programsEnd && programOffset + 4 <= payload.length) {
            int programNumber = ((payload[programOffset] & 0xFF) << 8) | (payload[programOffset + 1] & 0xFF);
            int programPid = ((payload[programOffset + 2] & 0x1F) << 8) | (payload[programOffset + 3] & 0xFF);

            if (programNumber != 0) {
                pmtPid = programPid;
                HQSpeakerMod.log("TS: Found PMT PID " + pmtPid + " for program " + programNumber);
                break;
            }
            programOffset += 4;
        }

        patParsed = true;
    }

    private void parsePMT(byte[] payload) {
        if (payload.length < 12) return;

        int pointer = 0;
        if ((payload[0] & 0xFF) != 0) {
            pointer = payload[0] & 0xFF;
        }

        int tableStart = pointer + 1;
        if (tableStart + 12 > payload.length) return;

        int tableId = payload[tableStart] & 0xFF;
        if (tableId != 0x02) return; 

        int sectionLength = ((payload[tableStart + 1] & 0x0F) << 8) | (payload[tableStart + 2] & 0xFF);
        int programNumber = ((payload[tableStart + 3] & 0xFF) << 8) | (payload[tableStart + 4] & 0xFF);
        int pcrPid = ((payload[tableStart + 8] & 0x1F) << 8) | (payload[tableStart + 9] & 0xFF);
        int programInfoLength = ((payload[tableStart + 10] & 0x0F) << 8) | (payload[tableStart + 11] & 0xFF);

        int streamOffset = tableStart + 12 + programInfoLength;
        int streamsEnd = tableStart + 3 + sectionLength - 4; 

        while (streamOffset + 5 <= streamsEnd && streamOffset + 5 <= payload.length) {
            int streamType = payload[streamOffset] & 0xFF;
            int elementaryPid = ((payload[streamOffset + 1] & 0x1F) << 8) | (payload[streamOffset + 2] & 0xFF);
            int esInfoLength = ((payload[streamOffset + 3] & 0x0F) << 8) | (payload[streamOffset + 4] & 0xFF);

            
            if (isAudioStreamType(streamType)) {
                audioPid = elementaryPid;
                this.streamType = streamType;
                HQSpeakerMod.log("TS: Found audio PID " + audioPid + " type 0x" + 
                    Integer.toHexString(streamType));
            }

            streamOffset += 5 + esInfoLength;
        }

        pmtParsed = true;
    }

    private boolean isAudioStreamType(int streamType) {
        return streamType == STREAM_TYPE_AAC ||
               streamType == STREAM_TYPE_MPEG1_AUDIO ||
               streamType == STREAM_TYPE_MPEG2_AUDIO ||
               streamType == STREAM_TYPE_AC3 ||
               streamType == STREAM_TYPE_EAC3;
    }

    private void processPESPacket(TSPacket packet, List<AudioFrame> frames) {
        byte[] payload = packet.payload;
        if (payload.length < 6) return;

        
        int startCode = ((payload[0] & 0xFF) << 16) | ((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF);
        
        if (startCode == PES_START_CODE_PREFIX) {
            
            if (currentPes.size() > 0) {
                AudioFrame frame = extractAudioFrame(currentPes.toByteArray());
                if (frame != null) frames.add(frame);
                currentPes.reset();
            }
            
            
            int streamId = payload[3] & 0xFF;
            int pesPacketLength = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
            
            int headerOffset = 6;
            long pts = -1;

            
            if ((streamId & 0xC0) == 0xC0) { 
                if (headerOffset + 3 <= payload.length) {
                    int flags = payload[headerOffset + 1] & 0xFF;
                    int headerLength = payload[headerOffset + 2] & 0xFF;
                    
                    if ((flags & 0x80) != 0 && headerOffset + 9 <= payload.length) {
                        
                        pts = parsePTS(payload, headerOffset + 3);
                    }
                    
                    headerOffset += 3 + headerLength;
                }
            }

            
            if (headerOffset < payload.length) {
                currentPes.write(payload, headerOffset, payload.length - headerOffset);
            }
        } else {
            
            currentPes.write(payload, 0, payload.length);
        }
    }

    private long parsePTS(byte[] data, int offset) {
        long pts = 0;
        pts |= ((data[offset] & 0x0E) << 29);
        pts |= ((data[offset + 1] & 0xFF) << 22);
        pts |= ((data[offset + 2] & 0xFE) << 14);
        pts |= ((data[offset + 3] & 0xFF) << 7);
        pts |= ((data[offset + 4] & 0xFE) >> 1);
        return pts;
    }

    private AudioFrame extractAudioFrame(byte[] pesData) {
        if (pesData.length < 4) return null;

        AudioFormat format = getAudioFormat();
        
        
        if (format == AudioFormat.MP3) {
            return extractMP3Frame(pesData);
        }

        
        return new AudioFrame(pesData, format, -1, true);
    }

    private AudioFrame extractMP3Frame(byte[] data) {
        
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == (byte) 0xFF && (data[i + 1] & 0xE0) == (byte) 0xE0) {
                
                int frameLength = parseMP3FrameLength(data, i);
                if (frameLength > 0 && i + frameLength <= data.length) {
                    byte[] frame = new byte[frameLength];
                    System.arraycopy(data, i, frame, 0, frameLength);
                    return new AudioFrame(frame, AudioFormat.MP3, -1, true);
                }
            }
        }
        return null;
    }

    private int parseMP3FrameLength(byte[] data, int offset) {
        if (offset + 4 > data.length) return -1;

        int version = (data[offset + 1] & 0x18) >> 3;
        int layer = (data[offset + 1] & 0x06) >> 1;
        int bitrateIndex = (data[offset + 2] & 0xF0) >> 4;
        int sampleRateIndex = (data[offset + 2] & 0x0C) >> 2;
        int padding = (data[offset + 2] & 0x02) >> 1;

        if (bitrateIndex == 0 || bitrateIndex == 15) return -1;
        if (sampleRateIndex == 3) return -1;

        int[] bitrates = getBitrates(version, layer);
        int[] sampleRates = getSampleRates(version);

        if (bitrates == null || sampleRates == null) return -1;

        int bitrate = bitrates[bitrateIndex] * 1000;
        int sampleRate = sampleRates[sampleRateIndex];

        if (bitrate == 0 || sampleRate == 0) return -1;

        int frameLength;
        if (layer == 3) { 
            frameLength = (12 * bitrate / sampleRate + padding) * 4;
        } else { 
            frameLength = 144 * bitrate / sampleRate + padding;
        }

        return frameLength;
    }

    private int[] getBitrates(int version, int layer) {
        
        if (version == 3) {
            if (layer == 3) { 
                return new int[]{0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0};
            } else if (layer == 2) { 
                return new int[]{0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0};
            } else { 
                return new int[]{0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
            }
        }
        
        else {
            if (layer == 3) { 
                return new int[]{0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0};
            } else { 
                return new int[]{0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
            }
        }
    }

    private int[] getSampleRates(int version) {
        if (version == 3) { 
            return new int[]{44100, 48000, 32000, 0};
        } else if (version == 2) { 
            return new int[]{22050, 24000, 16000, 0};
        } else { 
            return new int[]{11025, 12000, 8000, 0};
        }
    }

    private AudioFormat getAudioFormat() {
        switch (streamType) {
            case STREAM_TYPE_AAC:
                return AudioFormat.AAC_ADTS;
            case STREAM_TYPE_MPEG1_AUDIO:
            case STREAM_TYPE_MPEG2_AUDIO:
                return AudioFormat.MP3;
            case STREAM_TYPE_AC3:
                return AudioFormat.AC3;
            case STREAM_TYPE_EAC3:
                return AudioFormat.EAC3;
            default:
                return AudioFormat.UNKNOWN;
        }
    }

    private int readFully(InputStream stream, byte[] buffer) throws IOException {
        return readFully(stream, buffer, 0, buffer.length);
    }

    private int readFully(InputStream stream, byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = stream.read(buffer, offset + totalRead, length - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        return totalRead;
    }

    private static class TSPacket {
        final int pid;
        final boolean payloadStart;
        final boolean hasPayload;
        final int continuity;
        final byte[] payload;
        final int adaptationFlags;

        TSPacket(int pid, boolean payloadStart, boolean hasPayload, int continuity, byte[] payload, int adaptationFlags) {
            this.pid = pid;
            this.payloadStart = payloadStart;
            this.hasPayload = hasPayload;
            this.continuity = continuity;
            this.payload = payload;
            this.adaptationFlags = adaptationFlags;
        }
    }

    
    public static List<AudioFrame> fetchAndDemux(String url) throws IOException {
        HQSpeakerMod.log("TS: Fetching segment from " + url);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "HQSpeaker-TS/1.0");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " for " + url);
        }

        byte[] data;
        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            data = baos.toByteArray();
        }

        HQSpeakerMod.log("TS: Fetched " + data.length + " bytes");
        
        TSDemuxer demuxer = new TSDemuxer();
        return demuxer.demux(data);
    }
}
