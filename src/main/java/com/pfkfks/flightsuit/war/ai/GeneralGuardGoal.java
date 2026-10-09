package com.pfkfks.flightsuit.war.ai;

import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.GeneralEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A recruited general keeps the village like a soldier: runs to where the alarm was raised, and otherwise
 * stays near the hall.
 */
public class GeneralGuardGoal extends Goal {
    private final GeneralEntity general;
    private BlockPos point;
    private int ticks;

    public GeneralGuardGoal(GeneralEntity general) {
        this.general = general;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private BlockPos pick() {
        if (!general.isRecruited() || !general.canFight() || general.getTarget() != null) {
            return null;
        }
        VillageHallBlockEntity hall = Villages.hallAt(general.level(), general.getHallPos());
        if (hall == null) {
            return null;
        }
        BlockPos spot = hall.isAlarm() ? hall.alarmPos() : hall.getBlockPos();
        double reach = hall.isAlarm() ? 25.0D : 400.0D;
        return general.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) > reach ? spot : null;
    }

    @Override
    public boolean canUse() {
        point = pick();
        return point != null;
    }

    @Override
    public boolean canContinueToUse() {
        return general.getTarget() == null && general.canFight() && pick() != null;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (ticks++ % 20 == 0) {
            BlockPos spot = pick();
            if (spot != null) {
                general.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.1D);
            }
        }
    }

    @Override
    public void stop() {
        general.getNavigation().stop();
    }
}
