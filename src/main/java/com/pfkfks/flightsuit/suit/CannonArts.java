package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.entity.CannonballEntity;
import com.pfkfks.flightsuit.entity.MonkeyEntity;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Cannon Shooter Mark 6 (M18, MapleStory's Cannon Shooter - the area fighter): a cannon in hand (SuitWeaponItem) and
 * a monkey at heel (MonkeyEntity).
 * <ul>
 *   <li>right click held: cannonballs, one every CANNON_INTERVAL ticks, each shoving the gunner back a little;</li>
 *   <li>Z: the cannon jump - a ball fired at the ground throws the gunner into the air, twice in a row, the monkey
 *   kicking them on at the top; pressed as they land from a height, the landing slam (2026-10-12: no flight, so
 *   the gunner moves by recoil);</li>
 *   <li>X: Monkey Militia - three monkeys with popguns planted round the gunner for a while;</li>
 *   <li>C: the rolling cannon - three balls in a fan;</li>
 *   <li>V: the Big Huge Gigantic Cannonball - slow, straight, bowling through everything;</li>
 *   <li>B: the monkey rush - a line of monkeys charging down the look.</li>
 * </ul>
 */
public final class CannonArts {
    /** Ticks after a cannon jump during which "on the ground" is still the take-off. */
    private static final int LIFT_OFF_TICKS = 3;

    private CannonArts() {
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (suitClass != SuitClass.CANNONEER) {
            return;
        }
        // The monkey at heel comes with the full set and goes with it (MonkeyEntity minds the going).
        if (player.tickCount % 20 == 3 && WornSuit.of(player).fullSet() && MonkeyEntity.of(player.serverLevel(), player).isEmpty()) {
            MonkeyEntity.heel(player.serverLevel(), player);
        }
        tickAirtime(player, state);
    }

    /**
     * Keeps count of the airtime (2026-10-12): the top of it, the landing and from how high - and at the top of a
     * cannon jump, the monkey's kick forward (MapleStory's Monkey Boost).
     */
    private static void tickAirtime(ServerPlayer player, SuitWeapons.State state) {
        // Just thrown: the client still reports the ground for a tick or two - that's the take-off, not a landing.
        boolean ground = player.onGround() && player.level().getGameTime() - state.launchedAt > LIFT_OFF_TICKS;
        double y = player.getY();
        if (ground) {
            if (!state.wasGrounded) {
                state.landedAt = player.level().getGameTime();
                state.landedFall = state.airPeak - y;
            }
            state.cannonJumps = 0;
            state.cannonAir = false;
            state.monkeyKicked = false;
            state.airPeak = y;
        } else {
            state.airPeak = state.wasGrounded ? y : Math.max(state.airPeak, y);
            if (state.cannonAir && !state.monkeyKicked && y < state.lastY - 0.01D) {
                state.monkeyKicked = true;
                monkeyKick(player, state);
            }
        }
        state.descending = !ground && y < state.lastY - 0.01D;
        state.wasGrounded = ground;
        state.travel = player.position().subtract(state.lastPos).multiply(1.0D, 0.0D, 1.0D);
        state.lastPos = player.position();
        state.lastY = y;
    }

    /**
     * The monkey's kick at the top of a cannon jump: a shove on the way the gunner is already going (they fly away
     * from their aim, so not where they look) and a little up, if the monkey is about.
     */
    private static void monkeyKick(ServerPlayer player, SuitWeapons.State state) {
        ServerLevel level = player.serverLevel();
        java.util.Optional<MonkeyEntity> monkey = MonkeyEntity.of(level, player);
        if (monkey.isEmpty()) {
            return;
        }
        Vec3 going = state.travel.lengthSqr() > 1.0E-4D ? state.travel : player.getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
        going = going.lengthSqr() > 1.0E-4D ? going.normalize() : Vec3.ZERO;
        Vec3 behind = player.position().subtract(going.scale(0.8D));
        monkey.get().moveTo(behind.x, behind.y, behind.z, player.getYRot(), 0.0F);
        player.setDeltaMovement(going.scale(SuitTuning.MONKEY_KICK_FORWARD).add(0.0D, SuitTuning.MONKEY_KICK_UP, 0.0D));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        KiFx.burst(level, behind.add(0.0D, 0.6D, 0.0D), KiFx.FIRE, 0.6F, 6, KiFx.RING);
        level.playSound(null, player.blockPosition(), SoundEvents.PANDA_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Where the muzzle is: the cannon is held in both hands in front of the chest (SuitWeaponItem's arm pose), pointing where the gunner looks. */
    static Vec3 muzzle(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(1.1D)).add(right.scale(0.1D)).add(0.0D, -0.35D, 0.0D);
    }

    private static CannonballEntity shoot(ServerPlayer player, Vec3 dir, float damage, double radius, boolean recoil) {
        ServerLevel level = player.serverLevel();
        Vec3 from = muzzle(player);
        CannonballEntity ball = CannonballEntity.fire(level, player, from, dir.scale(SuitTuning.CANNON_SPEED), damage, radius);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 4, 0.1D, 0.1D, 0.1D, 0.03D);
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 6, 0.05D, 0.05D, 0.05D, 0.08D);
        KiFx.burst(level, from, KiFx.FIRE, 0.8F, 5, KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (recoil) {
            player.setDeltaMovement(player.getDeltaMovement().subtract(dir.scale(SuitTuning.CANNON_RECOIL)));
            player.hurtMarked = true;
        }
        return ball;
    }

    // ---------------------------------------------------------------- cannon fire (right click held)

    static void tickCannon(ServerPlayer player, SuitWeapons.State state) {
        if (state.firingTicks % SuitTuning.CANNON_INTERVAL != 1) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CANNON_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        SuitWeapons.Aim aim = SuitWeapons.aim(player, 48.0D);
        Vec3 dir = aim.end().subtract(muzzle(player)).normalize();
        // A touch of lift so the arc lands about where the crosshair sits.
        dir = dir.add(0.0D, 0.04D + aim.end().distanceTo(muzzle(player)) * 0.002D, 0.0D).normalize();
        shoot(player, dir, SuitTuning.CANNON_DAMAGE, SuitTuning.CANNON_RADIUS, true);
    }

    // ---------------------------------------------------------------- cannon jump and landing slam (Z)

    /**
     * Z (2026-10-12, MapleStory's Cannon Jump): the cannon fires where they aim and the recoil throws the gunner the
     * opposite way - look down to go up, look ahead to be thrown back, look down and behind to leap forward (the
     * user: the opposite of the aim - intuitive, hard to master on purpose). Twice in a row before landing (the
     * second a little weaker); the cooldown runs from the ground. Pressed as they touch down after a fall, it's the
     * landing slam - which throws them off again.
     */
    static void recoilJump(ServerPlayer player, SuitWeapons.State state) {
        if (trySlam(player, state)) {
            return;
        }
        long now = player.level().getGameTime();
        boolean ground = player.onGround();
        if (state.cannonJumps >= SuitTuning.CANNON_JUMP_CHAIN || state.cannonJumps == 0 && now < state.stepReady) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.RECOIL_JUMP_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        if (state.cannonJumps == 0) {
            state.stepReady = now + SuitTuning.RECOIL_JUMP_COOLDOWN_TICKS;
        }
        double power = SuitTuning.RECOIL_JUMP_POWER * (state.cannonJumps == 0 ? 1.0D : SuitTuning.CANNON_JUMP_CHAIN_POWER);
        state.cannonJumps++;
        // The monkey kicks once an airtime (reset on the ground), at the first top after a cannon jump.
        state.cannonAir = true;
        shoot(player, player.getLookAngle(), SuitTuning.CANNON_DAMAGE * 0.75F, SuitTuning.CANNON_RADIUS * 0.8D, false).keepBlocks();
        launch(player, state, power, !ground);
    }

    /** The recoil: thrown straight away from the aim at {@code power}, keeping a little of what they had in the air. */
    private static void launch(ServerPlayer player, SuitWeapons.State state, double power, boolean airborne) {
        state.launchedAt = player.level().getGameTime();
        state.wasGrounded = false;
        Vec3 keep = airborne ? player.getDeltaMovement().multiply(0.3D, 0.0D, 0.3D) : Vec3.ZERO;
        player.setDeltaMovement(keep.add(player.getLookAngle().scale(-power)));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SPIN_ATTACK, 0));
    }

    /**
     * The landing slam: after a fall of SLAM_MIN_FALL or more, Z with the ground just under the feet on the way down
     * (or a moment after touching down) - the cannon fired into the ground as they land, a shockwave that grows with
     * the fall, and the gunner thrown off again away from the aim (a fresh cannon jump: one more in the air, a new
     * monkey kick), so a well-timed run of them barely touches down. A plain landing stays a plain landing.
     */
    private static boolean trySlam(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        double fall;
        if (player.onGround()) {
            if (now - state.landedAt > SuitTuning.CANNON_SLAM_LATE_TICKS || state.landedFall < SuitTuning.CANNON_SLAM_MIN_FALL) {
                return false;
            }
            fall = state.landedFall;
        } else {
            fall = state.airPeak - player.getY();
            if (fall < SuitTuning.CANNON_SLAM_MIN_FALL || !state.descending || groundBelow(player) > SuitTuning.CANNON_SLAM_WINDOW) {
                return false;
            }
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CANNON_SLAM_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return true;
        }
        // Spent: this landing can't slam twice.
        state.landedAt = Long.MIN_VALUE / 2;
        state.airPeak = player.getY();
        ServerLevel level = player.serverLevel();
        Vec3 at = player.onGround() ? player.position() : player.position().subtract(0.0D, groundBelow(player), 0.0D);
        // Off again: a fresh cannon jump out of the slam.
        state.cannonJumps = 1;
        state.cannonAir = true;
        state.monkeyKicked = false;
        launch(player, state, SuitTuning.RECOIL_JUMP_POWER, !player.onGround());
        double radius = Math.min(SuitTuning.CANNON_SLAM_RADIUS_MAX, SuitTuning.CANNON_SLAM_RADIUS + fall * SuitTuning.CANNON_SLAM_RADIUS_PER_BLOCK);
        float damage = (float) Math.min(SuitTuning.CANNON_SLAM_DAMAGE_MAX, SuitTuning.CANNON_SLAM_DAMAGE + fall * SuitTuning.CANNON_SLAM_DAMAGE_PER_BLOCK);
        for (net.minecraft.world.entity.LivingEntity victim : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius, 2.0D, radius),
                entity -> entity.isAlive() && entity != player && !SuitWeapons.isFriendly(player, entity)
                        && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity))) {
            Vec3 out = victim.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            if (out.length() > radius) {
                continue;
            }
            float share = (float) (1.0D - 0.5D * out.length() / radius);
            victim.invulnerableTime = 0;
            victim.hurt(player.damageSources().playerAttack(player), damage * share);
            Vec3 push = out.lengthSqr() < 1.0E-4D ? Vec3.ZERO : out.normalize();
            victim.setDeltaMovement(push.scale(0.9D).add(0.0D, 0.6D, 0.0D));
            victim.hurtMarked = true;
        }
        Vec3 ground = at.add(0.0D, 0.1D, 0.0D);
        KiFx.burst(level, ground, KiFx.FIRE, (float) radius, 14, KiFx.RING);
        KiFx.burst(level, ground.add(0.0D, 0.5D, 0.0D), KiFx.FIRE, (float) (radius * 0.5D), 12, KiFx.BLAST);
        net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(net.minecraft.core.BlockPos.containing(at).below());
        if (!floor.isAir()) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12.0D;
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, floor),
                        at.x + Math.cos(angle) * radius * 0.6D, at.y + 0.2D, at.z + Math.sin(angle) * radius * 0.6D,
                        4, 0.3D, 0.2D, 0.3D, 0.3D);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3D, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
        com.pfkfks.flightsuit.cutscene.CutsceneRunner.shake(level, at, radius * 3.0D, 8);
        player.displayClientMessage(Component.translatable("message.flightsuit.cannon_slam"), true);
        return true;
    }

    /** How far below the feet the ground is (up to 6 blocks; 6 = nothing found). */
    private static double groundBelow(ServerPlayer player) {
        Vec3 from = player.position();
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(from,
                from.subtract(0.0D, 6.0D, 0.0D), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 6.0D : from.y - hit.getLocation().y;
    }

    // ---------------------------------------------------------------- Monkey Militia (X)

    static void militia(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.MILITIA_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.MILITIA_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < SuitTuning.MILITIA_COUNT; i++) {
            double angle = Math.toRadians(player.getYRot() + 90.0D) + Math.PI * 2.0D * i / SuitTuning.MILITIA_COUNT + Math.PI;
            Vec3 at = SuitSkills.standable(level, player, player.position().add(Math.cos(angle) * 2.2D, 0.0D, Math.sin(angle) * 2.2D));
            if (at == null) {
                at = player.position();
            }
            MonkeyEntity.turret(level, player, at, SuitTuning.MILITIA_TICKS);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PANDA_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.9F);
        player.swing(InteractionHand.OFF_HAND, true);
        player.displayClientMessage(Component.translatable("message.flightsuit.militia"), true);
    }

    // ---------------------------------------------------------------- rolling cannon (C)

    static void rollingCannon(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.ROLLING_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.ROLLING_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        SuitWeapons.Aim aim = SuitWeapons.aim(player, 48.0D);
        Vec3 dir = aim.end().subtract(muzzle(player)).normalize().add(0.0D, 0.05D, 0.0D).normalize();
        for (int i = 0; i < SuitTuning.ROLLING_SHOTS; i++) {
            double turn = Math.toRadians((i - (SuitTuning.ROLLING_SHOTS - 1) / 2.0D) * SuitTuning.ROLLING_SPREAD_DEG);
            Vec3 d = dir.yRot((float) turn);
            shoot(player, d, SuitTuning.CANNON_DAMAGE, SuitTuning.CANNON_RADIUS, false);
        }
        player.setDeltaMovement(player.getDeltaMovement().subtract(dir.scale(SuitTuning.CANNON_RECOIL * 2.0D)));
        player.hurtMarked = true;
    }

    // ---------------------------------------------------------------- Big Huge Gigantic Cannonball (V)

    static void giantCannonball(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.BHGC_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.BHGC_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(2.2D)).add(0.0D, -0.4D, 0.0D);
        CannonballEntity.giant(level, player, from, look.scale(SuitTuning.BHGC_SPEED), SuitTuning.BHGC_DAMAGE, SuitTuning.BHGC_RADIUS,
                SuitTuning.BHGC_RANGE);
        KiFx.burst(level, from, KiFx.FIRE, 3.0F, 12, KiFx.FLASH);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 20, 0.6D, 0.6D, 0.6D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.setDeltaMovement(player.getDeltaMovement().subtract(look.scale(0.8D)));
        player.hurtMarked = true;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_SLAM, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.bhgc"), true);
    }

    // ---------------------------------------------------------------- monkey rush (B)

    static void monkeyRush(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.RUSH_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.RUSH_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 side = new Vec3(-face.z, 0.0D, face.x);
        // Three abreast, from the gunner's feet, the one at heel leading if it's there.
        for (int i = -1; i <= 1; i++) {
            Vec3 at = player.position().add(face.scale(1.0D)).add(side.scale(i * 0.9D));
            MonkeyEntity.rush(level, player, at, face, SuitTuning.RUSH_TICKS + Math.abs(i) * 2);
        }
        MonkeyEntity.of(level, player).ifPresent(monkey -> {
            Vec3 at = player.position().add(face.scale(1.6D));
            monkey.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
        });
        level.playSound(null, player.blockPosition(), SoundEvents.PANDA_AGGRESSIVE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.8F);
        player.swing(InteractionHand.OFF_HAND, true);
        player.displayClientMessage(Component.translatable("message.flightsuit.monkey_rush"), true);
    }
}
