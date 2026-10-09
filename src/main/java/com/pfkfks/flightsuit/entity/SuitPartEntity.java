package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Purely visual: one suit piece flying from its launch point onto its owner's body during a suit-up
 * (DESIGN.md 4-4, Mark 42 style). The server's SuitUpManager equips the real item when the flight ends;
 * this entity just needs to arrive at the same moment and then disappear.
 *
 * The path is a pure function of (owner position, launch offset, age), so both sides compute it each tick
 * instead of relying on position sync - the piece stays glued to a moving owner.
 * Rendered at the owner's feet position with the suit-up pose, so arriving == lining up with the body.
 */
public class SuitPartEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SLOT = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> FLIGHT_TICKS = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SUIT_ID = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.STRING);

    public SuitPartEntity(EntityType<? extends SuitPartEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static SuitPartEntity create(Level level, Player owner, EquipmentSlot slot, String suitId, Vec3 offset, int flightTicks) {
        SuitPartEntity part = new SuitPartEntity(ModEntities.SUIT_PART.get(), level);
        part.entityData.set(OWNER_ID, owner.getId());
        part.entityData.set(SLOT, (byte) slot.getIndex());
        part.entityData.set(FLIGHT_TICKS, flightTicks);
        part.entityData.set(OFFSET_X, (float) offset.x);
        part.entityData.set(OFFSET_Y, (float) offset.y);
        part.entityData.set(OFFSET_Z, (float) offset.z);
        part.entityData.set(SUIT_ID, suitId);
        Vec3 start = owner.position().add(offset);
        part.moveTo(start.x, start.y, start.z, owner.yBodyRot, 0.0F);
        return part;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_ID, -1);
        this.entityData.define(SLOT, (byte) 0);
        this.entityData.define(FLIGHT_TICKS, 10);
        this.entityData.define(OFFSET_X, 0.0F);
        this.entityData.define(OFFSET_Y, 0.0F);
        this.entityData.define(OFFSET_Z, 0.0F);
        this.entityData.define(SUIT_ID, "");
    }

    public Player getOwner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof Player player ? player : null;
    }

    public EquipmentSlot getSlot() {
        return EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, entityData.get(SLOT));
    }

    public String getSuitId() {
        return entityData.get(SUIT_ID);
    }

    public int getFlightTicks() {
        return Math.max(1, entityData.get(FLIGHT_TICKS));
    }

    /** 0 at launch, 1 on arrival. */
    public float getProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / getFlightTicks(), 0.0F, 1.0F);
    }

    /** Ease-in-out so the piece launches lazily, whips across, and settles onto the body. */
    public static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    /** Where the piece sits relative to the owner's feet: the launch offset swirled around the owner and shrunk to zero. */
    public Vec3 offsetAt(float progress) {
        float remaining = 1.0F - ease(progress);
        double swirl = Math.toRadians(remaining * 75.0F);
        double ox = entityData.get(OFFSET_X), oz = entityData.get(OFFSET_Z);
        double rx = ox * Math.cos(swirl) - oz * Math.sin(swirl);
        double rz = ox * Math.sin(swirl) + oz * Math.cos(swirl);
        double arc = Math.sin(Math.PI * progress) * 1.2D;
        return new Vec3(rx * remaining, entityData.get(OFFSET_Y) * remaining + arc * remaining, rz * remaining);
    }

    @Override
    public void tick() {
        super.tick();
        Player owner = getOwner();
        if (owner == null) {
            if (!level().isClientSide && tickCount > 2) {
                discard();
            }
            return;
        }
        float progress = getProgress(0.0F);
        Vec3 pos = owner.position().add(offsetAt(progress));
        setPos(pos.x, pos.y, pos.z);

        if (level().isClientSide && progress < 1.0F) {
            // Thruster trail: each piece flies itself in, like the Mark 42 pieces.
            Vec3 bodyPoint = pos.add(0, pieceHeight(), 0);
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, bodyPoint.x, bodyPoint.y, bodyPoint.z, 0, 0, 0);
            if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, bodyPoint.x, bodyPoint.y, bodyPoint.z, 0, 0.01, 0);
            }
        }
        if (!level().isClientSide && tickCount > getFlightTicks() + 1) {
            discard();
        }
    }

    /** Rough height of the piece above the owner's feet, for effects. */
    public double pieceHeight() {
        return switch (getSlot()) {
            case HEAD -> 1.6D;
            case CHEST -> 1.1D;
            case LEGS -> 0.6D;
            default -> 0.15D;
        };
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
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
