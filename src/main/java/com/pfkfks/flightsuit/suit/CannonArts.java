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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Cannon Shooter Mark 6 (M18, MapleStory's Cannon Shooter - the area fighter): a cannon in hand (SuitWeaponItem) and
 * a monkey at heel (MonkeyEntity).
 * <ul>
 *   <li>right click held: cannonballs, one every CANNON_INTERVAL ticks, each shoving the gunner back a little;</li>
 *   <li>Z: the cannon jump - a ball fired at the ground throws the gunner into the air, twice in a row, the monkey
 *   kicking them on at the top; pressed as they land from a height, the landing slam (2026-10-12: no flight, so
 *   the gunner moves by recoil);</li>
 *   <li>X: the monkey's machine gun (2026-10-12; once Monkey Militia, briefly towers) - the monkey at heel, which
 *   fights with a pistol, sprays a machine gun for a while;</li>
 *   <li>C: the flame burst (2026-10-12; once the rolling cannon) - a wide cone of fire for a few seconds, rooted;</li>
 *   <li>V: the Big Huge Gigantic Cannonball - slow, straight, bowling through everything;</li>
 *   <li>B: C4 (2026-10-12; once the monkey rush) - stuck on the block or foe in front, set off by B again.</li>
 * </ul>
 */
public final class CannonArts {
    /** Ticks after a cannon jump during which "on the ground" is still the take-off. */
    private static final int LIFT_OFF_TICKS = 3;

    private CannonArts() {
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (suitClass != SuitClass.CANNONEER) {
            // Out of the suit: the flame stops and a C4 left stuck somewhere is disarmed.
            state.flameTicks = 0;
            if (state.c4 != null) {
                state.c4.discard();
                state.c4 = null;
            }
            return;
        }
        tickFlame(player, state);
        tickC4(player, state);
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

    // ---------------------------------------------------------------- the monkey's machine gun (X, 2026-10-12)

    /** The monkey at heel trades its pistol for a machine gun for a while (MonkeyEntity). */
    static void machineGun(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        java.util.Optional<MonkeyEntity> monkey = MonkeyEntity.of(player.serverLevel(), player);
        if (monkey.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_monkey"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.MONKEY_MG_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        monkey.get().startMachineGun(SuitTuning.MONKEY_MG_TICKS);
        state.skill1Ready = now + SuitTuning.MONKEY_MG_COOLDOWN_TICKS;
        state.machineGunUntil = now + SuitTuning.MONKEY_MG_TICKS;
        SuitWeapons.sendStatus(player, state);
        player.swing(InteractionHand.OFF_HAND, true);
        player.displayClientMessage(Component.translatable("message.flightsuit.machine_gun"), true);
    }

    // ---------------------------------------------------------------- flame burst (C, 2026-10-12)

    /**
     * Flame out of the cannon's mouth for a few seconds (user: "불꽃을 팍 뿜어대는 기술, 광역"; after the first look
     * "넉백 없애고 몇 초 동안 쏘되 고정"): the gunner is rooted and turns the stream with the look; everything in the
     * wide cone burns, nothing is thrown back.
     */
    static void flameBurst(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (state.flameTicks > 0) {
            return;
        }
        if (now < state.skill2Ready) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FLAME_BURST_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.FLAME_BURST_COOLDOWN_TICKS;
        state.flameTicks = SuitTuning.FLAME_BURST_TICKS;
        SuitWeapons.sendStatus(player, state);
        player.displayClientMessage(Component.translatable("message.flightsuit.flame_burst"), true);
    }

    private static void tickFlame(ServerPlayer player, SuitWeapons.State state) {
        if (state.flameTicks <= 0) {
            return;
        }
        int t = state.flameTicks--;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false, false));
        if (t % SuitTuning.FLAME_BURST_PULSE != 0) {
            return;
        }
        flameBurst(player.serverLevel(), player, muzzle(player), player.getLookAngle(), SuitTuning.FLAME_BURST_DAMAGE,
                entity -> !SuitWeapons.isFriendly(player, entity) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)
                        && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player || entity == player.getLastHurtMob()));
        if (t % (SuitTuning.FLAME_BURST_PULSE * 3) == 0) {
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /** One pulse of it (the companion Mark 6 uses one too): drawn in fire along the cone; the number it caught. */
    public static int flameBurst(ServerLevel level, net.minecraft.world.entity.LivingEntity caster, Vec3 from, Vec3 look, float damage,
                                 java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> hits) {
        double reach = SuitTuning.FLAME_BURST_REACH;
        double cos = Math.cos(Math.toRadians(SuitTuning.FLAME_BURST_ARC_DEG / 2.0D));
        int count = 0;
        for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                caster.getBoundingBox().inflate(reach), entity -> entity != caster && entity.isAlive() && hits.test(entity))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(from);
            if (to.length() - target.getBbWidth() / 2.0D > reach || to.normalize().dot(look) < cos || !caster.hasLineOfSight(target)) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(caster instanceof ServerPlayer player ? caster.damageSources().playerAttack(player) : caster.damageSources().mobAttack(caster),
                    damage);
            target.setSecondsOnFire(SuitTuning.FLAME_BURST_BURN_SECONDS);
            count++;
        }
        // The flame: fireballs blooming bigger the farther out, a spread of them across the cone.
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        Vec3 side = flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-flat.z, 0.0D, flat.x).normalize();
        double spread = Math.tan(Math.toRadians(SuitTuning.FLAME_BURST_ARC_DEG / 2.0D)) * 0.75D;
        for (double d = 1.2D; d <= reach; d += 1.6D) {
            Vec3 centre = from.add(look.scale(d));
            float size = (float) (0.6D + d * 0.32D);
            int life = 8 + (int) d;
            KiFx.burst(level, centre, d < 3.0D ? KiFx.BURNING : KiFx.FIRE, size, life, KiFx.BLAST);
            if (d > 3.0D) {
                KiFx.burst(level, centre.add(side.scale(d * spread)), KiFx.FIRE, size * 0.8F, life, KiFx.BLAST);
                KiFx.burst(level, centre.subtract(side.scale(d * spread)), KiFx.FIRE, size * 0.8F, life, KiFx.BLAST);
            }
        }
        Vec3 far = from.add(look.scale(reach));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, far.x, far.y, far.z, 10, 1.2D, 0.6D, 1.2D, 0.02D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
        // The ground may catch, like the scrap suit's flame (no village spared: DESIGN.md M18 2nd feedback).
        if (level.random.nextFloat() < SuitTuning.FLAME_BURST_IGNITE_CHANCE) {
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(from.add(look.scale(2.0D + level.random.nextDouble() * (reach - 2.0D))));
            for (int i = 0; i < 4 && level.getBlockState(at.below()).isAir(); i++) {
                at = at.below();
            }
            if (level.isEmptyBlock(at) && net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, at, net.minecraft.core.Direction.UP)
                    && (!(caster instanceof ServerPlayer player) || level.mayInteract(player, at))) {
                level.setBlock(at, net.minecraft.world.level.block.BaseFireBlock.getState(level, at), 11);
            }
        }
        return count;
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

    // ---------------------------------------------------------------- C4 (B, 2026-10-12)

    /**
     * A C4 (user: "근접 폭탄, 블럭이나 엔티티에 붙이는 거"): B sticks it on the block or foe in front (it rides along on
     * a foe); B again sets it off - or it goes off on its own after a while, beeping faster as it nears.
     */
    static void c4(ServerPlayer player, SuitWeapons.State state) {
        if (state.c4 != null) {
            detonate(player, state);
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        SuitWeapons.Aim aim = SuitWeapons.aim(player, SuitTuning.C4_RANGE);
        Vec3 eye = player.getEyePosition();
        LivingEntity on = aim.target();
        net.minecraft.world.phys.BlockHitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye,
                eye.add(player.getLookAngle().scale(SuitTuning.C4_RANGE)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 at;
        if (on != null) {
            // On its side where the look met it.
            at = aim.end();
        } else if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            at = block.getLocation();
        } else {
            player.displayClientMessage(Component.translatable("message.flightsuit.c4_nothing"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.C4_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.c4Fuse = now + SuitTuning.C4_FUSE_TICKS;
        ServerLevel level = player.serverLevel();
        state.c4 = on != null ? com.pfkfks.flightsuit.entity.C4Entity.onEntity(level, on, at, state.c4Fuse)
                : com.pfkfks.flightsuit.entity.C4Entity.onBlock(level, at, block.getDirection(), state.c4Fuse);
        player.swing(InteractionHand.OFF_HAND, true);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8F, 2.0F);
        player.displayClientMessage(on != null ? Component.translatable("message.flightsuit.c4_on", on.getDisplayName())
                : Component.translatable("message.flightsuit.c4_placed"), true);
    }

    private static void tickC4(ServerPlayer player, SuitWeapons.State state) {
        if (state.c4 == null) {
            return;
        }
        if (state.c4.isRemoved() || state.c4.level() != player.level()) {
            state.c4 = null;
            return;
        }
        long left = state.c4Fuse - player.level().getGameTime();
        if (left <= 0L) {
            detonate(player, state);
            return;
        }
        // A beep with each blink (the light is the C4's own, client/C4Renderer), faster near the end.
        int every = left > 60L ? 16 : left > 20L ? 6 : 2;
        if (left % every == 0) {
            ServerLevel level = player.serverLevel();
            Vec3 at = state.c4.position();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.7F, 2.0F);
        }
    }

    private static void detonate(ServerPlayer player, SuitWeapons.State state) {
        com.pfkfks.flightsuit.entity.C4Entity c4 = state.c4;
        state.c4 = null;
        if (c4 == null) {
            return;
        }
        Vec3 at = c4.position();
        LivingEntity on = c4.host();
        c4.discard();
        ServerLevel level = player.serverLevel();
        double radius = SuitTuning.C4_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                entity -> entity.isAlive() && (entity == on || !SuitWeapons.isFriendly(player, entity) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)
                        && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player)))) {
            double distance = victim.getBoundingBox().getCenter().distanceTo(at);
            if (victim != on && distance > radius + victim.getBbWidth() / 2.0D) {
                continue;
            }
            float share = victim == on ? 1.0F : (float) (1.0D - 0.5D * Math.min(1.0D, distance / radius));
            victim.invulnerableTime = 0;
            victim.hurt(player.damageSources().playerAttack(player), SuitTuning.C4_DAMAGE * share);
            Vec3 push = victim.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                victim.knockback(1.2D, -push.x, -push.z);
            }
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
            victim.hurtMarked = true;
        }
        CannonballEntity.crater(level, at, at, SuitTuning.C4_BREAK_RADIUS, SuitTuning.CANNON_BREAK_HARDNESS, false, player, player);
        KiFx.burst(level, at, KiFx.FIRE, (float) radius, 16, KiFx.BLAST);
        KiFx.burst(level, at, KiFx.WHITE, 1.6F, 6, KiFx.FLASH);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8F, 0.8F);
        com.pfkfks.flightsuit.cutscene.CutsceneRunner.shake(level, at, 16.0D, 8);
        state.skill3Ready = level.getGameTime() + SuitTuning.C4_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
    }
}
