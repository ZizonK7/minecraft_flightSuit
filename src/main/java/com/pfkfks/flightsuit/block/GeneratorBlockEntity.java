package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

/**
 * Burns any furnace fuel for a steady output. No GUI: right-click with fuel to load it (one stack of
 * reserve fuel is kept inside), and the block lights up while burning.
 */
public class GeneratorBlockEntity extends PowerBlockEntity {
    private ItemStack fuel = ItemStack.EMPTY;
    private int burnTime;
    private int burnTimeTotal;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state, PowerTuning.GENERATOR_BUFFER, 0, PowerTuning.GRID_PUSH);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.PRODUCER;
    }

    public static boolean isFuel(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, null) > 0;
    }

    /** Moves as much of {@code stack} as fits into the reserve. @return items taken. */
    public int insertFuel(ItemStack stack) {
        if (!isFuel(stack)) {
            return 0;
        }
        if (fuel.isEmpty()) {
            int take = Math.min(stack.getCount(), stack.getMaxStackSize());
            fuel = stack.copyWithCount(take);
            setChanged();
            return take;
        }
        if (!ItemStack.isSameItemSameTags(fuel, stack)) {
            return 0;
        }
        int take = Math.min(stack.getCount(), fuel.getMaxStackSize() - fuel.getCount());
        fuel.grow(take);
        setChanged();
        return take;
    }

    public ItemStack getFuel() {
        return fuel;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity gen) {
        boolean wasBurning = gen.isBurning();
        boolean bufferFull = gen.energy.getEnergyStored() >= gen.energy.getMaxEnergyStored();
        if (gen.burnTime <= 0 && !bufferFull && !gen.fuel.isEmpty()) {
            int time = ForgeHooks.getBurnTime(gen.fuel, null);
            if (time > 0) {
                ItemStack remainder = gen.fuel.getCraftingRemainingItem();
                gen.fuel.shrink(1);
                if (gen.fuel.isEmpty() && !remainder.isEmpty()) {
                    gen.fuel = remainder; // lava bucket -> empty bucket stays inside
                }
                gen.burnTime = time;
                gen.burnTimeTotal = time;
                gen.setChanged();
            }
        }
        if (gen.burnTime > 0) {
            gen.burnTime--;
            gen.energy.generate(PowerTuning.GENERATOR_OUTPUT);
        }
        PowerGrid.distribute(level, gen, PowerTuning.GRID_PUSH, PowerGrid.Role.CONSUMER, PowerGrid.Role.STORAGE);

        if (wasBurning != gen.isBurning()) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, gen.isBurning()), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Fuel", fuel.save(new CompoundTag()));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel = ItemStack.of(tag.getCompound("Fuel"));
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
    }
}
