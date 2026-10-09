package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** The teacher holds class at the school during school hours: stands at the lectern, facing the benches. */
public class TeacherWorkGoal extends Goal {
    private final ResidentEntity teacher;
    private BlockPos school;
    private int ticks;

    public TeacherWorkGoal(ResidentEntity teacher) {
        this.teacher = teacher;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (teacher.getJob() != ResidentJob.TEACHER || teacher.isWanderer() || teacher.isDowned()
                || !ChildSchoolGoal.schoolHours(teacher) || teacher.getRandom().nextInt(reducedTickDelay(40)) != 0) {
            return false;
        }
        VillageHallBlockEntity hall = teacher.hall();
        if (hall != null && hall.isAlarm()) {
            return false;
        }
        school = ChildSchoolGoal.schoolOf(teacher);
        return school != null;
    }

    @Override
    public boolean canContinueToUse() {
        VillageHallBlockEntity hall = teacher.hall();
        return school != null && ChildSchoolGoal.schoolHours(teacher) && !teacher.isDowned() && (hall == null || !hall.isAlarm());
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        Vec3 front = Vec3.atBottomCenterOf(school).add(0.0D, 1.0D, 0.0D);
        if (ticks++ % 20 == 0 && teacher.position().distanceToSqr(front) > 4.0D) {
            teacher.getNavigation().moveTo(front.x, front.y, front.z, 0.6D);
        }
        // Face the class (the nearest child, if any are there).
        for (ResidentEntity pupil : teacher.level().getEntitiesOfClass(ResidentEntity.class, teacher.getBoundingBox().inflate(6.0D),
                ResidentEntity::isBaby)) {
            teacher.getLookControl().setLookAt(pupil);
            break;
        }
    }

    @Override
    public void stop() {
        teacher.getNavigation().stop();
    }
}
