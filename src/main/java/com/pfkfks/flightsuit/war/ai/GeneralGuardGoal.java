package com.pfkfks.flightsuit.war.ai;

import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.GeneralEntity;
import com.pfkfks.flightsuit.war.RaidMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A recruited general keeps the village like a soldier: when the alarm is up he goes after the enemy himself
 * (the nearest raider or monster in or just outside the village, else where the alarm was raised - never the
 * hall, where the others shelter), and otherwise he walks the village rounds.
 */
public class GeneralGuardGoal extends Goal {
    /** How far past the village's edge he'll go out to meet them. */
    private static final int REACH_OUT = 16;

    private final GeneralEntity general;
    private BlockPos point;
    private int ticks;
    private int patrolCooldown;

    public GeneralGuardGoal(GeneralEntity general) {
        this.general = general;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private @Nullable VillageHallBlockEntity hall() {
        if (!general.isRecruited() || general.isFollowing() || !general.canFight() || general.getTarget() != null) {
            return null;
        }
        return Villages.hallAt(general.level(), general.getHallPos());
    }

    /** The nearest thing that is attacking the village (not kneeling prisoners, not our own side). */
    private @Nullable LivingEntity nearestThreat(VillageHallBlockEntity hall) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : general.level().getEntitiesOfClass(LivingEntity.class, hall.area().inflate(REACH_OUT, 0.0D, REACH_OUT),
                entity -> entity.isAlive() && entity instanceof Enemy && !RaidMember.isNoThreat(entity) && general.isFoe(entity))) {
            double dist = entity.distanceToSqr(general);
            if (dist < bestDist) {
                best = entity;
                bestDist = dist;
            }
        }
        return best;
    }

    private @Nullable BlockPos pick() {
        VillageHallBlockEntity hall = hall();
        if (hall == null) {
            return null;
        }
        if (hall.isAlarm()) {
            LivingEntity threat = nearestThreat(hall);
            if (threat != null) {
                return threat.blockPosition();
            }
            BlockPos alarm = hall.alarmPos();
            boolean atHall = alarm.closerThan(hall.getBlockPos(), 4.0D);
            if (!atHall && general.position().distanceToSqr(Vec3.atBottomCenterOf(alarm)) > 25.0D) {
                return alarm;
            }
        }
        // Rounds: a new spot somewhere in the village every half minute or so.
        if (patrolCooldown > 0) {
            return null;
        }
        patrolCooldown = 400 + general.getRandom().nextInt(400);
        BlockPos center = hall.getBlockPos();
        int range = VillageTuning.RADIUS - 8;
        int x = center.getX() + general.getRandom().nextInt(range * 2 + 1) - range;
        int z = center.getZ() + general.getRandom().nextInt(range * 2 + 1) - range;
        if (!general.level().hasChunkAt(new BlockPos(x, center.getY(), z))) {
            return null;
        }
        return new BlockPos(x, general.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    @Override
    public boolean canUse() {
        if (patrolCooldown > 0) {
            // canUse runs every other tick (goal selector), so count down by two.
            patrolCooldown = Math.max(0, patrolCooldown - 2);
        }
        if (general.getRandom().nextInt(reducedTickDelay(10)) != 0) {
            return false;
        }
        point = pick();
        return point != null;
    }

    @Override
    public boolean canContinueToUse() {
        return point != null && general.getTarget() == null && general.canFight() && hall() != null
                && (!general.getNavigation().isDone() || general.position().distanceToSqr(Vec3.atBottomCenterOf(point)) > 9.0D)
                && ticks < 20 * 30;
    }

    @Override
    public void start() {
        ticks = 0;
        moveTo(point);
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            VillageHallBlockEntity hall = hall();
            if (hall != null && hall.isAlarm()) {
                LivingEntity threat = nearestThreat(hall);
                if (threat != null) {
                    point = threat.blockPosition();
                }
            }
            moveTo(point);
        }
    }

    private void moveTo(BlockPos spot) {
        general.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.1D);
    }

    @Override
    public void stop() {
        general.getNavigation().stop();
        point = null;
    }
}
