package com.tom.hqspeaker.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistries;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HQSpeakerPeripheralProvider implements IPeripheralProvider {
    private static final ResourceLocation CC_SPEAKER_ID = new ResourceLocation("computercraft", "speaker");
    private static final ConcurrentHashMap<Key, HQSpeakerPeripheral> CACHE = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public LazyOptional<IPeripheral> getPeripheral(@Nonnull Level world, @Nonnull BlockPos pos, @Nonnull Direction side) {
        if (!isComputerCraftSpeaker(world, pos)) return LazyOptional.empty();
        return LazyOptional.of(() -> getOrCreate(world, pos));
    }

    public static HQSpeakerPeripheral getOrCreate(Level world, BlockPos pos) {
        Key key = new Key(world.dimension().location(), pos.immutable());
        return CACHE.computeIfAbsent(key, k -> new HQSpeakerPeripheral(k.pos, world));
    }

    public static boolean isComputerCraftSpeaker(Level world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(world.getBlockState(pos).getBlock());
        return CC_SPEAKER_ID.equals(id);
    }

    public static void forget(Level world, BlockPos pos) {
        HQSpeakerPeripheral p = CACHE.remove(new Key(world.dimension().location(), pos.immutable()));
        if (p != null) p.cleanup();
    }

    private record Key(ResourceLocation dimension, BlockPos pos) {
        private Key {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }
}
