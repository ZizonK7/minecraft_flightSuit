package com.pfkfks.flightsuit.cleaner;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cleaner robot dock. Right-click (empty hand): open the bin of vacuumed items + status.
 * Sneak + right-click (empty hand): start drawing a new cleaning area (again to cancel).
 * Right-click with a stick: erase all areas.
 */
public class CleanerDockBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public CleanerDockBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleanerDockBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CLEANER_DOCK.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> CleanerDockBlockEntity.serverTick(lvl, pos, st, (CleanerDockBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CleanerDockBlockEntity dock) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.getMainHandItem().is(Items.STICK)) {
            dock.clearAreas();
            player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_areas_cleared"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            CleanerAreaSelection.toggle(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        player.openMenu(dock);
        CleanerRobotEntity robot = CleanerRobotEntity.findFor(serverPlayer.serverLevel(), pos);
        Component robotState = robot == null ? Component.translatable("message.flightsuit.cleaner_no_robot")
                : Component.translatable(robot.isDocked() ? "message.flightsuit.cleaner_docked" : "message.flightsuit.cleaner_working");
        player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_status", dock.getAreas().size(),
                dock.energy().getEnergyStored(), dock.energy().getMaxEnergyStored(), robotState).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CleanerDockBlockEntity dock) {
            Containers.dropContents(level, pos, dock.getBin());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
