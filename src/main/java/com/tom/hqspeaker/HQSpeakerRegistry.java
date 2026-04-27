package com.tom.hqspeaker;

import com.tom.hqspeaker.peripheral.HQSpeakerBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class HQSpeakerRegistry {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, HQSpeakerMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, HQSpeakerMod.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HQSpeakerMod.MOD_ID);

    public static final RegistryObject<Block> SPEAKER_BLOCK =
        BLOCKS.register("hq_speaker", HQSpeakerBlock::new);

    public static final RegistryObject<Item> SPEAKER_BLOCK_ITEM =
        ITEMS.register("hq_speaker",
            () -> new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<HQSpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("hq_speaker",
            () -> BlockEntityType.Builder.of(HQSpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));
}
