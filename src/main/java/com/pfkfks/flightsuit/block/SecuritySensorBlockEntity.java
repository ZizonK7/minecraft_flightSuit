package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.thief.ThiefTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The security sensor's power and trip state (DESIGN.md 4-14). Sits on the grid as a consumer: armed while it
 * has power (its upkeep trickles away). Thieves check for armed sensors around them (ThiefEntity) and trip them.
 */
public class SecuritySensorBlockEntity extends PowerBlockEntity {
    private static final int TRIP_TICKS = 100;

    private int trippedTicks;

    public SecuritySensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_SENSOR.get(), pos, state, ThiefTuning.SENSOR_CAPACITY, ThiefTuning.SENSOR_INPUT, 0);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.CONSUMER;
    }

    public boolean isArmed() {
        return energy().getEnergyStored() > 0;
    }

    /** A thief was caught by this eye: a redstone pulse for a few seconds. */
    public void trip() {
        trippedTicks = TRIP_TICKS;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecuritySensorBlockEntity sensor) {
        sensor.energy().consume(ThiefTuning.SENSOR_UPKEEP);
        if (sensor.trippedTicks > 0) {
            sensor.trippedTicks--;
        }
        boolean armed = sensor.isArmed();
        boolean tripped = sensor.trippedTicks > 0;
        if (state.getValue(SecuritySensorBlock.ARMED) != armed || state.getValue(SecuritySensorBlock.TRIPPED) != tripped) {
            level.setBlock(pos, state.setValue(SecuritySensorBlock.ARMED, armed).setValue(SecuritySensorBlock.TRIPPED, tripped), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }
}
