package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.Blueprint;
import com.pfkfks.flightsuit.village.Construction;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Children spend school hours (morning to mid-afternoon) at the village school, if there is one - milling
 * around the benches. The lesson itself is counted by the hall each morning (VillageHallBlockEntity.schoolDay).
 */
public class ChildSchoolGoal extends Goal {
    private final ResidentEntity child;
    private BlockPos school;
    private int ticks;

    public ChildSchoolGoal(ResidentEntity child) {
        this.child = child;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    static boolean schoolHours(ResidentEntity resident) {
        long time = resident.level().getDayTime() % 24000L;
        return time >= 1500L && time < 9000L;
    }

    static BlockPos schoolOf(ResidentEntity resident) {
        VillageHallBlockEntity hall = resident.hall();
        if (hall == null) {
            return null;
        }
        BlockPos best = null;
        for (Construction building : hall.works().buildings()) {
            if (building.blueprint().isSchool()
                    && (best == null || building.center().distSqr(resident.blockPosition()) < best.distSqr(resident.blockPosition()))) {
                best = building.center();
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (!child.isBaby() || child.isDowned() || !schoolHours(child) || child.getRandom().nextInt(reducedTickDelay(40)) != 0) {
            return false;
        }
        VillageHallBlockEntity hall = child.hall();
        if (hall != null && hall.isAlarm()) {
            return false;
        }
        school = schoolOf(child);
        return school != null && child.position().distanceToSqr(Vec3.atBottomCenterOf(school)) > 9.0D;
    }

    @Override
    public boolean canContinueToUse() {
        VillageHallBlockEntity hall = child.hall();
        return school != null && schoolHours(child) && !child.isDowned() && (hall == null || !hall.isAlarm())
                && child.position().distanceToSqr(Vec3.atBottomCenterOf(school)) > 4.0D;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (ticks++ % 20 == 0) {
            int dx = child.getRandom().nextInt(5) - 2;
            int dz = child.getRandom().nextInt(5) - 2;
            child.getNavigation().moveTo(school.getX() + 0.5D + dx, school.getY() + 1, school.getZ() + 0.5D + dz, 0.7D);
        }
    }

    @Override
    public void stop() {
        child.getNavigation().stop();
    }
}
