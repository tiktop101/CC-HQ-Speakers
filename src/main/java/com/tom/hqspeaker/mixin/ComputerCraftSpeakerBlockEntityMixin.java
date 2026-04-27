package com.tom.hqspeaker.mixin;

import com.tom.hqspeaker.peripheral.HQSpeakerPeripheralProvider;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(SpeakerBlockEntity.class)
public abstract class ComputerCraftSpeakerBlockEntityMixin {
    @Inject(method = "peripheral", at = @At("HEAD"), cancellable = true, remap = false)
    private void hqspeaker$replaceSpeakerPeripheral(CallbackInfoReturnable<IPeripheral> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level == null || pos == null) return;
        cir.setReturnValue(HQSpeakerPeripheralProvider.getOrCreate(level, pos));
    }
}
