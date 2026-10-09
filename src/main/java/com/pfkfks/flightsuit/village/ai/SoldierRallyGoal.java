package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * When the alarm goes up, soldiers run to where the enemy was reported (and their target goals take it from
 * there). Until campaigns exist (M12), this is the army's job at home.
 */
public class SoldierRallyGoal extends Goal {
    private final ResidentEntity soldier;
    private int ticks;

    public SoldierRallyGoal(ResidentEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private BlockPos rallyPoint() {
        VillageHallBlockEntity hall = soldier.hall();
        return hall != null && hall.isAlarm() ? hall.alarmPos() : null;
    }

    @Override
    public boolean canUse() {
        if (soldier.getJob() != ResidentJob.SOLDIER || soldier.isDowned() || soldier.getTarget() != null) {
            return false;
        }
        BlockPos point = rallyPoint();
        return point != null && soldier.position().distanceToSqr(Vec3.atBottomCenterOf(point)) > 36.0D;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos point = rallyPoint();
        return point != null && soldier.getTarget() == null && !soldier.isDowned()
                && soldier.position().distanceToSqr(Vec3.atBottomCenterOf(point)) > 16.0D;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (ticks++ % 20 == 0) {
            BlockPos point = rallyPoint();
            if (point != null) {
                soldier.getNavigation().moveTo(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D, 1.0D);
            }
        }
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
    }
}
