package com.pfkfks.flightsuit.war.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Marches with a player (support troops, the player's own soldiers on campaign, M12): keeps within a few
 * blocks, catches up across big gaps. When there's an objective (a fortress being invaded) and the player is
 * near it, this steps aside so the march on the objective takes over.
 */
public class FollowCommanderGoal extends Goal {
    private static final double LEAVE = 6.0D;
    private static final double TELEPORT = 48.0D;

    private final PathfinderMob mob;
    private final Supplier<UUID> commander;
    private final Supplier<BlockPos> objective;
    private Player leader;
    private int ticks;

    public FollowCommanderGoal(PathfinderMob mob, Supplier<UUID> commander, Supplier<BlockPos> objective) {
        this.mob = mob;
        this.commander = commander;
        this.objective = objective;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private Player find() {
        UUID id = commander.get();
        if (id == null || mob.getTarget() != null) {
            return null;
        }
        Player player = mob.level().getPlayerByUUID(id);
        if (player == null || player.isSpectator()) {
            return null;
        }
        BlockPos goal = objective.get();
        if (goal != null && player.blockPosition().closerThan(goal, 48.0D)) {
            return null;
        }
        return player;
    }

    @Override
    public boolean canUse() {
        leader = find();
        return leader != null && mob.distanceToSqr(leader) > LEAVE * LEAVE;
    }

    @Override
    public boolean canContinueToUse() {
        leader = find();
        return leader != null && mob.distanceToSqr(leader) > 2.5D * 2.5D;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (leader == null || ticks++ % 10 != 0) {
            return;
        }
        double distance = mob.distanceTo(leader);
        if (distance > TELEPORT && leader.onGround()) {
            Vec3 behind = leader.position().subtract(leader.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(2.0D));
            mob.moveTo(behind.x, leader.getY(), behind.z, leader.getYRot(), 0.0F);
            mob.getNavigation().stop();
            return;
        }
        mob.getNavigation().moveTo(leader, distance > 12.0D ? 1.3D : 1.0D);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
