package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * 2026-10-12: the phantom's stolen slime skill (StolenSkill.SLIME_CLONES) - copies of the wearer, weaker than them,
 * that pick a fight with whatever the wearer fights (or whatever comes for them) for a while and then go up in a
 * puff. Drawn with the owner's skin and suit (client/PhantomCloneRenderer). Never saved, never dropping anything.
 */
public class PhantomCloneEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PhantomCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private int left;

    public PhantomCloneEntity(EntityType<? extends PhantomCloneEntity> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /** {@code count} clones round {@code owner}, for {@code ticks}, dressed as they are. */
    public static void spawn(ServerLevel level, ServerPlayer owner, int count, int ticks) {
        for (int i = 0; i < count; i++) {
            PhantomCloneEntity clone = new PhantomCloneEntity(com.pfkfks.flightsuit.registry.ModEntities.PHANTOM_CLONE.get(), level);
            clone.entityData.set(OWNER, Optional.of(owner.getUUID()));
            clone.left = ticks;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    clone.setItemSlot(slot, owner.getItemBySlot(slot).copy());
                }
            }
            double angle = owner.getYRot() * Mth.DEG_TO_RAD + Math.PI * 2.0D * i / count;
            Vec3 at = owner.position().add(-Math.sin(angle) * 1.6D, 0.0D, Math.cos(angle) * 1.6D);
            clone.moveTo(at.x, owner.getY(), at.z, owner.getYRot(), 0.0F);
            clone.setYHeadRot(owner.getYRot());
            level.addFreshEntity(clone);
            level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 1.0D, at.z, 12, 0.3D, 0.5D, 0.3D, 0.05D);
        }
        level.playSound(null, owner.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, owner.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, true));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public @Nullable UUID getOwnerId() {
        return entityData.get(OWNER).orElse(null);
    }

    public @Nullable Player getOwner() {
        UUID id = getOwnerId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(getOwnerId());
    }

    private boolean foe(ServerPlayer owner, LivingEntity entity) {
        return entity.isAlive() && entity != this && !(entity instanceof PhantomCloneEntity) && !SuitWeapons.isFriendly(owner, entity)
                && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != level() || --left <= 0) {
            poof();
            return;
        }
        LivingEntity target = getTarget();
        if ((target == null || !target.isAlive()) && tickCount % 10 == 0) {
            // What the owner fights, what fights the owner, else the nearest monster after them.
            LivingEntity pick = owner.getLastHurtMob();
            if (pick == null || !pick.isAlive() || !foe(owner, pick) || pick.distanceTo(this) > 24.0D) {
                pick = owner.getLastHurtByMob();
            }
            if (pick == null || !pick.isAlive() || !foe(owner, pick) || pick.distanceTo(this) > 24.0D) {
                pick = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16.0D),
                                entity -> foe(owner, entity) && SuitWeapons.isHostile(owner, entity))
                        .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            }
            setTarget(pick);
            target = pick;
        }
        if ((target == null || !target.isAlive()) && distanceTo(owner) > 5.0D && tickCount % 10 == 0) {
            getNavigation().moveTo(owner, 1.2D);
        }
    }

    /** Gone in a squelch of slime. */
    public void poof() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.0D, getZ(), 14, 0.3D, 0.5D, 0.3D, 0.05D);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0D, getZ(), 6, 0.2D, 0.4D, 0.2D, 0.02D);
            level.playSound(null, blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && isOwnedBy(player)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        poof();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
