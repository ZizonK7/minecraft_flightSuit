package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.war.RaidMember;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Everyone but the guards and soldiers runs from monsters. */
public class ResidentFleeGoal extends AvoidEntityGoal<Monster> {
    private final ResidentEntity resident;

    public ResidentFleeGoal(ResidentEntity resident) {
        super(resident, Monster.class, entity -> !RaidMember.isNoThreat(entity), 10.0F, 0.7D, 0.95D, entity -> true);
        this.resident = resident;
    }

    @Override
    public boolean canUse() {
        return !resident.getJob().isFighter() && !resident.isDowned() && !resident.isSleeping() && super.canUse();
    }
}
