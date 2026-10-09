package com.pfkfks.flightsuit.suit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Companion suits' palm repulsor: an instant blast. (The wearer's primary weapon is the continuous beam in
 * SuitWeapons.)
 */
public final class RepulsorHandler {
    private RepulsorHandler() {
    }

    /** Fire from an arbitrary shooter and muzzle point (companion suits). */
    public static void blast(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double range, float damage,
                             Predicate<Entity> canHit) {
        Vec3 end = traceEnd(level, shooter, from, direction, range, damage, canHit);
        effects(level, from, end);
    }

    /** Traces the blast, damages the first hit entity, and returns where the blast stops. */
    private static Vec3 traceEnd(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double range, float damage,
                                 Predicate<Entity> canHit) {
        Vec3 end = from.add(direction.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 beamEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = new AABB(from, beamEnd).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, shooter, from, beamEnd, sweep,
                entity -> !entity.isSpectator() && entity.isPickable() && canHit.test(entity));
        if (entityHit == null) {
            return beamEnd;
        }
        Entity target = entityHit.getEntity();
        DamageSource source = shooter instanceof Player player
                ? shooter.damageSources().playerAttack(player)
                : shooter.damageSources().mobAttack(shooter);
        target.hurt(source, damage);
        if (target instanceof LivingEntity living) {
            living.knockback(0.8D, -direction.x, -direction.z);
        }
        return entityHit.getLocation();
    }

    private static void effects(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        for (double d = 0; d < length; d += 0.4D) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 16, 0.2D, 0.2D, 0.2D, 0.3D);
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 6, 0.15D, 0.15D, 0.15D, 0.02D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3F, 1.9F);
    }
}
