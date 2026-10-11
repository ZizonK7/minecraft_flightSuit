package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.block.GeneratorBlockEntity;
import com.pfkfks.flightsuit.block.PowerPlantBlockEntity;
import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M18 the engineer's day (DESIGN.md 5차 피드백 "기술자"): by day they keep the village's power plant and generators
 * fed. Whenever one of them is running low they walk to the hall, take a few lumps of coal (any furnace fuel) out
 * of its storage - carried in hand, for everyone to see - and walk them over to the hungry block (one engineer per
 * hungry block: the others leave it to them). With nothing to carry they do their rounds instead - the plant, the
 * generators, the batteries, the panels, the cables, one after another, a look and a spanner's clank at each - so
 * a crew of them spreads over the works rather than crowding one plant (the first M18 test). The hall's board gets
 * a line about the day's coal each morning (VillageHallBlockEntity.morning counts the lumps noted here).
 */
public class EngineerWorkGoal extends Goal {
    private static final long DAY_START = 1000L;
    private static final long DAY_END = 11500L;
    /** A block with fewer lumps than this gets a delivery. */
    private static final int LOW_FUEL = 6;
    private static final int CARRY = 8;
    /** Who is seeing to which block right now (a delivery under way, or a stop on the rounds), so they spread out. */
    private static final Map<BlockPos, UUID> CLAIMS = new HashMap<>();

    private final ResidentEntity engineer;
    private @Nullable BlockPos target;
    private boolean fetching;
    private int ticks;
    /** How long this stop on the rounds lasts once they're there. */
    private int stay;
    private int atTicks;
    private @Nullable BlockPos lastStop;

    public EngineerWorkGoal(ResidentEntity engineer) {
        this.engineer = engineer;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean workHours() {
        long time = engineer.level().getDayTime() % 24000L;
        return time >= DAY_START && time < DAY_END;
    }

    private boolean able() {
        return engineer.getJob() == ResidentJob.ENGINEER && !engineer.isDowned() && !engineer.isWanderer() && !engineer.isBaby()
                && !engineer.isSleeping() && workHours();
    }

    /** The village's power blocks that burn fuel, nearest first. */
    private List<BlockEntity> burners(VillageHallBlockEntity hall) {
        List<BlockEntity> out = new ArrayList<>();
        BlockPos centre = hall.getBlockPos();
        for (PowerGrid.Node node : PowerGrid.nodesWithin(engineer.level(), centre, VillageTuning.RADIUS, PowerGrid.Role.PRODUCER)) {
            collect(out, node.self(), centre);
        }
        out.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(engineer.blockPosition())));
        return out;
    }

    /** Everything on the village's grid - the stops of the rounds. */
    private List<BlockPos> works(VillageHallBlockEntity hall) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos centre = hall.getBlockPos();
        for (PowerGrid.Role role : PowerGrid.Role.values()) {
            for (PowerGrid.Node node : PowerGrid.nodesWithin(engineer.level(), centre, VillageTuning.RADIUS, role)) {
                BlockPos pos = node.self().getBlockPos();
                if (!out.contains(pos) && pos.closerThan(centre, VillageTuning.RADIUS)) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    /** Any furnace fuel in the hall's storage. */
    private static boolean hasFuel(VillageHallBlockEntity hall) {
        for (int i = 0; i < hall.getStorage().getContainerSize(); i++) {
            if (GeneratorBlockEntity.isFuel(hall.getStorage().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collect(List<BlockEntity> out, BlockEntity be, BlockPos centre) {
        if ((be instanceof PowerPlantBlockEntity || be instanceof GeneratorBlockEntity) && !out.contains(be)
                && be.getBlockPos().closerThan(centre, VillageTuning.RADIUS)) {
            out.add(be);
        }
    }

    private static int fuelIn(BlockEntity be) {
        if (be instanceof PowerPlantBlockEntity plant) {
            return plant.fuelCount();
        }
        return be instanceof GeneratorBlockEntity generator ? generator.getFuel().getCount() : 0;
    }

    /** Is someone else already on this block? */
    private boolean claimed(BlockPos pos) {
        UUID who = CLAIMS.get(pos);
        return who != null && !who.equals(engineer.getUUID());
    }

    private void claim(BlockPos pos) {
        release();
        CLAIMS.put(pos, engineer.getUUID());
    }

    private void release() {
        CLAIMS.values().removeIf(id -> id.equals(engineer.getUUID()));
    }

    @Override
    public boolean canUse() {
        if (!able() || engineer.getRandom().nextInt(reducedTickDelay(40)) != 0) {
            return false;
        }
        VillageHallBlockEntity hall = engineer.hall();
        if (hall == null || hall.isAlarm()) {
            return false;
        }
        // A hungry one first (coal to give it, nobody else on it), else a stop on the rounds.
        if (hasFuel(hall)) {
            for (BlockEntity be : burners(hall)) {
                if (fuelIn(be) < LOW_FUEL && !claimed(be.getBlockPos())) {
                    target = be.getBlockPos();
                    claim(target);
                    fetching = true;
                    ticks = 0;
                    return true;
                }
            }
        }
        List<BlockPos> stops = works(hall);
        stops.removeIf(pos -> pos.equals(lastStop) || claimed(pos));
        if (stops.isEmpty()) {
            return false;
        }
        target = stops.get(engineer.getRandom().nextInt(stops.size()));
        claim(target);
        fetching = false;
        ticks = 0;
        atTicks = 0;
        stay = 60 + engineer.getRandom().nextInt(80);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        VillageHallBlockEntity hall = engineer.hall();
        return target != null && able() && hall != null && !hall.isAlarm() && ticks < 20 * 90 && stillThere(target);
    }

    /** The block they're heading for is still a power block (someone may have broken it meanwhile). */
    private boolean stillThere(BlockPos pos) {
        return engineer.level().getBlockEntity(pos) != null
                || engineer.level().getBlockState(pos).getBlock() instanceof com.pfkfks.flightsuit.block.CableBlock;
    }

    private Vec3 beside(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    @Override
    public void tick() {
        ticks++;
        VillageHallBlockEntity hall = engineer.hall();
        if (hall == null || target == null) {
            return;
        }
        if (fetching) {
            Vec3 spot = beside(hall.getBlockPos());
            if (engineer.position().distanceToSqr(spot) > 3.0D * 3.0D) {
                if (ticks % 20 == 1) {
                    engineer.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.7D);
                }
                return;
            }
            // At the hall: coal out of the store and into the hand.
            ItemStack first = hall.takeAny(GeneratorBlockEntity::isFuel);
            if (first == null) {
                target = null;
                return;
            }
            ItemStack carried = first;
            for (int i = 1; i < CARRY; i++) {
                ItemStack lump = hall.takeAny(stack -> ItemStack.isSameItem(stack, first));
                if (lump == null) {
                    break;
                }
                carried.grow(1);
            }
            engineer.setItemSlot(EquipmentSlot.MAINHAND, carried);
            engineer.swing(InteractionHand.MAIN_HAND);
            engineer.level().playSound(null, engineer.blockPosition(), SoundEvents.CHEST_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.2F);
            fetching = false;
            return;
        }
        Vec3 spot = beside(target);
        ItemStack hand = engineer.getMainHandItem();
        boolean delivering = GeneratorBlockEntity.isFuel(hand);
        if (engineer.position().distanceToSqr(spot) > 2.6D * 2.6D) {
            if (ticks % 20 == 1) {
                engineer.getNavigation().moveTo(spot.x, spot.y, spot.z, delivering ? 0.75D : 0.6D);
            }
            return;
        }
        engineer.getNavigation().stop();
        engineer.getLookControl().setLookAt(Vec3.atCenterOf(target));
        BlockEntity be = engineer.level().getBlockEntity(target);
        if (delivering) {
            int before = hand.getCount();
            if (be instanceof PowerPlantBlockEntity plant) {
                plant.insertFuel(hand);
            } else if (be instanceof GeneratorBlockEntity generator) {
                int taken = generator.insertFuel(hand);
                hand.shrink(taken);
            }
            int given = before - hand.getCount();
            if (given > 0) {
                noteCoal(hall, given);
                engineer.swing(InteractionHand.MAIN_HAND);
                engineer.level().playSound(null, target, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 0.8F);
                engineer.setLastWork(Component.translatable("activity.flightsuit.engineer_fed", given));
            }
            if (!hand.isEmpty()) {
                // The rest goes back to the store.
                ItemStack left = hall.store(hand.copy());
                hand.setCount(left.getCount());
            }
            engineer.setItemSlot(EquipmentSlot.MAINHAND, engineer.getJob().tool());
            ticks = Math.max(ticks, 20 * 60);
            return;
        }
        // A stop on the rounds: a look, one clank of the spanner, then on to the next.
        atTicks++;
        if (atTicks == 20 || atTicks == stay - 15) {
            engineer.swing(InteractionHand.MAIN_HAND);
            if (engineer.level() instanceof ServerLevel server) {
                Vec3 hands = engineer.position().add(engineer.getLookAngle().scale(0.6D)).add(0.0D, 1.1D, 0.0D);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, hands.x, hands.y, hands.z, 2, 0.2D, 0.2D, 0.2D, 0.05D);
                server.playSound(null, engineer.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.15F, 1.7F);
            }
            if (atTicks == 20 && engineer.getRandom().nextInt(4) == 0) {
                engineer.setLastWork(Component.translatable("activity.flightsuit.engineer_tinkered"));
            }
        }
        if (atTicks >= stay) {
            lastStop = target;
            target = null;
        }
    }

    /** Counts the day's coal on the engineer (the hall reads it off every morning). */
    private void noteCoal(VillageHallBlockEntity hall, int lumps) {
        engineer.getPersistentData().putInt("FlightSuitCoalToday", engineer.getPersistentData().getInt("FlightSuitCoalToday") + lumps);
        hall.setChanged();
    }

    @Override
    public void stop() {
        if (target != null) {
            lastStop = target;
        }
        target = null;
        fetching = false;
        release();
        engineer.getNavigation().stop();
        // Never left holding the coal.
        if (GeneratorBlockEntity.isFuel(engineer.getMainHandItem())) {
            VillageHallBlockEntity hall = engineer.hall();
            if (hall != null) {
                hall.store(engineer.getMainHandItem().copy());
            }
            engineer.setItemSlot(EquipmentSlot.MAINHAND, engineer.getJob().tool());
        }
    }
}
