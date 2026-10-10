package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An invisible seat on a block (a throne): whoever rides it sits facing the way the seat faces. Never saved,
 * and gone as soon as nobody sits on it - the one sitting puts it back when they sit down again.
 */
public class SeatEntity extends Entity {
    private int empty;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    /** A seat whose top is {@code surface} (e.g. y + 0.5 for stairs), facing {@code yaw}. */
    public static SeatEntity at(ServerLevel level, Vec3 surface, float yaw) {
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.moveTo(surface.x, surface.y, surface.z, yaw, 0.0F);
        seat.setYHeadRot(yaw);
        return seat;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            empty = isVehicle() ? 0 : empty + 1;
            if (empty > 5) {
                discard();
            }
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        super.positionRider(passenger, move);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger);
    }

    /** Like a boat: the body faces the seat's way, the head turns at most a little past the shoulders. */
    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float turn = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(turn, -100.0F, 100.0F);
        passenger.yRotO += clamped - turn;
        passenger.setYRot(passenger.getYRot() + clamped - turn);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /** Getting up: a step forward off the seat, standing on the block in front (not inside the seat's back). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 front = Vec3.directionFromRotation(0.0F, getYRot());
        return new Vec3(getX() + front.x, Math.floor(getY()), getZ() + front.z);
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
