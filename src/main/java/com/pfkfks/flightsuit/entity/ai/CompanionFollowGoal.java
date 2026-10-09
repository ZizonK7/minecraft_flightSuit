package com.pfkfks.flightsuit.entity.ai;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Stands by at the owner's side and stays put, looking where the owner looks. Only when the owner has
 * moved more than {@link #LEAVE_DISTANCE} away does it fly back to their right side (faster the further
 * it is), then settles again. Left far behind, it streaks back in flight (teleporting only across huge gaps).
 * While the owner is remote-piloting another suit, it stands guard by the body they left instead.
 */
public class CompanionFollowGoal extends Goal {
    /** Beyond this it streaks back in flight (rather than flying in normally). */
    private static final double ARRIVAL_DISTANCE = 48.0D;
    /** Only absurd gaps (other side of the map) still teleport. */
    private static final double TELEPORT_DISTANCE = 160.0D;
    private static final double LEAVE_DISTANCE = 6.0D;

    private final SuitCompanionEntity suit;
    private boolean repositioning;

    public CompanionFollowGoal(SuitCompanionEntity suit) {
        this.suit = suit;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player owner = suit.getOwner();
        return owner != null && owner.isAlive() && !owner.isSpectator() && !suit.isBusy() && suit.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity owner = suit.getAnchor();
        if (owner == null) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        // Right beside the owner: standing on the ground with them, or level with them in the air.
        Vec3 spot = owner.position().add(right.scale(1.5D));

        double ownerDistance = suit.distanceTo(owner);
        if (ownerDistance > TELEPORT_DISTANCE) {
            suit.moveTo(spot.x, spot.y, spot.z, owner.getYRot(), 0.0F);
            suit.setDeltaMovement(Vec3.ZERO);
            repositioning = false;
            return;
        }
        if (ownerDistance > ARRIVAL_DISTANCE) {
            suit.startArrival();
            repositioning = false;
            return;
        }
        if (!repositioning && ownerDistance > LEAVE_DISTANCE) {
            repositioning = true;
        }
        if (repositioning) {
            double distance = suit.position().distanceTo(spot);
            if (distance < 0.6D) {
                repositioning = false;
            } else {
                suit.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, Math.max(1.0D, Math.min(5.0D, distance / 3.0D)));
            }
        }
        Vec3 gaze = owner.getEyePosition().add(owner.getLookAngle().scale(12.0D));
        suit.getLookControl().setLookAt(gaze.x, gaze.y, gaze.z);
    }
}
