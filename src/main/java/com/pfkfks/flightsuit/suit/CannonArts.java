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
 *   <li>Z: the recoil jump - a ball fired at the ground throws the gunner up and back;</li>
 *   <li>X: Monkey Militia - three monkeys with popguns planted round the gunner for a while;</li>
 *   <li>C: the rolling cannon - three balls in a fan;</li>
 *   <li>V: the Big Huge Gigantic Cannonball - slow, straight, bowling through everything;</li>
 *   <li>B: the monkey rush - a line of monkeys charging down the look.</li>
 * </ul>
 */
public final class CannonArts {
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
    }

    /** Where the muzzle is: the cannon rides on the right shoulder, pointing where the gunner looks. */
    static Vec3 muzzle(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.9D)).add(right.scale(0.4D)).add(0.0D, -0.15D, 0.0D);
    }

    private static void shoot(ServerPlayer player, Vec3 dir, float damage, double radius, boolean recoil) {
        ServerLevel level = player.serverLevel();
        Vec3 from = muzzle(player);
        CannonballEntity.fire(level, player, from, dir.scale(SuitTuning.CANNON_SPEED), damage, radius);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 4, 0.1D, 0.1D, 0.1D, 0.03D);
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 6, 0.05D, 0.05D, 0.05D, 0.08D);
        KiFx.burst(level, from, KiFx.FIRE, 0.8F, 5, KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 0.8F);
        if (recoil) {
            player.setDeltaMovement(player.getDeltaMovement().subtract(dir.scale(SuitTuning.CANNON_RECOIL)));
            player.hurtMarked = true;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
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
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
    }

    // ---------------------------------------------------------------- recoil jump (Z)

    static void recoilJump(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.stepReady) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.RECOIL_JUMP_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.stepReady = now + SuitTuning.RECOIL_JUMP_COOLDOWN_TICKS;
        // Fired down and a little forward: up and back it goes.
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 dir = new Vec3(face.x * 0.5D, -1.0D, face.z * 0.5D).normalize();
        shoot(player, dir, SuitTuning.CANNON_DAMAGE * 0.75F, SuitTuning.CANNON_RADIUS * 0.8D, false);
        player.setDeltaMovement(face.scale(-SuitTuning.RECOIL_JUMP_POWER * 0.55D).add(0.0D, SuitTuning.RECOIL_JUMP_POWER, 0.0D));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SPIN_ATTACK, 0));
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
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
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
