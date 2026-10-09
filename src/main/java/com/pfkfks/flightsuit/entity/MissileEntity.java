package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mark 1 micro-missile (SuitWeapons): pops up out of a shoulder pod, turns toward its locked target (or the
 * aim point when nothing was locked), accelerates, and blows up on whatever it meets - mobs and blocks alike.
 * Its blast spares the shooter's side (SuitWeapons#onAttack).
 */
public class MissileEntity extends ThrowableProjectile {
    private static final int BOOST_DELAY = 5;
    private static final int LIFETIME = 120;

    private LivingEntity target;
    private Vec3 aim = Vec3.ZERO;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
    }

    public static void launch(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 velocity, LivingEntity target, Vec3 aim) {
        MissileEntity missile = new MissileEntity(ModEntities.MISSILE.get(), level);
        missile.setOwner(owner);
        missile.setPos(from.x, from.y, from.z);
        missile.setDeltaMovement(velocity);
        missile.target = target;
        missile.aim = aim;
        level.addFreshEntity(missile);
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 4, 0.05D, 0.05D, 0.05D, 0.02D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.7F);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount > LIFETIME) {
                explode();
                return;
            }
            if (tickCount > BOOST_DELAY) {
                // Motor lit: speed up and steer for the target, a little more sharply as it closes in.
                Vec3 goal = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : aim;
                Vec3 velocity = getDeltaMovement();
                double speed = Math.min(2.2D, 0.5D + (tickCount - BOOST_DELAY) * 0.09D);
                Vec3 want = goal.subtract(position()).normalize();
                double turn = goal.distanceToSqr(position()) < 64.0D ? 0.5D : 0.25D;
                setDeltaMovement(velocity.normalize().scale(1.0D - turn).add(want.scale(turn)).normalize().scale(speed));
            } else {
                // Coasting up out of the pod.
                setDeltaMovement(getDeltaMovement().scale(0.9D));
            }
        }
        super.tick();
        if (level().isClientSide) {
            Vec3 tail = position().subtract(getDeltaMovement().normalize().scale(0.3D));
            level().addParticle(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 0.0D, 0.01D, 0.0D);
            if (tickCount > BOOST_DELAY) {
                level().addParticle(ParticleTypes.FLAME, tail.x, tail.y, tail.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(getOwner() instanceof ServerPlayer owner && SuitWeapons.isFriendly(owner, entity));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            explode();
        }
    }

    private void explode() {
        level().explode(this, damageSources().explosion(this, getOwner()), null, getX(), getY(), getZ(),
                SuitTuning.MISSILE_POWER, false, Level.ExplosionInteraction.TNT);
        discard();
    }
}
