
package com.tom.hqspeaker.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

final class HQSpeakerCluster {
    private HQSpeakerCluster() {}

    static List<BlockPos> collect(Level world, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!seen.add(pos)) continue;
            if (!world.isLoaded(pos)) continue;
            if (!HQSpeakerPeripheralProvider.isComputerCraftSpeaker(world, pos)) continue;
            out.add(pos.immutable());
            for (Direction d : Direction.values()) queue.add(pos.relative(d));
        }
        out.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getY())
                .thenComparingInt(pos -> pos.getZ()));
        return out;
    }
}
