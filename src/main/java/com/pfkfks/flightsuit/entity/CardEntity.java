package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.PhantomCards;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A phantom's thrown card (PhantomCards): flies flat and spinning, curves onto its target if it has one,
 * and is gone on the first thing it hits. Each one that lands fills the Judgment Draw gauge.
 */
public class CardEntity extends ThrowableProjectile {
    public static final byte BLANCHE = 0;
    public static final byte NOIR = 1;

    private static final EntityDataAccessor<Byte> STYLE = SynchedEntityData.defineId(CardEntity.class, EntityDataSerializers.BYTE);
    private static final int LIFETIME = 60;

    private LivingEntity target;
    private float damage;

    public CardEntity(EntityType<? extends CardEntity> type, Level level) {
        super(type, level);
    }

    public static void throwCard(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 velocity, LivingEntity target, float damage, byte style) {
        CardEntity card = new CardEntity(ModEntities.CARD.get(), level);
        card.setOwner(owner);
        card.setPos(from.x, from.y, from.z);
        card.setDeltaMovement(velocity);
        card.target = target;
        card.damage = damage;
        card.entityData.set(STYLE, style);
        level.addFreshEntity(card);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STYLE, BLANCHE);
    }

    public byte getStyle() {
        return entityData.get(STYLE);
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount > LIFETIME) {
                discard();
                return;
            }
            if (target != null && target.isAlive() && tickCount > 2) {
                // Curve in: keep the speed, swing the heading toward the target.
                Vec3 velocity = getDeltaMovement();
                double speed = Math.max(1.2D, velocity.length());
                Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed);
                setDeltaMovement(velocity.scale(0.7D).add(want.scale(0.3D)).normalize().scale(speed));
            }
        }
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            switch (getStyle()) {
                case NOIR -> level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
                default -> level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity
                && !(getOwner() instanceof ServerPlayer owner && SuitWeapons.isFriendly(owner, entity));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || !(result.getEntity() instanceof LivingEntity hit)) {
            return;
        }
        // A rapid stream: every card counts, straight through the hurt cooldown.
        hit.invulnerableTime = 0;
        if (hit.hurt(damageSources().thrown(this, getOwner()), damage) && getOwner() instanceof ServerPlayer owner) {
            PhantomCards.onCardHit(owner);
        }
        ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 6, 0.15D, 0.15D, 0.15D, 0.1D);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 3, 0.1D, 0.1D, 0.1D, 0.05D);
            discard();
        }
    }
}
