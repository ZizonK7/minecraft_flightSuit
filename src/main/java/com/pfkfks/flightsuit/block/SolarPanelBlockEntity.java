package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Daylight-only generation; needs open sky above, weakened by rain. */
public class SolarPanelBlockEntity extends PowerBlockEntity {
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state, PowerTuning.SOLAR_BUFFER, 0, PowerTuning.GRID_PUSH);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.PRODUCER;
    }

    public int currentOutput() {
        if (level == null || !level.isDay() || !level.canSeeSky(worldPosition.above())) {
            return 0;
        }
        float factor = level.isRaining() ? PowerTuning.SOLAR_RAIN_FACTOR : 1.0F;
        return Math.round(PowerTuning.SOLAR_OUTPUT * factor);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
        panel.energy.generate(panel.currentOutput());
        PowerGrid.distribute(level, panel, PowerTuning.GRID_PUSH, PowerGrid.Role.CONSUMER, PowerGrid.Role.STORAGE);
    }
}
