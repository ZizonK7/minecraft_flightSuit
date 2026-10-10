package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageWorkday;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The musician's evening (after the M16 test, "every job should really work"): at dusk they go to their stage -
 * or the front of the hall - and play for the village until dark. A concert played lifts everyone's mood the
 * next morning (VillageWorkday.concertBonus).
 */
public class MusicianGoal extends Goal {
    private static final long EVENING_START = 11000L;
    private static final long EVENING_END = 13500L;

    private final ResidentEntity musician;
    private BlockPos venue;
    private int ticks;

    public MusicianGoal(ResidentEntity musician) {
        this.musician = musician;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean evening() {
        long time = musician.level().getDayTime() % 24000L;
        return time >= EVENING_START && time < EVENING_END;
    }

    private boolean able() {
        VillageHallBlockEntity hall = musician.hall();
        return musician.getJob() == ResidentJob.MUSICIAN && !musician.isBaby() && !musician.isDowned() && !musician.isWanderer()
                && !musician.isSleeping() && evening() && hall != null && !hall.isAlarm();
    }

    @Override
    public boolean canUse() {
        if (!able() || musician.getRandom().nextInt(reducedTickDelay(20)) != 0) {
            return false;
        }
        venue = VillageWorkday.venue(musician.hall(), musician);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return able();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 spot = Vec3.atBottomCenterOf(venue);
        if (musician.position().distanceToSqr(spot) > 3.0D * 3.0D) {
            if (ticks % 20 == 1) {
                musician.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.7D);
            }
            return;
        }
        musician.getNavigation().stop();
        if (ticks % 30 == 0 && musician.level() instanceof ServerLevel server) {
            VillageWorkday.playNote(server, musician);
            VillageHallBlockEntity hall = musician.hall();
            if (hall != null) {
                hall.noteConcert(musician);
            }
        }
    }

    @Override
    public void stop() {
        musician.getNavigation().stop();
    }
}
