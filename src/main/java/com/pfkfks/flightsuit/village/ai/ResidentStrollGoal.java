package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Idle wandering that keeps to the village (a wanderer stays near the hall, waiting for an answer). */
public class ResidentStrollGoal extends WaterAvoidingRandomStrollGoal {
    private final ResidentEntity resident;

    public ResidentStrollGoal(ResidentEntity resident) {
        super(resident, 0.5D);
        this.resident = resident;
    }

    @Override
    public boolean canUse() {
        return !resident.isDowned() && !resident.isSleeping() && super.canUse();
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        VillageHallBlockEntity hall = resident.hall();
        if (hall != null) {
            Vec3 center = hall.center();
            double limit = resident.isWanderer() ? 10.0D : VillageTuning.RADIUS * 0.75D;
            if (resident.position().distanceToSqr(center) > limit * limit) {
                return LandRandomPos.getPosTowards(resident, 12, 7, center);
            }
        }
        return super.getPosition();
    }
}
