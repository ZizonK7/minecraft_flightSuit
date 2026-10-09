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
 * Purely visual suit piece in flight around its owner (DESIGN.md 4-4). Three flavors:
 * - one piece flying in to lock onto the body (ground suit-up, Mark 42 style);
 * - the WHOLE suit diving onto a falling owner (mid-air suit-up, Avengers Mark VII style);
 * - REVERSE: a piece leaving the body and flying off toward the station;
 * - CLAMP: station assembly - the piece starts split open right next to the body and presses straight on.
 * The server equips / stores the real items; this entity only has to look right and then disappear.
 *
 * The path is a pure function of (owner position, launch offset, age), so both sides compute it each tick
 * instead of relying on position sync - the piece stays glued to a moving (or falling) owner.
 * Rendered as if worn by a phantom owner standing at the entity's position, so "arriving" == lining up.
 */
public class SuitPartEntity extends Entity {
    /** Slot index used for the whole-suit variant (armor slot indices are 0-3). */
    public static final byte WHOLE = 4;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SLOT = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> FLIGHT_TICKS = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SUIT_ID = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> REVERSE = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLAMP = SynchedEntityData.defineId(SuitPartEntity.class, EntityDataSerializers.BOOLEAN);

    public SuitPartEntity(EntityType<? extends SuitPartEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static SuitPartEntity create(Level level, Player owner, EquipmentSlot slot, String suitId, Vec3 offset, int flightTicks) {
        return create(level, owner, (byte) slot.getIndex(), suitId, offset, flightTicks, false);
    }

    public static SuitPartEntity createWhole(Level level, Player owner, String suitId, Vec3 offset, int flightTicks) {
        return create(level, owner, WHOLE, suitId, offset, flightTicks, false);
    }

    public static SuitPartEntity createLeaving(Level level, Player owner, EquipmentSlot slot, String suitId, Vec3 offset, int flightTicks) {
        return create(level, owner, (byte) slot.getIndex(), suitId, offset, flightTicks, true);
    }

    /** Station assembly: the piece starts just off the body (split open) and clamps straight on - no flight. */
    public static SuitPartEntity createClamp(Level level, Player owner, EquipmentSlot slot, String suitId, Vec3 offset, int flightTicks) {
        SuitPartEntity part = create(level, owner, (byte) slot.getIndex(), suitId, offset, flightTicks, false);
        part.entityData.set(CLAMP, true);
        return part;
    }

    private static SuitPartEntity create(Level level, Player owner, byte slot, String suitId, Vec3 offset, int flightTicks, boolean reverse) {
        SuitPartEntity part = new SuitPartEntity(ModEntities.SUIT_PART.get(), level);
        part.entityData.set(OWNER_ID, owner.getId());
        part.entityData.set(SLOT, slot);
        part.entityData.set(FLIGHT_TICKS, flightTicks);
        part.entityData.set(OFFSET_X, (float) offset.x);
        part.entityData.set(OFFSET_Y, (float) offset.y);
        part.entityData.set(OFFSET_Z, (float) offset.z);
        part.entityData.set(SUIT_ID, suitId);
        part.entityData.set(REVERSE, reverse);
        Vec3 start = reverse ? owner.position() : owner.position().add(offset);
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
        this.entityData.define(REVERSE, false);
        this.entityData.define(CLAMP, false);
    }

    public boolean isClamp() {
        return entityData.get(CLAMP);
    }

    public Player getOwner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof Player player ? player : null;
    }

    public boolean isWhole() {
        return entityData.get(SLOT) == WHOLE;
    }

    public boolean isLeaving() {
        return entityData.get(REVERSE);
    }

    /** The single armor slot this piece is; HEAD for the whole-suit variant (callers check {@link #isWhole()}). */
    public EquipmentSlot getSlot() {
        byte slot = entityData.get(SLOT);
        return slot == WHOLE ? EquipmentSlot.HEAD : EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, slot);
    }

    public String getSuitId() {
        return entityData.get(SUIT_ID);
    }

    public int getFlightTicks() {
        return Math.max(1, entityData.get(FLIGHT_TICKS));
    }

    /** 0 at launch, 1 on arrival (or, leaving, on reaching the far point). */
    public float getProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / getFlightTicks(), 0.0F, 1.0F);
    }

    /** Ease-in-out so the piece launches lazily, whips across, and settles onto the body. */
    public static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    /** How far along the launch offset the piece is: 1 = at the launch point, 0 = on the body. */
    public float distanceFactor(float progress) {
        if (isLeaving()) {
            return progress * progress;
        }
        if (isClamp()) {
            // Slides in slowly, then snaps shut at the end - a machine press, not a throw.
            return 1.0F - progress * progress * progress;
        }
        return 1.0F - ease(progress);
    }

    /** Where the piece sits relative to the owner's feet. */
    public Vec3 offsetAt(float progress) {
        float factor = distanceFactor(progress);
        double ox = entityData.get(OFFSET_X), oy = entityData.get(OFFSET_Y), oz = entityData.get(OFFSET_Z);
        if (isWhole() || isClamp()) {
            // Straight dive along the launch line - the suit is aimed, not tumbling.
            return new Vec3(ox * factor, oy * factor, oz * factor);
        }
        double swirl = Math.toRadians(factor * 75.0F);
        double rx = ox * Math.cos(swirl) - oz * Math.sin(swirl);
        double rz = ox * Math.sin(swirl) + oz * Math.cos(swirl);
        double arc = Math.sin(Math.PI * progress) * 1.2D;
        return new Vec3(rx * factor, oy * factor + arc * factor, rz * factor);
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

        if (level().isClientSide && progress < 1.0F && isClamp()) {
            // Welding sparks as the assembly rig presses the piece on.
            if (progress > 0.6F) {
                Vec3 seam = pos.add(0, pieceHeight(), 0);
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, seam.x + (random.nextDouble() - 0.5D) * 0.6D,
                        seam.y + (random.nextDouble() - 0.5D) * 0.4D, seam.z + (random.nextDouble() - 0.5D) * 0.6D,
                        (random.nextDouble() - 0.5D) * 0.2D, 0.05D, (random.nextDouble() - 0.5D) * 0.2D);
            }
        } else if (level().isClientSide && progress < 1.0F) {
            // Thruster trail: every piece flies itself, like the Mark 42 pieces.
            Vec3 bodyPoint = pos.add(0, pieceHeight(), 0);
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, bodyPoint.x, bodyPoint.y, bodyPoint.z, 0, 0, 0);
            if (isWhole()) {
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.1D, pos.z, 0, 0.15D, 0);
                level().addParticle(ParticleTypes.CLOUD, bodyPoint.x, bodyPoint.y, bodyPoint.z, 0, 0.05D, 0);
            } else if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, bodyPoint.x, bodyPoint.y, bodyPoint.z, 0, 0.01, 0);
            }
        }
        if (!level().isClientSide && tickCount > getFlightTicks() + 1) {
            discard();
        }
    }

    /** Rough height of the piece above the owner's feet, for effects. */
    public double pieceHeight() {
        if (isWhole()) {
            return 1.0D;
        }
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
