package com.tom.hqspeaker;

import com.tom.hqspeaker.network.HQSpeakerNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.OnlyIn;

@Mod("hqspeaker")
public class HQSpeakerMod {

    public static final String MOD_ID = "hqspeaker";

    public HQSpeakerMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        
        modEventBus.addListener(this::setup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
        }

        MinecraftForge.EVENT_BUS.register(this);
        log("HQSpeaker mod loaded");
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            HQSpeakerNetwork.register();
            log("HQSpeaker network registered");

            dan200.computercraft.api.ForgeComputerCraftAPI.registerPeripheralProvider(
                new com.tom.hqspeaker.peripheral.HQSpeakerPeripheralProvider()
            );
            log("HQSpeaker provider registered on CC:Tweaked speaker blocks");
        });
    }

    @OnlyIn(Dist.CLIENT)
    private void clientSetup(FMLClientSetupEvent event) {
        log("HQSpeaker client setup complete");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            com.tom.hqspeaker.peripheral.HQSpeakerPeripheral.tickAllActive();
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            com.tom.hqspeaker.client.HQSpeakerClientHandler.tick();
        }
    }

    public static void log(String msg)   { System.out.println("[HQSpeaker] " + msg); }
    public static void warn(String msg)  { System.err.println("[HQSpeaker WARN] " + msg); }
    public static void error(String msg) { System.err.println("[HQSpeaker ERROR] " + msg); }
}
