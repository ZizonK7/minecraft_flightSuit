package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.Blueprint;
import com.pfkfks.flightsuit.village.Construction;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The farmer's day: harvest ripe crops on farmland inside the village, plant again right away, and carry
 * the harvest to the village storage (the residents' meals) when the bag fills up or the day ends. Seeds come
 * from the harvest or the storage - or, with none anywhere, from pulling grass (and if there is no grass
 * either, the board says the farmers need seeds).
 */
public class FarmerWorkGoal extends Goal {
    private static final int REACH = 12;
    private static final int GIVE_UP_TICKS = 600;

    private final ResidentEntity farmer;
    private @Nullable BlockPos target;
    private @Nullable BlockPos lastField;
    private boolean depositing;
    private int ticks;
    private int cooldown;
    private int workTicks;
    private boolean bareWithoutSeed;

    public FarmerWorkGoal(ResidentEntity farmer) {
        this.farmer = farmer;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (farmer.getJob() != ResidentJob.FARMER || farmer.isDowned() || farmer.isWanderer()) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 40;
        VillageHallBlockEntity hall = farmer.hall();
        if (hall == null) {
            return false;
        }
        boolean night = farmer.isNightTime();
        if (bagFull() || night && !bagEmpty()) {
            depositing = true;
            target = hall.getBlockPos();
            return true;
        }
        if (night) {
            return false;
        }
        depositing = false;
        bareWithoutSeed = false;
        target = findWork(hall);
        if (target == null && bareWithoutSeed) {
            // Farmland waiting and not a seed anywhere: go pull some grass for seeds.
            target = findGrass(hall);
            if (target == null) {
                hall.noteNoSeeds();
            }
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && farmer.getJob() == ResidentJob.FARMER && !farmer.isDowned();
    }

    @Override
    public void start() {
        ticks = 0;
        workTicks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 spot = Vec3.atBottomCenterOf(target);
        farmer.getLookControl().setLookAt(spot.x, spot.y, spot.z);
        double reach = depositing ? 9.0D : 3.0D;
        if (farmer.position().distanceToSqr(spot) > reach) {
            if (ticks % 20 == 1) {
                farmer.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.6D);
            }
            return;
        }
        farmer.getNavigation().stop();
        if (!depositing && workTicks++ < workTime()) {
            // Bent over the crop a moment - shorter for a talented farmer.
            if (workTicks % 10 == 1) {
                farmer.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        VillageHallBlockEntity hall = farmer.hall();
        if (hall != null) {
            if (depositing) {
                deposit(hall);
            } else {
                work(hall, target);
            }
        }
        target = null;
        // Straight on to the next crop.
        cooldown = 5;
    }

    /** 20 ticks a crop at ★3; 33 at ★1, 14 at ★5. */
    private int workTime() {
        return Math.round(20.0F / VillageTuning.talentSpeed(farmer.talent(ResidentJob.FARMER)));
    }

    @Override
    public void stop() {
        target = null;
        farmer.getNavigation().stop();
    }

    // ---- finding work ----

    /**
     * The nearest ripe crop or bare farmland: around the farmer, the last field they worked, and every farm
     * the architects built (so a farm across the village isn't forgotten).
     */
    private @Nullable BlockPos findWork(VillageHallBlockEntity hall) {
        boolean hasSeed = hasSeed(hall);
        // Their own farm first (after the M13 test, every farm has a farmer); the rest when it's all done.
        Construction own = hall.works().farmOf(farmer.getUUID());
        if (own != null) {
            BlockPos found = findWorkAround(hall, own.center().above(), hasSeed);
            if (found != null) {
                return found;
            }
        }
        List<BlockPos> bases = new ArrayList<>();
        bases.add(farmer.blockPosition());
        if (lastField != null) {
            bases.add(lastField);
        }
        for (Construction building : hall.works().buildings()) {
            if (building.blueprint() == Blueprint.FARM) {
                bases.add(building.center().above());
            }
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos base : bases) {
            BlockPos found = findWorkAround(hall, base, hasSeed);
            if (found != null && found.distSqr(farmer.blockPosition()) < bestDist) {
                best = found;
                bestDist = found.distSqr(farmer.blockPosition());
            }
        }
        return best;
    }

    private @Nullable BlockPos findWorkAround(VillageHallBlockEntity hall, BlockPos base, boolean hasSeed) {
        Level level = farmer.level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (!hall.contains(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    boolean ripe = state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
                    boolean bare = state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
                    if (bare && !hasSeed) {
                        bareWithoutSeed = true;
                        bare = false;
                    }
                    if (ripe || bare) {
                        double dist = pos.distSqr(farmer.blockPosition());
                        if (dist < bestDist) {
                            best = pos.immutable();
                            bestDist = dist;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Grass or ferns nearby to pull for seeds. */
    private @Nullable BlockPos findGrass(VillageHallBlockEntity hall) {
        Level level = farmer.level();
        BlockPos base = farmer.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (hall.contains(pos) && isSeedGrass(level.getBlockState(pos)) && pos.distSqr(base) < bestDist) {
                        best = pos.immutable();
                        bestDist = pos.distSqr(base);
                    }
                }
            }
        }
        return best;
    }

    private static boolean isSeedGrass(BlockState state) {
        return state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    // ---- working ----

    private void work(VillageHallBlockEntity hall, BlockPos pos) {
        Level level = farmer.level();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            if (level instanceof ServerLevel server) {
                for (ItemStack drop : Block.getDrops(state, server, pos, null, farmer, farmer.getMainHandItem())) {
                    keep(hall, drop);
                }
            }
            level.destroyBlock(pos, false, farmer);
            Item seed = crop.getCloneItemStack(level, pos, state).getItem();
            if (takeSeed(hall, seed)) {
                level.setBlockAndUpdate(pos, crop.getStateForAge(0));
            }
        } else if (isSeedGrass(state)) {
            if (level instanceof ServerLevel server) {
                for (ItemStack drop : Block.getDrops(state, server, pos, null, farmer, farmer.getMainHandItem())) {
                    keep(hall, drop);
                }
            }
            level.destroyBlock(pos, false, farmer);
        } else if (state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            Item seed = anySeed(hall);
            if (seed instanceof BlockItem blockItem && takeSeed(hall, seed)) {
                level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        farmer.swing(InteractionHand.MAIN_HAND);
        lastField = pos;
    }

    private void keep(VillageHallBlockEntity hall, ItemStack stack) {
        ItemStack rest = farmer.pack().addItem(stack);
        if (!rest.isEmpty()) {
            rest = hall.store(rest);
        }
        if (!rest.isEmpty()) {
            farmer.spawnAtLocation(rest);
        }
    }

    private void deposit(VillageHallBlockEntity hall) {
        SimpleContainer pack = farmer.pack();
        bakeBread(pack);
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack stack = pack.getItem(i);
            if (!stack.isEmpty()) {
                pack.setItem(i, hall.store(stack));
            }
        }
        farmer.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Wheat isn't a meal by itself: until there is a cook, the farmer brings it in as bread (3 wheat each,
     * like the crafting recipe); the odd stalks stay in the bag for next time.
     */
    private static void bakeBread(SimpleContainer pack) {
        int wheat = pack.countItem(Items.WHEAT);
        int loaves = wheat / 3;
        if (loaves == 0) {
            return;
        }
        pack.removeItemType(Items.WHEAT, loaves * 3);
        ItemStack rest = pack.addItem(new ItemStack(Items.BREAD, loaves));
        if (!rest.isEmpty()) {
            pack.addItem(new ItemStack(Items.WHEAT, rest.getCount() * 3));
        }
    }

    private boolean bagFull() {
        int used = 0;
        for (int i = 0; i < farmer.pack().getContainerSize(); i++) {
            if (!farmer.pack().getItem(i).isEmpty()) {
                used++;
            }
        }
        return used >= 6;
    }

    private boolean bagEmpty() {
        return farmer.pack().isEmpty();
    }

    // ---- seeds ----

    private static boolean isSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock;
    }

    private boolean hasSeed(VillageHallBlockEntity hall) {
        return anySeed(hall) != null;
    }

    private @Nullable Item anySeed(VillageHallBlockEntity hall) {
        SimpleContainer pack = farmer.pack();
        for (int i = 0; i < pack.getContainerSize(); i++) {
            if (isSeed(pack.getItem(i))) {
                return pack.getItem(i).getItem();
            }
        }
        SimpleContainer storage = hall.getStorage();
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (isSeed(storage.getItem(i))) {
                return storage.getItem(i).getItem();
            }
        }
        return null;
    }

    private boolean takeSeed(VillageHallBlockEntity hall, Item seed) {
        SimpleContainer pack = farmer.pack();
        for (int i = 0; i < pack.getContainerSize(); i++) {
            if (pack.getItem(i).is(seed)) {
                pack.removeItem(i, 1);
                return true;
            }
        }
        return hall.takeAny(stack -> stack.is(seed)) != null;
    }
}
