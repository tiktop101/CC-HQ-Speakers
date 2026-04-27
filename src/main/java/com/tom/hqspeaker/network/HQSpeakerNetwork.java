package com.tom.hqspeaker.network;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class HQSpeakerNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("hqspeaker", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static void register() {
        CHANNEL.registerMessage(id(), HQSpeakerAudioPacket.class,
            HQSpeakerAudioPacket::encode, HQSpeakerAudioPacket::decode, HQSpeakerAudioPacket::handle);

        CHANNEL.registerMessage(id(), HQSpeakerStopPacket.class,
            HQSpeakerStopPacket::encode, HQSpeakerStopPacket::decode, HQSpeakerStopPacket::handle);

        CHANNEL.registerMessage(id(), IcyMetaPacket.class,
            IcyMetaPacket::encode, IcyMetaPacket::decode, IcyMetaPacket::handle);

        HQSpeakerMod.log("Network registered with " + packetId + " packets.");
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), packet);
    }
}
