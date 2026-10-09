package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Security sensor (DESIGN.md 4-14, M14): a powered eye on the grid. While it has power it is armed (red lens)
 * and catches hidden thieves within ThiefTuning.SENSOR_RANGE - the village alarm goes off and it gives a
 * redstone signal for a few seconds (TRIPPED). Right-click shows its state.
 */
public class SecuritySensorBlock extends Block implements EntityBlock {
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    public static final BooleanProperty TRIPPED = BlockStateProperties.POWERED;

    public SecuritySensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ARMED, false).setValue(TRIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED, TRIPPED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecuritySensorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SECURITY_SENSOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> SecuritySensorBlockEntity.serverTick(lvl, pos, st, (SecuritySensorBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(TRIPPED) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SecuritySensorBlockEntity sensor) {
            player.displayClientMessage(Component.translatable(sensor.isArmed() ? "message.flightsuit.sensor_armed" : "message.flightsuit.sensor_off",
                    sensor.energy().getEnergyStored(), sensor.energy().getMaxEnergyStored()), true);
        }
        return InteractionResult.CONSUME;
    }
}
