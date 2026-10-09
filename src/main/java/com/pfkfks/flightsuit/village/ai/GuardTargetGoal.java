package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;

/** Guards and soldiers go for monsters they can see inside their own village (not ones passing by outside it). */
public class GuardTargetGoal extends NearestAttackableTargetGoal<Monster> {
    private final ResidentEntity guard;

    public GuardTargetGoal(ResidentEntity guard) {
        super(guard, Monster.class, 10, true, false, target -> guard.isInOwnVillage(target.blockPosition()));
        this.guard = guard;
    }

    @Override
    public boolean canUse() {
        return guard.getJob().isFighter() && !guard.isDowned() && !guard.isWanderer() && super.canUse();
    }
}
