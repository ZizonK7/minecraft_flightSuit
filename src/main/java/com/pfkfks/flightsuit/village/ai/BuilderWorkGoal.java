package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import com.pfkfks.flightsuit.village.VillageWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The architect's work (VillageWorks.nextTask): put back what fights broke, then take down and put up the
 * buildings the player ordered (the hall's own building included) - as one of a crew of up to four, each on
 * their own quarter. Walks within reach (a long one - think scaffolding), holds up what goes in and sets
 * it, paid from the village storage; one step after another until there is nothing left it can do (or the
 * rest is waiting for materials). A more talented architect works faster.
 */
public class BuilderWorkGoal extends Goal {
    private static final double REACH_SQR = 36.0D;
    private static final int GIVE_UP_TICKS = 400;

    private final ResidentEntity builder;
    private @Nullable VillageWorks.Task task;
    private int ticks;
    private int placeDelay;
    private int cooldown;

    public BuilderWorkGoal(ResidentEntity builder) {
        this.builder = builder;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (builder.getJob() != ResidentJob.ARCHITECT || builder.isDowned() || builder.isWanderer() || builder.isNightTime()) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 40;
        VillageHallBlockEntity hall = builder.hall();
        if (hall == null) {
            return false;
        }
        task = hall.works().nextTask(builder.position(), builder.getUUID());
        if (task == null && standsInTheWay(hall)) {
            climbOut(hall);
        }
        return task != null;
    }

    @Override
    public boolean canContinueToUse() {
        return task != null && ticks < GIVE_UP_TICKS && builder.getJob() == ResidentJob.ARCHITECT && !builder.isDowned();
    }

    @Override
    public void start() {
        ticks = 0;
        placeDelay = interval();
        holdBlock();
    }

    private int interval() {
        float speed = VillageTuning.talentSpeed(builder.talent(ResidentJob.ARCHITECT));
        return Math.max(4, Math.round(VillageTuning.REPAIR_INTERVAL / speed));
    }

    @Override
    public void tick() {
        VillageHallBlockEntity hall = builder.hall();
        if (hall == null || task == null) {
            task = null;
            return;
        }
        ticks++;
        BlockPos pos = task.pos();
        Vec3 spot = Vec3.atCenterOf(pos);
        builder.getLookControl().setLookAt(spot.x, spot.y, spot.z);
        if (builder.getEyePosition().distanceToSqr(spot) > REACH_SQR) {
            if (ticks % 20 == 1) {
                builder.getNavigation().moveTo(spot.x, pos.getY(), spot.z, 0.6D);
            }
            return;
        }
        builder.getNavigation().stop();
        if (--placeDelay > 0) {
            return;
        }
        placeDelay = interval();
        builder.swing(InteractionHand.MAIN_HAND);
        if (!hall.works().doTask(task)) {
            hall.works().giveUp(task);
        }
        task = hall.works().nextTask(builder.position(), builder.getUUID());
        ticks = 0;
        holdBlock();
    }

    /** Hold up the block about to go in. */
    private void holdBlock() {
        builder.setItemSlot(EquipmentSlot.MAINHAND, task != null ? task.held().copy() : ItemStack.EMPTY);
    }

    /** Filling a crater (or raising walls) from inside, the builder ends up standing where blocks still go. */
    private boolean standsInTheWay(VillageHallBlockEntity hall) {
        BlockPos feet = builder.blockPosition();
        return hall.works().isPendingSpot(feet) || hall.works().isPendingSpot(feet.above());
    }

    /** Hop up onto the ground next to it (a cheat, but better than being walled in for good). */
    private void climbOut(VillageHallBlockEntity hall) {
        BlockPos feet = builder.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            int top = builder.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, side.getX(), side.getZ());
            BlockPos ground = new BlockPos(side.getX(), top, side.getZ());
            if (!hall.works().isPendingSpot(ground) && !hall.works().isPendingSpot(ground.above())) {
                builder.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
                return;
            }
        }
    }

    @Override
    public void stop() {
        VillageHallBlockEntity hall = builder.hall();
        if (hall != null && task != null && ticks >= GIVE_UP_TICKS) {
            // Couldn't get there: let the others go first.
            hall.works().giveUp(task);
        }
        task = null;
        builder.getNavigation().stop();
        builder.setItemSlot(EquipmentSlot.MAINHAND, builder.getJob().tool());
    }
}
