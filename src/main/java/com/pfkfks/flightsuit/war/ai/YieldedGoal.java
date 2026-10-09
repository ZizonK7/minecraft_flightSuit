package com.pfkfks.flightsuit.war.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/** Kneeling after a surrender (or nursing a wound): stays put and does nothing else. */
public class YieldedGoal extends Goal {
    private final Mob mob;
    private final BooleanSupplier down;

    public YieldedGoal(Mob mob, BooleanSupplier down) {
        this.mob = mob;
        this.down = down;
        // No JUMP: it would share it with FloatGoal at the same priority and wait for it to finish.
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return down.getAsBoolean();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        mob.setTarget(null);
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
    }
}
