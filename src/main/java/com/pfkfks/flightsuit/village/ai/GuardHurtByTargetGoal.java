package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enemy;

/** A guard or soldier hits back at whatever monster hit them (a player bumping into them is not a fight). */
public class GuardHurtByTargetGoal extends HurtByTargetGoal {
    private final ResidentEntity guard;

    public GuardHurtByTargetGoal(ResidentEntity guard) {
        super(guard);
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        return guard.getJob().isFighter() && !guard.isDowned() && guard.getLastHurtByMob() instanceof Enemy
                && super.canUse();
    }
}
