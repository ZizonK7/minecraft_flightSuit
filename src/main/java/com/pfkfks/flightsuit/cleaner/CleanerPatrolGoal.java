package com.pfkfks.flightsuit.cleaner;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Robot-vacuum patrol, ported from minebutler's GatedWorkerGuardGoal with the station swapped for the
 * cleaner dock. Twice a day (morning and evening windows) - or right away when tapped - it sweeps every
 * area in boustrophedon rows (two passes from opposite sides), deletes reachable hostile mobs it detects on
 * the way (with a short burst for splitting slimes), puffs a suction effect so the drops get vacuumed, and
 * shines a "flashlight" in the dark.
 *
 * Only ground-reachable targets count: within ~half a block of its own height and a short local path, so
 * fences and walls keep it from deleting things on the other side.
 */
public class CleanerPatrolGoal extends Goal {
    private static final long DAY_LENGTH = 24000L;
    private static final int MORNING_START = 0;
    private static final int MORNING_END = 1800;
    private static final int EVENING_START = 12000;
    private static final int EVENING_END = 13800;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int NAV_RETRY_TICKS = 20;
    private static final double CLEAN_DISTANCE_SQR = 3.0D;
    private static final double PATROL_ARRIVAL_DISTANCE_SQR = 2.25D;
    private static final double PATROL_SMOOTH_ADVANCE_DISTANCE_SQR = 5.76D;
    private static final double PATROL_DETECTION_RADIUS = 12.0D;
    private static final double MAX_CLEANABLE_Y_DELTA = 0.75D;
    private static final double MAX_CLEANABLE_PATH_LENGTH = 24.0D;
    private static final double MAX_PATROL_PATH_LENGTH = 32.0D;
    private static final int SWEEP_PASSES = 2;
    private static final int SUCTION_EFFECT_TICKS = 40;
    private static final double CLEANUP_BURST_RADIUS = 5.0D;
    private static final int CLEANUP_BURST_TICKS = 60;
    private static final int CLEANUP_BURST_MAX_KILLS = 16;
    private static final double MAX_WORK_START_DISTANCE_SQR = 20.0D * 20.0D;
    private static final float CLEAN_DAMAGE = 10000.0F;
    /** Routine key used for tap-to-start runs (never matches a scheduled key). */
    private static final long MANUAL_KEY = -2L;

    private final CleanerRobotEntity robot;
    private final List<BlockPos> patrolPoints = new ArrayList<>();
    private int patrolIndex;
    @Nullable
    private LivingEntity target;
    private int scanCooldown;
    private int navRetryCooldown;
    private int suctionEffectTicks;
    @Nullable
    private Vec3 suctionEffectCenter;
    @Nullable
    private Vec3 cleanupBurstCenter;
    private int cleanupBurstTicks;
    private int cleanupBurstKills;

    public CleanerPatrolGoal(CleanerRobotEntity robot) {
        this.robot = robot;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (robot.getDock() == null || robot.isReturnRequested() || robot.isHopperNearlyFull()
                || !isNearDock() || !(robot.level() instanceof ServerLevel)) {
            return false;
        }
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = SCAN_INTERVAL_TICKS;
        if (patrolPoints.isEmpty() && !prepareRoutine()) {
            return false;
        }
        target = findNearbyPatrolTarget();
        return patrolIndex < patrolPoints.size();
    }

    @Override
    public boolean canContinueToUse() {
        return !robot.isReturnRequested() && !robot.isHopperNearlyFull()
                && (target != null && target.isAlive() || patrolIndex < patrolPoints.size());
    }

    @Override
    public void start() {
        robot.setDocked(false);
        navRetryCooldown = 0;
        if (target != null) {
            moveToTarget();
        } else {
            moveToPatrolPoint();
        }
    }

    @Override
    public void stop() {
        target = null;
        navRetryCooldown = 0;
        suctionEffectTicks = 0;
        suctionEffectCenter = null;
    }

    @Override
    public void tick() {
        if (suctionEffectTicks > 0) {
            tickSuctionEffect();
        }
        if (cleanupBurstTicks > 0) {
            tickCleanupBurst();
            return;
        }
        if ((target == null || !target.isAlive()) && --scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL_TICKS;
            target = findNearbyPatrolTarget();
            if (target != null) {
                moveToTarget();
            }
        }
        if (target != null && target.isAlive()) {
            tickTarget();
            return;
        }
        tickPatrol();
    }

    private void tickTarget() {
        if (target == null) {
            return;
        }
        robot.getLookControl().setLookAt(target, 30.0F, 30.0F);
        emitFlashlightIfDark(target.position());
        if (robot.distanceToSqr(target) <= CLEAN_DISTANCE_SQR) {
            if (!canCleanNow(target)) {
                target = null;
                moveToPatrolPoint();
                return;
            }
            Vec3 cleanPos = target.position();
            startCleanupBurst(cleanPos);
            startSuctionEffect(cleanPos);
            cleanTarget(target);
            target = null;
            return;
        }
        if (--navRetryCooldown <= 0) {
            moveToTarget();
        }
    }

    private void startSuctionEffect(Vec3 center) {
        suctionEffectCenter = center;
        suctionEffectTicks = SUCTION_EFFECT_TICKS;
        robot.getNavigation().stop();
    }

    private void tickSuctionEffect() {
        if (suctionEffectCenter == null) {
            suctionEffectTicks = 0;
            return;
        }
        robot.getNavigation().stop();
        emitSuctionParticles(suctionEffectCenter, 0.9D);
        // The suction actually pulls the drops in: nudge nearby items toward the intake.
        Vec3 intake = robot.position().add(0.0D, 0.15D, 0.0D);
        for (var item : robot.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(suctionEffectCenter, suctionEffectCenter).inflate(2.5D))) {
            item.setDeltaMovement(intake.subtract(item.position()).normalize().scale(0.25D));
        }
        suctionEffectTicks--;
        if (suctionEffectTicks <= 0) {
            suctionEffectCenter = null;
        }
    }

    private void startCleanupBurst(Vec3 center) {
        cleanupBurstCenter = center;
        cleanupBurstTicks = CLEANUP_BURST_TICKS;
        cleanupBurstKills = 0;
        robot.getNavigation().stop();
    }

    private void tickCleanupBurst() {
        if (cleanupBurstCenter == null || !(robot.level() instanceof ServerLevel serverLevel)) {
            endCleanupBurst();
            return;
        }
        cleanupBurstTicks--;
        LivingEntity next = findCleanupBurstTarget(serverLevel);
        if (next != null) {
            robot.getLookControl().setLookAt(next, 30.0F, 30.0F);
            emitFlashlightIfDark(next.position());
            emitSuctionParticles(next);
            startSuctionEffect(next.position());
            cleanTarget(next);
            cleanupBurstKills++;
        }
        if (cleanupBurstTicks <= 0 || cleanupBurstKills >= CLEANUP_BURST_MAX_KILLS) {
            endCleanupBurst();
        }
    }

    private void endCleanupBurst() {
        cleanupBurstCenter = null;
        cleanupBurstTicks = 0;
        cleanupBurstKills = 0;
        moveToPatrolPoint();
    }

    private void tickPatrol() {
        if (patrolIndex >= patrolPoints.size()) {
            completeRoutine();
            return;
        }
        BlockPos point = patrolPoints.get(patrolIndex);
        robot.getLookControl().setLookAt(point.getX() + 0.5D, point.getY() + 0.5D, point.getZ() + 0.5D);
        double distanceSqr = robot.distanceToSqr(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D);
        if (distanceSqr <= patrolAdvanceDistanceSqr()) {
            patrolIndex++;
            moveToPatrolPoint();
            return;
        }
        if (--navRetryCooldown <= 0) {
            moveToPatrolPoint();
        }
    }

    private boolean prepareRoutine() {
        CleanerDockBlockEntity dock = robot.getDock();
        if (dock == null || dock.getAreas().isEmpty() || !(robot.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean manual = robot.consumeManualRunRequest();
        long routineKey;
        if (manual) {
            routineKey = MANUAL_KEY;
        } else {
            long dayTime = serverLevel.getDayTime();
            int slot = routineSlot((int) (dayTime % DAY_LENGTH));
            if (slot < 0) {
                return false;
            }
            routineKey = (dayTime / DAY_LENGTH) * 2L + slot;
            if (robot.getLastRoutineKey() == routineKey) {
                return false;
            }
        }
        if (!dock.payForRoutine(false)) {
            return false;
        }
        patrolPoints.clear();
        patrolIndex = 0;
        for (CleanArea area : dock.getAreas()) {
            for (int pass = 0; pass < SWEEP_PASSES; pass++) {
                appendSweepPatrolPoints(area, pass);
            }
        }
        if (!manual) {
            robot.setLastRoutineKey(routineKey);
        }
        robot.level().playSound(null, robot.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.5F, 1.4F);
        return !patrolPoints.isEmpty();
    }

    private void appendSweepPatrolPoints(CleanArea area, int pass) {
        BlockPos min = area.minCorner();
        BlockPos max = area.maxCorner();
        if (max.getX() - min.getX() >= max.getZ() - min.getZ()) {
            int rowCount = max.getZ() - min.getZ() + 1;
            for (int row = 0; row < rowCount; row++) {
                int z = pass % 2 == 0 ? min.getZ() + row : max.getZ() - row;
                appendRowSegments(area, min.getX(), max.getX(), z, true, (row + pass) % 2 == 0);
            }
        } else {
            int rowCount = max.getX() - min.getX() + 1;
            for (int row = 0; row < rowCount; row++) {
                int x = pass % 2 == 0 ? min.getX() + row : max.getX() - row;
                appendRowSegments(area, min.getZ(), max.getZ(), x, false, (row + pass) % 2 == 0);
            }
        }
    }

    private void appendRowSegments(CleanArea area, int start, int end, int fixed, boolean variableIsX, boolean forward) {
        int cursor = start;
        while (cursor <= end) {
            while (cursor <= end && !area.contains(toSweepPoint(area, cursor, fixed, variableIsX))) {
                cursor++;
            }
            if (cursor > end) {
                break;
            }
            int segmentStart = cursor;
            while (cursor <= end && area.contains(toSweepPoint(area, cursor, fixed, variableIsX))) {
                cursor++;
            }
            int segmentEnd = cursor - 1;
            addPatrolPointIfDistinct(toSweepPoint(area, forward ? segmentStart : segmentEnd, fixed, variableIsX));
            addPatrolPointIfDistinct(toSweepPoint(area, forward ? segmentEnd : segmentStart, fixed, variableIsX));
        }
    }

    private static BlockPos toSweepPoint(CleanArea area, int variable, int fixed, boolean variableIsX) {
        return variableIsX ? new BlockPos(variable, area.baseY(), fixed) : new BlockPos(fixed, area.baseY(), variable);
    }

    private void addPatrolPointIfDistinct(BlockPos point) {
        if (patrolPoints.isEmpty() || !patrolPoints.get(patrolPoints.size() - 1).equals(point)) {
            patrolPoints.add(point);
        }
    }

    private static int routineSlot(int timeOfDay) {
        if (timeOfDay >= MORNING_START && timeOfDay <= MORNING_END) {
            return 0;
        }
        if (timeOfDay >= EVENING_START && timeOfDay <= EVENING_END) {
            return 1;
        }
        return -1;
    }

    @Nullable
    private LivingEntity findNearbyPatrolTarget() {
        CleanerDockBlockEntity dock = robot.getDock();
        if (dock == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : robot.level().getEntitiesOfClass(LivingEntity.class,
                robot.getBoundingBox().inflate(PATROL_DETECTION_RADIUS), LivingEntity::isAlive)) {
            if (!isCleanable(dock, candidate)) {
                continue;
            }
            double distance = robot.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private LivingEntity findCleanupBurstTarget(ServerLevel level) {
        CleanerDockBlockEntity dock = robot.getDock();
        if (cleanupBurstCenter == null || dock == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cleanupBurstCenter, cleanupBurstCenter).inflate(CLEANUP_BURST_RADIUS), LivingEntity::isAlive)) {
            if (!isCleanable(dock, candidate)) {
                continue;
            }
            double distance = candidate.position().distanceToSqr(cleanupBurstCenter);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private boolean canCleanNow(LivingEntity candidate) {
        CleanerDockBlockEntity dock = robot.getDock();
        return dock != null && isCleanable(dock, candidate);
    }

    /** Hostile mobs only (the cleaner is for monsters and their drops), never named ones or our own units. */
    private boolean isCleanable(CleanerDockBlockEntity dock, LivingEntity candidate) {
        if (!(candidate instanceof Enemy) || candidate instanceof Player || candidate instanceof CleanerRobotEntity
                || candidate instanceof SuitCompanionEntity || candidate.hasCustomName()) {
            return false;
        }
        if (!dock.inAnyArea(candidate.blockPosition())) {
            return false;
        }
        if (Math.abs(candidate.getY() - robot.getY()) > MAX_CLEANABLE_Y_DELTA) {
            return false;
        }
        Path path = robot.getNavigation().createPath(candidate, 0);
        return path != null && path.canReach() && pathLength(path) <= MAX_CLEANABLE_PATH_LENGTH;
    }

    private static double pathLength(Path path) {
        double length = 0.0D;
        for (int i = 1; i < path.getNodeCount(); i++) {
            var prev = path.getNode(i - 1);
            var next = path.getNode(i);
            double dx = next.x - prev.x, dy = next.y - prev.y, dz = next.z - prev.z;
            length += Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > Math.max(MAX_CLEANABLE_PATH_LENGTH, MAX_PATROL_PATH_LENGTH)) {
                return length;
            }
        }
        return length;
    }

    private void moveToTarget() {
        if (target != null) {
            robot.getNavigation().moveTo(target, 1.05D);
            navRetryCooldown = NAV_RETRY_TICKS;
        }
    }

    private void moveToPatrolPoint() {
        while (patrolIndex < patrolPoints.size() && !canReachPatrolPoint(patrolPoints.get(patrolIndex))) {
            patrolIndex++;
        }
        if (patrolIndex >= patrolPoints.size()) {
            completeRoutine();
            return;
        }
        BlockPos point = patrolPoints.get(patrolIndex);
        robot.getNavigation().moveTo(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D, 1.0D);
        navRetryCooldown = NAV_RETRY_TICKS;
    }

    private double patrolAdvanceDistanceSqr() {
        // Roll through row ends instead of stopping at each one; only the last point needs a real arrival.
        return patrolIndex + 1 < patrolPoints.size() ? PATROL_SMOOTH_ADVANCE_DISTANCE_SQR : PATROL_ARRIVAL_DISTANCE_SQR;
    }

    private void completeRoutine() {
        // Clear the finished path, or the next morning/evening run would never get prepared.
        patrolPoints.clear();
        patrolIndex = 0;
        target = null;
        robot.requestReturn();
    }

    private boolean canReachPatrolPoint(BlockPos point) {
        Path path = robot.getNavigation().createPath(point, 0);
        return path != null && path.canReach() && pathLength(path) <= MAX_PATROL_PATH_LENGTH;
    }

    private void cleanTarget(LivingEntity mob) {
        if (!(robot.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        emitSuctionParticles(mob);
        serverLevel.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                12, 0.25D, 0.25D, 0.25D, 0.03D);
        mob.hurt(robot.damageSources().mobAttack(robot), CLEAN_DAMAGE);
        if (mob.isAlive()) {
            mob.kill();
        }
    }

    private void emitSuctionParticles(LivingEntity entity) {
        emitSuctionParticles(entity.position().add(0.0D, entity.getBbHeight() * 0.45D, 0.0D), entity.getBbHeight());
    }

    private void emitSuctionParticles(Vec3 center, double heightScale) {
        if (!(robot.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 intake = robot.position().add(0.0D, 0.18D, 0.0D);
        var random = robot.getRandom();
        for (int i = 0; i < 6; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.25D + random.nextDouble() * 0.85D;
            double height = (random.nextDouble() - 0.35D) * heightScale;
            Vec3 start = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            Vec3 velocity = intake.subtract(start).normalize().scale(0.16D + random.nextDouble() * 0.08D);
            serverLevel.sendParticles(ParticleTypes.CLOUD, start.x, start.y, start.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        if (robot.tickCount % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, intake.x, intake.y + 0.08D, intake.z, 2, 0.18D, 0.04D, 0.18D, 0.01D);
        }
    }

    private boolean isNearDock() {
        BlockPos dockPos = robot.getDockPos();
        return dockPos != null
                && robot.distanceToSqr(dockPos.getX() + 0.5D, dockPos.getY(), dockPos.getZ() + 0.5D) <= MAX_WORK_START_DISTANCE_SQR;
    }

    /** Robot-vacuum headlight: a short beam of light motes toward the target when it's dark. */
    private void emitFlashlightIfDark(Vec3 targetPos) {
        if (!(robot.level() instanceof ServerLevel serverLevel) || robot.tickCount % 4 != 0) {
            return;
        }
        BlockPos pos = robot.blockPosition();
        if (serverLevel.getMaxLocalRawBrightness(pos) > 7) {
            return;
        }
        Vec3 eye = robot.position().add(0.0D, robot.getBbHeight() * 0.45D, 0.0D);
        Vec3 direction = targetPos.subtract(eye);
        if (direction.lengthSqr() < 0.01D) {
            direction = robot.getLookAngle();
        }
        direction = direction.normalize();
        for (int i = 1; i <= 4; i++) {
            Vec3 point = eye.add(direction.scale(i * 0.65D));
            serverLevel.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
        if (robot.tickCount % 20 == 0) {
            serverLevel.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.NEUTRAL, 0.25F, 1.8F);
        }
    }
}
