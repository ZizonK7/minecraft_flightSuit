package com.pfkfks.flightsuit.cutscene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The cutscene camera (M17): an invisible point the client looks through while a scene plays (client/CutsceneClient
 * sets it as the camera entity and moves it every frame along the script's camera path). Never added to a world,
 * never saved, never drawn.
 */
public class CameraEntity extends Entity {
    public CameraEntity(EntityType<? extends CameraEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Puts it at a point looking along yaw/pitch, with no in-between frame to blend from. */
    public void snap(double x, double y, double z, float yaw, float pitch) {
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        xOld = x;
        yOld = y;
        zOld = z;
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
