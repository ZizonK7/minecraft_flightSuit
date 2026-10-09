package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** At night residents walk to their own bed and sleep in it. Guards stay up (night patrol). */
public class ResidentSleepGoal extends Goal {
    private static final int GIVE_UP_TICKS = 600;

    private final ResidentEntity resident;
    private BlockPos bed;
    private int ticks;
    private int cooldown;
    private boolean gaveUp;

    public ResidentSleepGoal(ResidentEntity resident) {
        this.resident = resident;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!shouldSleep()) {
            return false;
        }
        bed = resident.getHomeBed();
        BlockState state = resident.level().getBlockState(bed);
        // Not loaded yet, or someone else (a player) is in it.
        return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
    }

    private boolean shouldSleep() {
        if (resident.isWanderer() || resident.getJob() == ResidentJob.GUARD || resident.isDowned()
                || !resident.isNightTime() || resident.getHomeBed() == null || !resident.isBedHead(resident.getHomeBed())) {
            return false;
        }
        VillageHallBlockEntity hall = resident.hall();
        return hall == null || !hall.isAlarm();
    }

    @Override
    public boolean canContinueToUse() {
        return !gaveUp && shouldSleep() && bed.equals(resident.getHomeBed());
    }

    @Override
    public void start() {
        ticks = 0;
        gaveUp = false;
    }

    @Override
    public void tick() {
        if (resident.isSleeping()) {
            return;
        }
        ticks++;
        Vec3 target = Vec3.atBottomCenterOf(bed);
        if (resident.position().distanceToSqr(target) <= 2.5D) {
            resident.getNavigation().stop();
            resident.startSleeping(bed);
        } else if (ticks % 20 == 1) {
            resident.getNavigation().moveTo(target.x, target.y, target.z, 0.6D);
        }
        if (ticks > GIVE_UP_TICKS) {
            gaveUp = true;
        }
    }

    @Override
    public void stop() {
        if (resident.isSleeping()) {
            resident.stopSleeping();
        }
        resident.getNavigation().stop();
        if (gaveUp) {
            // Can't get to the bed (door locked, walled in): try again a bit later.
            cooldown = 1200;
        }
    }
}
