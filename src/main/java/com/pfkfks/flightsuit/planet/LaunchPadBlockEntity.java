package com.pfkfks.flightsuit.planet;

import com.pfkfks.flightsuit.block.PowerBlockEntity;
import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The launch pad's charge (DESIGN.md 4-16: 큰 전력이 필요하다). A consumer on the grid with a big buffer; a
 * launch takes SpaceTravel.LAUNCH_COST of it at once (the round trip - coming home is paid for).
 */
public class LaunchPadBlockEntity extends PowerBlockEntity {
    public static final int CAPACITY = 120_000;
    public static final int INPUT = 2_000;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCH_PAD.get(), pos, state, CAPACITY, INPUT, 0);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.CONSUMER;
    }

    public boolean canLaunch() {
        return energy().getEnergyStored() >= SpaceTravel.LAUNCH_COST;
    }

    public void spend() {
        energy().consume(SpaceTravel.LAUNCH_COST);
    }
}
