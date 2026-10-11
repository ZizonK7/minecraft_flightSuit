package com.pfkfks.flightsuit.entity.ai;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import com.pfkfks.flightsuit.war.RaidMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * House Party Protocol (M18 feedback: "suits go out and fight on their own, not just round me"): a suit sent out
 * hunting sweeps the country round where the call went up - a new spot every little while within HUNT_RADIUS of
 * it, scanning for anything hostile within HUNT_SCAN as it goes and going for it. Flyers fly low over the ground;
 * a grounded suit (Mark 4) walks. The hunt ends when its time is up, its power runs low or the owner calls it
 * (SuitCompanionEntity.isHunting); then it follows as ever.
 */
public class CompanionHuntGoal extends Goal {
    public static final double HUNT_RADIUS = 64.0D;
    public static final double HUNT_SCAN = 32.0D;

    private final SuitCompanionEntity suit;
    private Vec3 spot;
    private int ticks;

    public CompanionHuntGoal(SuitCompanionEntity suit) {
        this.suit = suit;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return suit.isHunting() && !suit.isBusy() && suit.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        spot = null;
        ticks = 0;
    }

    @Override
    public void stop() {
        suit.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 10 == 0 && scan()) {
            return;
        }
        Vec3 centre = suit.huntCentre();
        if (centre == null) {
            return;
        }
        if (spot == null || suit.position().distanceTo(spot) < 2.5D || ticks % 200 == 0) {
            spot = pick(centre);
            ticks = 1;
        }
        if (suit.isGrounded()) {
            PathNavigation navigation = suit.getNavigation();
            if (ticks % 10 == 1 || navigation.isDone()) {
                Path path = navigation.createPath(spot.x, spot.y, spot.z, 0);
                if (path != null) {
                    navigation.moveTo(path, 1.1D);
                } else {
                    spot = null;
                }
            }
        } else {
            suit.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.6D);
        }
        Vec3 ahead = spot.subtract(suit.position());
        if (ahead.horizontalDistanceSqr() > 1.0E-3D) {
            suit.getLookControl().setLookAt(spot.x, spot.y + 1.0D, spot.z);
        }
    }

    /** Something hostile within HUNT_SCAN with a line of sight: after it. */
    private boolean scan() {
        Mob closest = null;
        for (Mob mob : suit.level().getEntitiesOfClass(Mob.class, suit.getBoundingBox().inflate(HUNT_SCAN),
                mob -> mob.isAlive() && mob != suit && SuitWeapons.isHostile(suit, mob) && !RaidMember.isNoThreat(mob)
                        && !(mob instanceof SuitCompanionEntity) && suit.hasLineOfSight(mob))) {
            if (closest == null || mob.distanceToSqr(suit) < closest.distanceToSqr(suit)) {
                closest = mob;
            }
        }
        if (closest == null) {
            return false;
        }
        suit.setTarget(closest);
        return true;
    }

    /** A spot within HUNT_RADIUS of the centre, just above the ground (a flyer skims it a few blocks up). */
    private Vec3 pick(Vec3 centre) {
        double angle = suit.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = 12.0D + suit.getRandom().nextDouble() * (HUNT_RADIUS - 12.0D);
        double x = centre.x + Math.cos(angle) * distance;
        double z = centre.z + Math.sin(angle) * distance;
        int y = suit.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        if (!suit.level().isLoaded(new BlockPos((int) Math.floor(x), y, (int) Math.floor(z)))) {
            return suit.position();
        }
        return new Vec3(x, y + (suit.isGrounded() ? 0.0D : 3.0D + suit.getRandom().nextDouble() * 3.0D), z);
    }

    /** For CompanionTargetGoal: the ward a hunting suit guards is itself. */
    public static LivingEntity ward(SuitCompanionEntity suit) {
        return suit.isHunting() ? suit : suit.getAnchor();
    }
}
