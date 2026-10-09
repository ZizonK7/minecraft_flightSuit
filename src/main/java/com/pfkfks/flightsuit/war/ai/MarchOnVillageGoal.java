package com.pfkfks.flightsuit.war.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * With nobody to fight, the raiders press on toward the village hall. Paths that far don't always resolve in
 * one go, so when the hall is out of reach they take a long stride in its direction instead.
 */
public class MarchOnVillageGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<BlockPos> objective;
    private int ticks;

    public MarchOnVillageGoal(PathfinderMob mob, Supplier<BlockPos> objective) {
        this.mob = mob;
        this.objective = objective;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos hall = objective.get();
        return hall != null && mob.getTarget() == null && mob.position().distanceToSqr(Vec3.atBottomCenterOf(hall)) > 36.0D;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (ticks++ % 40 != 0) {
            return;
        }
        BlockPos hall = objective.get();
        if (hall == null) {
            return;
        }
        Vec3 goal = Vec3.atBottomCenterOf(hall);
        if (!mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0D)) {
            Vec3 step = DefaultRandomPos.getPosTowards(mob, 16, 7, goal, Math.PI / 2.0D);
            if (step != null) {
                mob.getNavigation().moveTo(step.x, step.y, step.z, 1.0D);
            }
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
