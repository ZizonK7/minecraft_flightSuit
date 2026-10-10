package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Builds a planned structure (a fortress, Hero City) a slice per tick: first it clears the site - every column
 * of the square, from {@code clearHeight} down to just above the ground, emptied of hills and trees - then lays
 * the placements in order. The clearing isn't in the placement list: for a big site that would be hundreds of
 * thousands of air blocks held in memory, most of them air already.
 */
public final class StructureJob {
    /** What clearing one column costs out of the tick's budget, on top of each block actually removed. */
    private static final int COLUMN_COST = 4;

    private final BlockPos origin;
    private final int clearRadius;
    private final int clearHeight;
    private final List<FortressBuilder.Placement> placements;
    private int column;
    private int index;

    public StructureJob(BlockPos origin, int clearRadius, int clearHeight, List<FortressBuilder.Placement> placements) {
        this.origin = origin.immutable();
        this.clearRadius = clearRadius;
        this.clearHeight = clearHeight;
        this.placements = placements;
    }

    /** Does about {@code budget} blocks' worth of work; true once everything is built. */
    public boolean tick(ServerLevel level, int budget) {
        int side = clearRadius * 2 + 1;
        int columns = side * side;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget > 0 && column < columns) {
            int x = origin.getX() - clearRadius + column % side;
            int z = origin.getZ() - clearRadius + column / side;
            for (int y = clearHeight; y >= 1; y--) {
                pos.set(x, origin.getY() + y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    budget--;
                }
            }
            budget -= COLUMN_COST;
            column++;
        }
        int end = Math.min(placements.size(), index + Math.max(0, budget));
        for (; index < end; index++) {
            FortressBuilder.Placement placement = placements.get(index);
            if (placement.fillOnly()) {
                BlockState current = level.getBlockState(placement.pos());
                if (!current.isAir() && !current.canBeReplaced() && current.getFluidState().isEmpty()) {
                    continue;
                }
            }
            level.setBlock(placement.pos(), placement.state(), Block.UPDATE_CLIENTS);
        }
        return column >= columns && index >= placements.size();
    }
}
