package com.pfkfks.flightsuit.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Straight-line repulsor flight toward the wanted position (like the Vex's control, not ground pathing):
 * velocity eases toward the target direction, speeds up with distance, and pops upward when bumping into
 * a wall so the suit hops over obstacles instead of grinding against them. Idle = hold position in the air.
 */
public class SuitMoveControl extends MoveControl {
    /** Blocks/tick at speed modifier 1. */
    private static final double BASE_SPEED = 0.35D;

    public SuitMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (!mob.isNoGravity()) {
            // Powered down: let gravity and vanilla physics take over.
            super.tick();
            return;
        }
        if (operation != Operation.MOVE_TO) {
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.75D));
            return;
        }
        Vec3 to = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
        double distance = to.length();
        if (distance < 0.3D) {
            operation = Operation.WAIT;
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5D));
            return;
        }
        double speed = Math.min(BASE_SPEED * speedModifier, distance * 0.5D);
        Vec3 desired = to.scale(speed / distance);
        Vec3 velocity = mob.getDeltaMovement().lerp(desired, 0.25D);
        if (mob.horizontalCollision) {
            velocity = velocity.add(0.0D, 0.2D, 0.0D);
        }
        mob.setDeltaMovement(velocity);
        if (mob.getTarget() == null && to.horizontalDistanceSqr() > 0.25D) {
            float yaw = (float) (Mth.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0F));
            mob.yBodyRot = mob.getYRot();
        }
    }
}
