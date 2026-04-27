package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;


public class HQSpeakerAudioPacket {

    public enum AudioFormat {
        PCM_S16LE,
        OGG_VORBIS,
        MP3,
        AUDIO_FILE,     
        MP3_STREAM,     
        HLS_STREAM,     
        TS_STREAM       
    }

    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_URL_CHARS = 512;
    public static final int MAX_SYNC_GROUP = 64;

    public final UUID        source;
    public final AudioFormat format;
    public final float       volume;
    public final float       x, y, z;           
    public final int         blockX, blockY, blockZ; 
    public final byte[]      data;
    public final String      streamUrl;         
    public final long        startTick;         
    public final UUID        syncGroupId;       
    public final int         syncGroupSize;     

    
    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, data, null, 0L, null, 0);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data,
                                 long startTick) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, data, null, startTick, null, 0);
    }

    
    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 String streamUrl) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, new byte[0], streamUrl, 0L, null, 0);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 String streamUrl,
                                 long startTick) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, new byte[0], streamUrl, startTick, null, 0);
    }


    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data,
                                 long startTick,
                                 UUID syncGroupId,
                                 int syncGroupSize) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, data, null, startTick, syncGroupId, syncGroupSize);
    }

    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 String streamUrl,
                                 long startTick,
                                 UUID syncGroupId,
                                 int syncGroupSize) {
        this(source, format, volume, x, y, z, blockX, blockY, blockZ, new byte[0], streamUrl, startTick, syncGroupId, syncGroupSize);
    }

    
    public HQSpeakerAudioPacket(UUID source, AudioFormat format,
                                 float volume,
                                 float x, float y, float z,
                                 int blockX, int blockY, int blockZ,
                                 byte[] data,
                                 String streamUrl,
                                 long startTick,
                                 UUID syncGroupId,
                                 int syncGroupSize) {
        this.source = source;
        this.format = format != null ? format : AudioFormat.AUDIO_FILE;
        this.volume = Float.isFinite(volume) ? Math.max(0.0f, Math.min(3.0f, volume)) : 1.0f;
        this.x = Float.isFinite(x) ? x : 0.0f; this.y = Float.isFinite(y) ? y : 0.0f; this.z = Float.isFinite(z) ? z : 0.0f;
        this.blockX = blockX; this.blockY = blockY; this.blockZ = blockZ;
        if (data == null) data = new byte[0];
        this.data = data.length <= MAX_BYTES ? data : new byte[0];
        this.streamUrl = streamUrl != null && streamUrl.length() <= MAX_URL_CHARS ? streamUrl : "";
        this.startTick = Math.max(0L, startTick);
        this.syncGroupId = syncGroupId;
        this.syncGroupSize = syncGroupId == null ? 0 : Math.max(1, Math.min(MAX_SYNC_GROUP, syncGroupSize));
    }

    public boolean isStreamingFormat() {
        return format == AudioFormat.MP3_STREAM ||
               format == AudioFormat.HLS_STREAM ||
               format == AudioFormat.TS_STREAM;
    }

    public static void encode(HQSpeakerAudioPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.source);
        buf.writeEnum(pkt.format);
        buf.writeFloat(pkt.volume);
        buf.writeFloat(pkt.x);
        buf.writeFloat(pkt.y);
        buf.writeFloat(pkt.z);
        buf.writeInt(pkt.blockX);
        buf.writeInt(pkt.blockY);
        buf.writeInt(pkt.blockZ);
        buf.writeVarLong(pkt.startTick);
        buf.writeBoolean(pkt.syncGroupId != null);
        if (pkt.syncGroupId != null) {
            buf.writeUUID(pkt.syncGroupId);
            buf.writeVarInt(pkt.syncGroupSize);
        }

        if (pkt.isStreamingFormat()) {
            
            buf.writeBoolean(true); 
            String safeUrl = pkt.streamUrl != null && pkt.streamUrl.length() <= MAX_URL_CHARS ? pkt.streamUrl : "";
            buf.writeUtf(safeUrl, MAX_URL_CHARS);
        } else {
            
            buf.writeBoolean(false); 
            int len = pkt.data == null ? 0 : Math.min(pkt.data.length, MAX_BYTES);
            buf.writeVarInt(len);
            if (len > 0) {
                buf.writeBytes(pkt.data, 0, len);
            }
        }
    }

    public static HQSpeakerAudioPacket decode(FriendlyByteBuf buf) {
        UUID        source = buf.readUUID();
        AudioFormat format = buf.readEnum(AudioFormat.class);
        float       volume = buf.readFloat();
        float       x      = buf.readFloat();
        float       y      = buf.readFloat();
        float       z      = buf.readFloat();
        int         blockX = buf.readInt();
        int         blockY = buf.readInt();
        int         blockZ = buf.readInt();
        long        startTick = buf.readVarLong();
        UUID        syncGroupId = buf.readBoolean() ? buf.readUUID() : null;
        int         syncGroupSize = syncGroupId != null ? buf.readVarInt() : 0;

        boolean isStreaming = buf.readBoolean();

        
        if (isStreaming != (format == AudioFormat.MP3_STREAM || format == AudioFormat.HLS_STREAM || format == AudioFormat.TS_STREAM)) {
            HQSpeakerMod.warn("HQSpeakerAudioPacket: rejected mismatched streaming flag for " + format);
            return new HQSpeakerAudioPacket(source, AudioFormat.AUDIO_FILE, volume, x, y, z, blockX, blockY, blockZ, new byte[0], startTick, null, 0);
        }

        if (isStreaming) {
            String streamUrl = buf.readUtf(MAX_URL_CHARS);
            return new HQSpeakerAudioPacket(source, format, volume, x, y, z, blockX, blockY, blockZ, streamUrl, startTick, syncGroupId, syncGroupSize);
        } else {
            int len = buf.readVarInt();
            if (len < 0 || len > MAX_BYTES) {
                HQSpeakerMod.warn("HQSpeakerAudioPacket: rejected oversized payload (" + len + " bytes)");
                return new HQSpeakerAudioPacket(source, format, volume, x, y, z, blockX, blockY, blockZ, new byte[0], startTick, syncGroupId, syncGroupSize);
            }

            byte[] data = new byte[len];
            if (len > 0) {
                buf.readBytes(data);
            }
            return new HQSpeakerAudioPacket(source, format, volume, x, y, z, blockX, blockY, blockZ, data, startTick, syncGroupId, syncGroupSize);
        }
    }

    public static void handle(HQSpeakerAudioPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
        ctx.enqueueWork(() -> com.tom.hqspeaker.client.HQSpeakerClientHandler.receive(pkt));
    }
}
