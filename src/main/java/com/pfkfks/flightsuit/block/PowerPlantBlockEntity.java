package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

/**
 * M18 power plant: the village's coal-fired station. A hopper of fuel (POWER_PLANT_SLOTS stacks) burns a stack at
 * a time for POWER_PLANT_OUTPUT a tick - each lump for POWER_PLANT_BURN_FACTOR of its furnace time, so it gets
 * through coal faster than the small generator but makes far more a second. No GUI: use it with fuel to load it;
 * the village's engineer (EngineerWorkGoal) keeps it fed from the hall's storage. Counts what it made today for
 * the hall's board.
 */
public class PowerPlantBlockEntity extends PowerBlockEntity {
    private final SimpleContainer fuel = new SimpleContainer(PowerTuning.POWER_PLANT_SLOTS);
    private int burnTime;
    private int burnTimeTotal;
    /** FE made since the last morning (reset by the hall through {@link #takeMadeToday}). */
    private long madeToday;

    public PowerPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_PLANT.get(), pos, state, PowerTuning.POWER_PLANT_BUFFER, 0, PowerTuning.CABLE_TRANSFER);
        fuel.addListener(container -> setChanged());
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.PRODUCER;
    }

    public static boolean isFuel(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, null) > 0;
    }

    public SimpleContainer fuel() {
        return fuel;
    }

    /** Moves as much of {@code stack} as fits into the hopper. @return items taken. */
    public int insertFuel(ItemStack stack) {
        if (!isFuel(stack)) {
            return 0;
        }
        int before = stack.getCount();
        ItemStack left = fuel.addItem(stack.copy());
        int taken = before - left.getCount();
        if (taken > 0) {
            stack.shrink(taken);
            setChanged();
        }
        return taken;
    }

    /** Lumps of fuel in the hopper (whatever kind). */
    public int fuelCount() {
        int count = 0;
        for (int i = 0; i < fuel.getContainerSize(); i++) {
            count += fuel.getItem(i).getCount();
        }
        return count;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    public long takeMadeToday() {
        long made = madeToday;
        madeToday = 0L;
        return made;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerPlantBlockEntity plant) {
        boolean wasBurning = plant.isBurning();
        boolean bufferFull = plant.energy.getEnergyStored() >= plant.energy.getMaxEnergyStored();
        if (plant.burnTime <= 0 && !bufferFull) {
            for (int i = 0; i < plant.fuel.getContainerSize(); i++) {
                ItemStack stack = plant.fuel.getItem(i);
                int time = stack.isEmpty() ? 0 : ForgeHooks.getBurnTime(stack, null);
                if (time > 0) {
                    ItemStack remainder = stack.getCraftingRemainingItem();
                    stack.shrink(1);
                    if (stack.isEmpty() && !remainder.isEmpty()) {
                        plant.fuel.setItem(i, remainder);
                    }
                    plant.burnTime = Math.max(1, (int) (time * PowerTuning.POWER_PLANT_BURN_FACTOR));
                    plant.burnTimeTotal = plant.burnTime;
                    plant.setChanged();
                    break;
                }
            }
        }
        if (plant.burnTime > 0) {
            plant.burnTime--;
            plant.madeToday += plant.energy.generate(PowerTuning.POWER_PLANT_OUTPUT);
        }
        PowerGrid.distribute(level, plant, PowerTuning.CABLE_TRANSFER, PowerGrid.Role.CONSUMER, PowerGrid.Role.STORAGE);
        if (wasBurning != plant.isBurning()) {
            level.setBlock(pos, state.setValue(PowerPlantBlock.LIT, plant.isBurning()), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Fuel", fuel.createTag());
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        tag.putLong("MadeToday", madeToday);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel.fromTag(tag.getList("Fuel", 10));
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        madeToday = tag.getLong("MadeToday");
    }
}
