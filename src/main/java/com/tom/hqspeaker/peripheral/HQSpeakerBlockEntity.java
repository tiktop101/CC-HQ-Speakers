package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HQSpeakerBlockEntity extends BlockEntity {

    private HQSpeakerPeripheral peripheral;

    public HQSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(HQSpeakerRegistry.SPEAKER_BLOCK_ENTITY.get(), pos, state);
    }

    public void serverTick() {
        if (peripheral != null) {
            peripheral.speakerTick();
        }
    }

    public HQSpeakerPeripheral getOrCreatePeripheral() {
        if (peripheral == null) {
            peripheral = new HQSpeakerPeripheral(worldPosition, level);
        }
        return peripheral;
    }

    public void invalidatePeripheral() {
        if (peripheral != null) {
            peripheral.cleanup();
            peripheral = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidatePeripheral();
    }
}
