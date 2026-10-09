package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BaseFireBlock;

import java.util.EnumSet;

/**
 * Guards put out fires in the village (DESIGN.md 4-12 경비병: 화재 진압) when there's nobody to fight: run to
 * the nearest blaze the hall knows of and stamp it out.
 */
public class GuardFirefightGoal extends Goal {
    private final ResidentEntity guard;
    private BlockPos fire;
    private int ticks;

    public GuardFirefightGoal(ResidentEntity guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private BlockPos find() {
        VillageHallBlockEntity hall = guard.hall();
        return hall == null ? null : hall.fireWatch().nearestFire(guard.blockPosition());
    }

    @Override
    public boolean canUse() {
        if (!guard.getJob().isFighter() || guard.isDowned() || guard.isWanderer() || guard.getTarget() != null) {
            return false;
        }
        fire = find();
        return fire != null;
    }

    @Override
    public boolean canContinueToUse() {
        return fire != null && guard.getTarget() == null && !guard.isDowned() && ticks < 400;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        if (fire == null) {
            return;
        }
        if (!(guard.level().getBlockState(fire).getBlock() instanceof BaseFireBlock)) {
            fire = find();
            return;
        }
        if (guard.position().distanceToSqr(fire.getX() + 0.5D, fire.getY(), fire.getZ() + 0.5D) < 6.25D) {
            guard.swing(InteractionHand.MAIN_HAND);
            guard.level().removeBlock(fire, false);
            guard.level().playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.0F);
            fire = find();
        } else if (ticks % 10 == 1) {
            guard.getNavigation().moveTo(fire.getX() + 0.5D, fire.getY(), fire.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
        fire = null;
    }
}
