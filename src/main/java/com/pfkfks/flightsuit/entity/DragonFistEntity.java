package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The golden dragon of Trunks' Dragon Fist (SwordArts.dragonFist, M18 feedback): a marker that rides on the puncher
 * for a few seconds; the clients draw a golden Shenron coiling along the path the puncher took (DragonFistRenderer
 * keeps the trail). No physics, no hits of its own - the punch's damage is the suit's. Never saved.
 */
public class DragonFistEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(DragonFistEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(DragonFistEntity.class, EntityDataSerializers.INT);
    /** Drawn this long after the dash ends, dissolving. */
    public static final int FADE_TICKS = 16;

    private int life;

    public DragonFistEntity(EntityType<? extends DragonFistEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Rides {@code owner} for {@code ticks}, then dissolves over FADE_TICKS. */
    public static DragonFistEntity ride(ServerLevel level, LivingEntity owner, int ticks) {
        DragonFistEntity dragon = new DragonFistEntity(ModEntities.DRAGON_FIST.get(), level);
        dragon.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        dragon.entityData.set(OWNER, owner.getId());
        dragon.entityData.set(LIFE, ticks);
        dragon.life = ticks;
        level.addFreshEntity(dragon);
        return dragon;
    }

    /** The puncher (client or server), or null. */
    public @Nullable LivingEntity owner() {
        return level().getEntity(entityData.get(OWNER)) instanceof LivingEntity living ? living : null;
    }

    /** How long the dash lasts (synced once). */
    public int life() {
        return entityData.get(LIFE);
    }

    /** 0 while the dash is on, rising to 1 as it dissolves. */
    public float fade(float partialTick) {
        float over = tickCount + partialTick - life();
        return over <= 0.0F ? 0.0F : Math.min(1.0F, over / FADE_TICKS);
    }

    /** Called by the punch when it ends early (a wall): the dash is over, dissolve from here. */
    public void finishNow() {
        if (tickCount < life()) {
            entityData.set(LIFE, tickCount);
            life = tickCount;
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(LIFE, 40);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        LivingEntity owner = owner();
        if (owner != null && tickCount <= life()) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        if (owner == null || tickCount > life() + FADE_TICKS) {
            discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(40.0D, 12.0D, 40.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192.0D * 192.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
