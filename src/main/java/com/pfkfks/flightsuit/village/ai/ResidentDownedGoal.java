package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** A downed resident lies still: holds every movement flag so nothing else runs. */
public class ResidentDownedGoal extends Goal {
    private final ResidentEntity resident;

    public ResidentDownedGoal(ResidentEntity resident) {
        this.resident = resident;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return resident.isDowned();
    }

    @Override
    public void start() {
        resident.getNavigation().stop();
    }
}
