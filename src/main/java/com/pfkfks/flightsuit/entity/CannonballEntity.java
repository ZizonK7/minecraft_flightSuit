package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * M18 Cannon Shooter (CannonArts): an iron ball from the cannon. The ordinary one flies a shallow arc and bursts on
 * the first thing it meets, hurting everything round the burst and shoving it back. The giant one (the ultimate)
 * is five times the size, flies straight and slow, bowls through whatever it meets - each foe hurt once - and
 * bursts big where it stops. Whatever a ball strikes head-on takes the hit (the splash is for foes); where it bursts,
 * soft blocks go too - the giant one cracks stone (never inside the shooter's own village). Never hurts the
 * shooter's side (SuitWeapons.isFriendly).
 */
public class CannonballEntity extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(CannonballEntity.class, EntityDataSerializers.FLOAT);
    private static final int LIFETIME = 200;

    private float damage = 8.0F;
    private double radius = 3.0D;
    private boolean giant;
    private double range = 64.0D;
    private double travelled;
    private final Set<LivingEntity> bowled = new HashSet<>();

    public CannonballEntity(EntityType<? extends CannonballEntity> type, Level level) {
        super(type, level);
    }

    /** An ordinary ball: {@code damage} over {@code radius} where it bursts. */
    public static CannonballEntity fire(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 velocity, float damage, double radius) {
        CannonballEntity ball = new CannonballEntity(ModEntities.CANNONBALL.get(), level);
        ball.setOwner(owner);
        ball.setPos(from.x, from.y, from.z);
        ball.setDeltaMovement(velocity);
        ball.damage = damage;
        ball.radius = radius;
        ball.entityData.set(SIZE, 0.5F);
        level.addFreshEntity(ball);
        return ball;
    }

    /** The big huge gigantic cannonball: bowls {@code range} blocks, {@code damage} to each it passes, {@code blast} at the end. */
    public static CannonballEntity giant(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 velocity, float damage, double radius,
                                         double range) {
        CannonballEntity ball = fire(level, owner, from, velocity, damage, radius);
        ball.giant = true;
        ball.range = range;
        ball.entityData.set(SIZE, 2.5F);
        ball.setNoGravity(true);
        return ball;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SIZE, 0.5F);
    }

    /** Across, in blocks - for the renderer and the hit box. */
    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected float getGravity() {
        return giant ? 0.0F : 0.03F;
    }

    @Override
    public void tick() {
        Vec3 before = position();
        super.tick();
        if (level().isClientSide) {
            // A smoke trail, and the giant one's fuse of sparks.
            Vec3 tail = position().subtract(getDeltaMovement().normalize().scale(size() * 0.5D));
            level().addParticle(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 0.0D, 0.01D, 0.0D);
            if (giant() && random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.LAVA, tail.x, tail.y, tail.z, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        travelled += position().distanceTo(before);
        if (tickCount > LIFETIME || travelled > range) {
            burst(null);
            return;
        }
        if (giant) {
            // Bowling: everything it passes through takes the hit once and is thrown aside.
            for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius * 0.5D),
                    entity -> foe(entity) && !bowled.contains(entity))) {
                bowled.add(victim);
                victim.invulnerableTime = 0;
                victim.hurt(source(), damage);
                Vec3 push = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize().add(getDeltaMovement().normalize().scale(0.6D));
                victim.knockback(1.4D, -push.x, -push.z);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.5D, 0.0D));
                victim.hurtMarked = true;
                level().playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            if (tickCount % 4 == 0) {
                level().playSound(null, blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.5F, 0.5F);
            }
        }
    }

    /** The giant one is told apart by its size on both sides. */
    public boolean giant() {
        return size() > 1.5F;
    }

    private DamageSource source() {
        return getOwner() instanceof ServerPlayer player ? damageSources().playerAttack(player)
                : getOwner() instanceof LivingEntity living ? damageSources().mobAttack(living) : damageSources().generic();
    }

    /** Who the burst hurts: monsters and whoever is fighting the shooter - never the shooter's side or kneeling captives. */
    private boolean foe(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == getOwner()) {
            return false;
        }
        if (getOwner() instanceof ServerPlayer owner && SuitWeapons.isFriendly(owner, living)) {
            return false;
        }
        if (getOwner() instanceof SuitCompanionEntity companion && !companion.isFoe(living)) {
            return false;
        }
        return (living instanceof Enemy || living.getLastHurtByMob() == getOwner()) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(living);
    }

    /** Anyone but the shooter's side: a ball to the face is a ball to the face. */
    private boolean friendly(Entity entity) {
        if (entity == getOwner()) {
            return true;
        }
        if (getOwner() instanceof ServerPlayer owner && SuitWeapons.isFriendly(owner, entity)) {
            return true;
        }
        return getOwner() instanceof SuitCompanionEntity companion && entity instanceof LivingEntity living && !companion.isFoe(living);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity living && living.isAlive() && !friendly(entity)
                && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(living) && !(giant && bowled.contains(entity));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (giant) {
            // Bowls on through (tick handles the hit).
            return;
        }
        super.onHitEntity(result);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) {
            return;
        }
        if (giant && result.getType() == HitResult.Type.ENTITY) {
            return;
        }
        burst(result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) result).getEntity() : null);
    }

    private void burst(Entity direct) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        Vec3 at = position();
        double reach = giant ? radius * 2.0D : radius;
        float hit = giant ? damage * 1.2F : damage;
        Set<LivingEntity> victims = new HashSet<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach), this::foe));
        if (direct instanceof LivingEntity living && living.isAlive()) {
            victims.add(living);
        }
        for (LivingEntity victim : victims) {
            double distance = victim.getBoundingBox().getCenter().distanceTo(at);
            if (distance > reach + victim.getBbWidth() / 2.0D) {
                continue;
            }
            // Full damage close in, half at the edge; the one it struck takes it all.
            float share = victim == direct ? 1.0F : (float) (1.0D - 0.5D * Math.min(1.0D, distance / reach));
            victim.invulnerableTime = 0;
            victim.hurt(source(), hit * share);
            Vec3 push = victim.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() < 1.0E-4D) {
                push = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
            }
            victim.knockback(giant ? 1.6D : 0.9D, -push.x, -push.z);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, giant ? 0.5D : 0.3D, 0.0D));
            victim.hurtMarked = true;
        }
        crater(level, at);
        KiFx.burst(level, at, KiFx.FIRE, (float) reach, giant ? 24 : 14, KiFx.BLAST);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, giant ? 6 : 1, giant ? 1.5D : 0.0D, giant ? 1.0D : 0.0D, giant ? 1.5D : 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, giant ? 30 : 10, reach * 0.3D, 0.3D, reach * 0.3D, 0.03D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, giant ? 2.0F : 1.0F, giant ? 0.6F : 1.1F);
        discard();
    }

    /** The blocks the burst takes with it: soft ones round an ordinary ball, stone round the giant one. Not in a village of the owner's. */
    private void crater(ServerLevel level, Vec3 at) {
        double reach = giant ? SuitTuning.CANNON_BREAK_RADIUS * 2.0D : SuitTuning.CANNON_BREAK_RADIUS;
        float maxHardness = giant ? SuitTuning.BHGC_BREAK_HARDNESS : SuitTuning.CANNON_BREAK_HARDNESS;
        net.minecraft.core.BlockPos centre = net.minecraft.core.BlockPos.containing(at);
        if (com.pfkfks.flightsuit.village.Villages.containing(level, centre) != null) {
            return;
        }
        int r = (int) Math.ceil(reach);
        int broken = 0;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (Vec3.atCenterOf(pos).distanceTo(at) > reach) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState block = level.getBlockState(pos);
            float hardness = block.getDestroySpeed(level, pos);
            if (block.isAir() || hardness < 0.0F || hardness > maxHardness || level.getBlockEntity(pos) != null || !block.getFluidState().isEmpty()
                    || getOwner() instanceof ServerPlayer owner && !level.mayInteract(owner, pos)) {
                continue;
            }
            level.destroyBlock(pos, false, this);
            broken++;
        }
        if (broken > 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }
}
