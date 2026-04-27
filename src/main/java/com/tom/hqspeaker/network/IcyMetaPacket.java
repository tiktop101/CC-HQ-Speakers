package com.tom.hqspeaker.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;


public class IcyMetaPacket {

    
    public static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,
            com.tom.hqspeaker.peripheral.HQSpeakerPeripheral> SPEAKER_REGISTRY =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final int MAX_FIELD_LEN = 256;

    public final UUID   source;      
    public final String rawTitle;    
    public final String stationName; 
    public final String genre;       
    public final String description; 

    public IcyMetaPacket(UUID source, String rawTitle,
                          String stationName, String genre, String description) {
        this.source      = source;
        this.rawTitle    = cap(rawTitle);
        this.stationName = cap(stationName);
        this.genre       = cap(genre);
        this.description = cap(description);
    }

    private static String cap(String s) {
        if (s == null) return "";
        return s.length() > MAX_FIELD_LEN ? s.substring(0, MAX_FIELD_LEN) : s;
    }

    public static void encode(IcyMetaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.source);
        buf.writeUtf(pkt.rawTitle,    MAX_FIELD_LEN);
        buf.writeUtf(pkt.stationName, MAX_FIELD_LEN);
        buf.writeUtf(pkt.genre,       MAX_FIELD_LEN);
        buf.writeUtf(pkt.description, MAX_FIELD_LEN);
    }

    public static IcyMetaPacket decode(FriendlyByteBuf buf) {
        UUID   source      = buf.readUUID();
        String rawTitle    = buf.readUtf(MAX_FIELD_LEN);
        String stationName = buf.readUtf(MAX_FIELD_LEN);
        String genre       = buf.readUtf(MAX_FIELD_LEN);
        String description = buf.readUtf(MAX_FIELD_LEN);
        return new IcyMetaPacket(source, rawTitle, stationName, genre, description);
    }

    public static void handle(IcyMetaPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
        if (ctx.getSender() == null) return; 

        ctx.enqueueWork(() -> {
            
            com.tom.hqspeaker.peripheral.HQSpeakerPeripheral p = SPEAKER_REGISTRY.get(pkt.source);
            if (p != null && p.canAcceptIcyMetadata(ctx.getSender())) {
                p.onIcyMetadata(pkt.rawTitle, pkt.stationName, pkt.genre, pkt.description);
            }
        });
    }
}
