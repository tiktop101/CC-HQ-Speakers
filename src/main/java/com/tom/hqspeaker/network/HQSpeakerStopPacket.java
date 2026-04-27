package com.tom.hqspeaker.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;


public class HQSpeakerStopPacket {

    public final UUID source;

    public HQSpeakerStopPacket(UUID source) {
        this.source = source;
    }

    public static void encode(HQSpeakerStopPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.source);
    }

    public static HQSpeakerStopPacket decode(FriendlyByteBuf buf) {
        return new HQSpeakerStopPacket(buf.readUUID());
    }

    public static void handle(HQSpeakerStopPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
        ctx.enqueueWork(() -> com.tom.hqspeaker.client.HQSpeakerClientHandler.stop(pkt.source));
    }
}
