package com.pfkfks.flightsuit.cleaner;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Drives home after a run (or when the hopper is nearly full), parks on the dock and empties the hopper. */
public class CleanerReturnGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 1.0D;
    private static final int NAV_RETRY_TICKS = 20;
    private static final int GIVE_UP_TICKS = 20 * 60;

    private final CleanerRobotEntity robot;
    private int retry;
    private int ticks;

    public CleanerReturnGoal(CleanerRobotEntity robot) {
        this.robot = robot;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !robot.isDocked() && robot.getDock() != null && (robot.isReturnRequested() || robot.isHopperNearlyFull());
    }

    @Override
    public boolean canContinueToUse() {
        return !robot.isDocked() && robot.getDock() != null;
    }

    @Override
    public void start() {
        retry = 0;
        ticks = 0;
    }

    @Override
    public void tick() {
        CleanerDockBlockEntity dock = robot.getDock();
        if (dock == null) {
            return;
        }
        Vec3 point = dock.dockPoint();
        ticks++;
        if (robot.distanceToSqr(point) <= ARRIVAL_DISTANCE_SQR || ticks > GIVE_UP_TICKS) {
            // Roll onto the pad, face the same way every time, and unload.
            robot.getNavigation().stop();
            robot.moveTo(point.x, point.y, point.z, 0.0F, 0.0F);
            robot.setDeltaMovement(Vec3.ZERO);
            dock.deposit(robot.getHopper());
            robot.clearReturnRequest();
            robot.setDocked(true);
            return;
        }
        if (--retry <= 0) {
            robot.getNavigation().moveTo(point.x, point.y, point.z, 1.0D);
            retry = NAV_RETRY_TICKS;
        }
    }
}
