package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** A guard (or soldier) closes in on their target with the sword - and the village alarm goes up when they do. */
public class GuardMeleeGoal extends MeleeAttackGoal {
    private final ResidentEntity guard;

    public GuardMeleeGoal(ResidentEntity guard) {
        super(guard, 0.9D, true);
        this.guard = guard;
    }

    private boolean onDuty() {
        return guard.getJob().isFighter() && !guard.isDowned() && !guard.isWanderer();
    }

    @Override
    public boolean canUse() {
        return onDuty() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return onDuty() && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        LivingEntity target = guard.getTarget();
        VillageHallBlockEntity hall = guard.hall();
        // On campaign, far from home: a fight out there is no reason to ring the village bell.
        if (hall != null && target != null && hall.contains(target.blockPosition())) {
            hall.raiseAlarm(target.blockPosition(), target.getName());
        }
    }
}
