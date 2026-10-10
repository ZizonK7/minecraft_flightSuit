package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.Construction;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * By day, a worker whose job has a place of its own (after the M13 test: kitchen, smithy, shop, pen, clinic,
 * stage, the architects' workshop) goes there and works - in their own if they have one - instead of
 * wandering: stands at it, turns to the work, a swing and the sound and sparkle of the trade now and then.
 * Jobs with real work (the doctor's patients, the architect's building) do that first; this is for between.
 */
public class WorkplaceGoal extends Goal {
    private static final long DAY_START = 1000L;
    private static final long DAY_END = 11500L;

    private final ResidentEntity worker;
    private @Nullable Construction place;
    private int ticks;

    public WorkplaceGoal(ResidentEntity worker) {
        this.worker = worker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean workHours() {
        long time = worker.level().getDayTime() % 24000L;
        return time >= DAY_START && time < DAY_END;
    }

    private boolean able() {
        return !worker.isDowned() && !worker.isWanderer() && !worker.isBaby() && !worker.isSleeping() && workHours();
    }

    @Override
    public boolean canUse() {
        if (!able() || worker.getRandom().nextInt(reducedTickDelay(40)) != 0) {
            return false;
        }
        VillageHallBlockEntity hall = worker.hall();
        if (hall == null || hall.isAlarm()) {
            return false;
        }
        place = hall.works().workplaceOf(worker);
        return place != null;
    }

    @Override
    public boolean canContinueToUse() {
        VillageHallBlockEntity hall = worker.hall();
        return place != null && able() && hall != null && !hall.isAlarm() && ticks < 20 * 60;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    /** Where to stand: the middle of the building, at floor level. */
    private Vec3 spot() {
        BlockPos center = place.center();
        return new Vec3(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D);
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 spot = spot();
        double distSqr = worker.position().distanceToSqr(spot);
        if (distSqr > 2.5D * 2.5D) {
            if (ticks % 20 == 1) {
                worker.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.6D);
            }
            return;
        }
        worker.getNavigation().stop();
        if (ticks % 80 == 0 && worker.level() instanceof ServerLevel server) {
            work(server);
        }
    }

    /** A moment of the trade: a swing, its sound, a few particles over the hands. */
    private void work(ServerLevel server) {
        ResidentJob job = worker.getJob();
        ParticleOptions particle = switch (job) {
            case BLACKSMITH -> ParticleTypes.LAVA;
            case COOK -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case MUSICIAN -> ParticleTypes.NOTE;
            case MERCHANT -> ParticleTypes.HAPPY_VILLAGER;
            case DOCTOR -> ParticleTypes.HEART;
            case RANCHER -> ParticleTypes.COMPOSTER;
            default -> ParticleTypes.CRIT;
        };
        SoundEvent sound = switch (job) {
            case BLACKSMITH -> SoundEvents.ANVIL_USE;
            case COOK -> SoundEvents.SMOKER_SMOKE;
            case MUSICIAN -> SoundEvents.NOTE_BLOCK_HARP.get();
            case MERCHANT -> SoundEvents.VILLAGER_WORK_CARTOGRAPHER;
            case DOCTOR -> SoundEvents.BREWING_STAND_BREW;
            case RANCHER -> SoundEvents.COW_AMBIENT;
            default -> SoundEvents.VILLAGER_WORK_MASON;
        };
        worker.swing(InteractionHand.MAIN_HAND);
        Vec3 hands = worker.position().add(worker.getLookAngle().scale(0.6D)).add(0.0D, 1.1D, 0.0D);
        server.sendParticles(particle, hands.x, hands.y, hands.z, 3, 0.2D, 0.2D, 0.2D, 0.02D);
        server.playSound(null, worker.blockPosition(), sound, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F,
                0.9F + worker.getRandom().nextFloat() * 0.2F);
        worker.setYRot(worker.getYRot() + worker.getRandom().nextInt(91) - 45);
    }

    @Override
    public void stop() {
        place = null;
        worker.getNavigation().stop();
    }
}
