package com.pfkfks.flightsuit.entity;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 2026-10-12: the Cannon Shooter's C4 (CannonArts.c4) as a thing you can see - a taped brick with a timer stuck flat
 * on a block face, or riding on a foe (kept at the spot it was stuck, turning with it). It only shows and follows;
 * the fuse, the beeping and the blast are CannonArts'. Drawn by client/C4Renderer, its light blinking faster as
 * the fuse runs down. Never saved.
 */
public class C4Entity extends Entity {
    /** Which way the brick's top faces: yaw (as an entity's) and tilt from straight up (0 up, 90 level, 180 down). */
    private static final EntityDataAccessor<Float> FACE_YAW = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FACE_TILT = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.FLOAT);
    /** The game tick it goes off (for the blinking). */
    private static final EntityDataAccessor<Integer> FUSE_AT = SynchedEntityData.defineId(C4Entity.class, EntityDataSerializers.INT);

    private @Nullable LivingEntity host;
    /** Where on the host it sits, in the host's own frame (turned back by its body yaw at the time). */
    private Vec3 local = Vec3.ZERO;
    private float localYaw;

    public C4Entity(EntityType<? extends C4Entity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Stuck on a block face at {@code at}. */
    public static C4Entity onBlock(ServerLevel level, Vec3 at, Direction face, long fuseAt) {
        C4Entity c4 = new C4Entity(com.pfkfks.flightsuit.registry.ModEntities.C4.get(), level);
        c4.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        c4.setFace(face);
        c4.entityData.set(FUSE_AT, (int) fuseAt);
        level.addFreshEntity(c4);
        return c4;
    }

    /** Stuck on {@code host} where {@code at} is (a point on its side). */
    public static C4Entity onEntity(ServerLevel level, LivingEntity host, Vec3 at, long fuseAt) {
        C4Entity c4 = new C4Entity(com.pfkfks.flightsuit.registry.ModEntities.C4.get(), level);
        c4.host = host;
        Vec3 rel = at.subtract(host.position());
        float body = host.yBodyRot;
        c4.local = rel.yRot(body * Mth.DEG_TO_RAD);
        // Facing straight out from the host's middle.
        Vec3 out = new Vec3(rel.x, 0.0D, rel.z);
        float faceYaw = out.lengthSqr() < 1.0E-4D ? body : (float) (Mth.atan2(-out.x, out.z) * Mth.RAD_TO_DEG);
        c4.localYaw = faceYaw - body;
        c4.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        c4.entityData.set(FACE_YAW, faceYaw);
        c4.entityData.set(FACE_TILT, 90.0F);
        c4.entityData.set(FUSE_AT, (int) fuseAt);
        level.addFreshEntity(c4);
        return c4;
    }

    private void setFace(Direction face) {
        float tilt = face == Direction.UP ? 0.0F : face == Direction.DOWN ? 180.0F : 90.0F;
        float yaw = face.getAxis().isHorizontal() ? face.toYRot() : 0.0F;
        entityData.set(FACE_YAW, yaw);
        entityData.set(FACE_TILT, tilt);
    }

    public float faceYaw() {
        return entityData.get(FACE_YAW);
    }

    public float faceTilt() {
        return entityData.get(FACE_TILT);
    }

    /** Ticks till it goes off, as the client sees it. */
    public long fuseLeft() {
        return entityData.get(FUSE_AT) - level().getGameTime();
    }

    public @Nullable LivingEntity host() {
        return host;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FACE_YAW, 0.0F);
        entityData.define(FACE_TILT, 0.0F);
        entityData.define(FUSE_AT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (fuseLeft() < -40L) {
            // Whatever set it went away without setting it off.
            discard();
            return;
        }
        if (host != null) {
            if (!host.isAlive() || host.level() != level()) {
                // Fell off where it died.
                host = null;
                return;
            }
            float body = host.yBodyRot;
            Vec3 at = host.position().add(local.yRot(-body * Mth.DEG_TO_RAD));
            setPos(at.x, at.y, at.z);
            entityData.set(FACE_YAW, body + localYaw);
        }
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
    public boolean shouldBeSaved() {
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
