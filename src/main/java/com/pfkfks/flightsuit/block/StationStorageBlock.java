package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Station storage block: right-click opens the 54-slot vault, sneak + empty-hand right-click sorts it.
 * Breaking it spills the contents.
 */
public class StationStorageBlock extends Block implements EntityBlock {
    public StationStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationStorageBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.STATION_STORAGE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> StationStorageBlockEntity.serverTick(lvl, pos, st, (StationStorageBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof StationStorageBlockEntity storage) {
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
                storage.sort();
                player.displayClientMessage(Component.translatable("message.flightsuit.storage_sorted",
                        storage.usedSlots(), StationStorageBlockEntity.SIZE,
                        storage.energy().getEnergyStored(), storage.energy().getMaxEnergyStored()), true);
            } else {
                player.openMenu(storage);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StationStorageBlockEntity storage) {
            Containers.dropContents(level, pos, storage.getStorage());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
