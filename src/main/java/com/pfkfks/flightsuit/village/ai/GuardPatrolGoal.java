package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Guards walk the village between random points - out toward the edges by day, close around the hall at
 * night (DESIGN.md: 야간 순찰) - and stand watch a moment at each.
 */
public class GuardPatrolGoal extends Goal {
    private final ResidentEntity guard;
    private int ticks;
    private int cooldown;

    public GuardPatrolGoal(ResidentEntity guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (guard.getJob() != ResidentJob.GUARD || guard.isDowned() || guard.isWanderer() || guard.getTarget() != null) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        VillageHallBlockEntity hall = guard.hall();
        if (hall == null) {
            return false;
        }
        BlockPos point = pickPoint(hall);
        return point != null && guard.getNavigation().moveTo(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D, 0.6D);
    }

    private BlockPos pickPoint(VillageHallBlockEntity hall) {
        boolean night = guard.isNightTime();
        float min = night ? 4.0F : 12.0F;
        float max = night ? 16.0F : VillageTuning.RADIUS - 6.0F;
        for (int i = 0; i < 8; i++) {
            float angle = guard.getRandom().nextFloat() * Mth.TWO_PI;
            float dist = min + guard.getRandom().nextFloat() * (max - min);
            int x = hall.getBlockPos().getX() + Mth.floor(Mth.cos(angle) * dist);
            int z = hall.getBlockPos().getZ() + Mth.floor(Mth.sin(angle) * dist);
            if (!guard.level().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, guard.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (hall.contains(pos)) {
                return pos;
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return !guard.getNavigation().isDone() && ticks < 400 && guard.getTarget() == null && !guard.isDowned()
                && guard.getJob() == ResidentJob.GUARD;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
        cooldown = 40 + guard.getRandom().nextInt(60);
    }
}
