package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.Tags;

/**
 * Damage that just happens in a fight (DESIGN.md 4-11: 적이 블록을 노리지는 않지만 싸우다 보니 부서지는 흐름).
 * Only inside villages, where everything broken goes on the hall's ledger for the builders to put back.
 */
public final class WarDamage {
    private WarDamage() {
    }

    /** Zhang Fei's shout: glass shatters and doors burst within {@code radius}, at most {@code max} of them. */
    public static int shatter(ServerLevel level, BlockPos center, int radius, int max) {
        if (Villages.containing(level, center) == null) {
            return 0;
        }
        int broken = 0;
        int radiusSqr = radius * radius;
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
            if (broken >= max) {
                break;
            }
            if (scan.distSqr(center) > radiusSqr) {
                continue;
            }
            BlockState state = level.getBlockState(scan);
            boolean glass = state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES);
            boolean door = state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            if (!glass && !door) {
                continue;
            }
            BlockPos pos = scan.immutable();
            if (door) {
                Villages.recordDamage(level, pos.above(), level.getBlockState(pos.above()));
            }
            Villages.recordDamage(level, pos, state);
            level.destroyBlock(pos, false);
            broken++;
        }
        return broken;
    }
}
