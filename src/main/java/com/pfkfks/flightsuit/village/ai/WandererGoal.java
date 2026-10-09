package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** A wanderer walks up to the hall and waits there; one who was turned away (or waited a day) walks off. */
public class WandererGoal extends Goal {
    private final ResidentEntity wanderer;
    private int ticks;

    public WandererGoal(ResidentEntity wanderer) {
        this.wanderer = wanderer;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!wanderer.isWanderer() || wanderer.isDowned()) {
            return false;
        }
        VillageHallBlockEntity hall = wanderer.hall();
        return wanderer.isLeaving() || hall != null && wanderer.position().distanceToSqr(hall.center()) > 36.0D;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        if (ticks++ % 20 != 0) {
            return;
        }
        VillageHallBlockEntity hall = wanderer.hall();
        if (wanderer.isLeaving()) {
            Vec3 away = hall != null ? DefaultRandomPos.getPosAway(wanderer, 16, 7, hall.center()) : null;
            if (away != null) {
                wanderer.getNavigation().moveTo(away.x, away.y, away.z, 0.6D);
            }
        } else if (hall != null) {
            Vec3 target = hall.center();
            wanderer.getNavigation().moveTo(target.x, target.y, target.z, 0.6D);
        }
    }

    @Override
    public void stop() {
        wanderer.getNavigation().stop();
    }
}
