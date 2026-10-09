package com.pfkfks.flightsuit.entity.ai;

import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.suit.RepulsorHandler;
import com.pfkfks.flightsuit.suit.SuitTuning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Iron Man style engagement: hold a firing position a few blocks off the target (above it, so the shot
 * isn't blocked by the ground), blast it with the palm repulsor on a cooldown, punch when it closes in.
 */
public class CompanionCombatGoal extends Goal {
    private static final double STANDOFF = 7.0D;
    private static final double FIRE_RANGE = 24.0D;
    private static final int FIRE_COOLDOWN = 20;
    private static final int MELEE_COOLDOWN = 15;
    private static final float COMPANION_REPULSOR_DAMAGE = 6.0F;

    private final SuitCompanionEntity suit;
    private int fireCooldown;
    private int meleeCooldown;

    public CompanionCombatGoal(SuitCompanionEntity suit) {
        this.suit = suit;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = suit.getTarget();
        return target != null && target.isAlive() && !suit.isBusy();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        fireCooldown = 5;
    }

    @Override
    public void tick() {
        LivingEntity target = suit.getTarget();
        if (target == null) {
            return;
        }
        suit.getLookControl().setLookAt(target, 60.0F, 60.0F);
        double distance = suit.distanceTo(target);

        // Firing position: on the line from the target to the suit, STANDOFF away and a bit above.
        Vec3 away = suit.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 spot = target.position().add(new Vec3(away.x, 0.0D, away.z).normalize().scale(STANDOFF)).add(0.0D, 2.5D, 0.0D);
        if (distance > 2.0D) {
            suit.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, distance > 16.0D ? 3.0D : 1.6D);
        }

        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (distance < 2.4D && meleeCooldown <= 0) {
            suit.swing(InteractionHand.MAIN_HAND);
            suit.doHurtTarget(target);
            meleeCooldown = MELEE_COOLDOWN;
            return;
        }
        if (fireCooldown <= 0 && distance < FIRE_RANGE && suit.getSensing().hasLineOfSight(target)
                && suit.drain(SuitTuning.REPULSOR_COST)) {
            Vec3 palm = suit.palmPosition();
            Vec3 aim = target.getBoundingBox().getCenter().subtract(palm).normalize();
            RepulsorHandler.blast((ServerLevel) suit.level(), suit, palm, aim, FIRE_RANGE, COMPANION_REPULSOR_DAMAGE,
                    entity -> entity != suit && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity)
                            && entity != suit.getOwner());
            suit.markAiming();
            fireCooldown = FIRE_COOLDOWN;
        }
    }
}
