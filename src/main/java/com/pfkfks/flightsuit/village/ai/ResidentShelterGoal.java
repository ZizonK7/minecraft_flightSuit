package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * While the village alarm is up (DESIGN.md 4-12: 경보 종 → 대피), everyone but the fighters goes home - to
 * their bed, or the hall if they have none - and stays there until it clears.
 */
public class ResidentShelterGoal extends Goal {
    private final ResidentEntity resident;
    private BlockPos shelter;
    private int ticks;

    public ResidentShelterGoal(ResidentEntity resident) {
        this.resident = resident;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (resident.getJob().isFighter() || resident.isDowned()) {
            return false;
        }
        VillageHallBlockEntity hall = resident.hall();
        if (hall == null || !hall.isAlarm()) {
            return false;
        }
        shelter = resident.getHomeBed() != null ? resident.getHomeBed() : hall.getBlockPos();
        return true;
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
        Vec3 target = Vec3.atBottomCenterOf(shelter);
        if (resident.position().distanceToSqr(target) > 4.0D) {
            resident.getNavigation().moveTo(target.x, target.y, target.z, 0.9D);
        } else {
            resident.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        resident.getNavigation().stop();
    }
}
