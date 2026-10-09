package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bulk storage: soaks up surplus from producers and feeds stations (never other batteries). */
public class BatteryBlockEntity extends PowerBlockEntity {
    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state,
                PowerTuning.BATTERY_CAPACITY, PowerTuning.BATTERY_TRANSFER, PowerTuning.BATTERY_TRANSFER);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.STORAGE;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        PowerGrid.distribute(level, battery, PowerTuning.BATTERY_TRANSFER, PowerGrid.Role.CONSUMER);
    }
}
