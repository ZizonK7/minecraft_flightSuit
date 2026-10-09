package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.suit.MainStation;
import com.pfkfks.flightsuit.suit.OwnedStations;
import com.pfkfks.flightsuit.suit.SuitCapsuleItem;
import com.pfkfks.flightsuit.suit.SuitUpManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Suit station platform. Right-click (empty hand):
 * - wearing suit pieces -> dock them here (walk-in unsuit);
 * - suit docked, not wearing one -> suit up from the station;
 * - otherwise / sneaking -> status, and this becomes your main station.
 * Right-click with a filled capsule -> move that suit into the station.
 */
public class SuitStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    public SuitStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The docked suit faces the player who placed the station.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuitStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SUIT_STATION.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> SuitStationBlockEntity.serverTick(lvl, pos, st, (SuitStationBlockEntity) be);
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
        if (!(level.getBlockEntity(pos) instanceof SuitStationBlockEntity station) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Every station you use is remembered (suit wheel); the first one, or one you sneak-click, becomes main.
        OwnedStations.add(serverPlayer, pos);
        if (MainStation.get(serverPlayer) == null || player.isShiftKeyDown()) {
            MainStation.set(serverPlayer, pos);
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable("message.flightsuit.main_station_set"), true);
                return InteractionResult.CONSUME;
            }
        }
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof SuitCapsuleItem && SuitCapsuleItem.hasParts(held)) {
            Map<EquipmentSlot, ItemStack> parts = SuitCapsuleItem.getParts(held);
            if (!station.canDock(parts)) {
                player.displayClientMessage(Component.translatable("message.flightsuit.station_occupied"), true);
                return InteractionResult.CONSUME;
            }
            SuitCapsuleItem.clearParts(held);
            station.dock(parts);
            player.displayClientMessage(Component.translatable("message.flightsuit.station_docked"), true);
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty() && !player.isShiftKeyDown()) {
            if (SuitUpManager.dockAtStation(serverPlayer, station)) {
                return InteractionResult.CONSUME;
            }
            if (station.hasSuit() && SuitUpManager.suitUpAtStation(serverPlayer, station)) {
                return InteractionResult.CONSUME;
            }
        }
        sendStatus(serverPlayer, station);
        return InteractionResult.CONSUME;
    }

    private static void sendStatus(ServerPlayer player, SuitStationBlockEntity station) {
        Component suit = station.hasSuit() && station.getSuitType() != null
                ? Component.literal(station.getSuitType().hudName() + " " + station.suitChargePercent() + "%")
                : Component.translatable("message.flightsuit.station_no_suit");
        player.displayClientMessage(Component.translatable("message.flightsuit.station_status",
                station.energy().getEnergyStored(), station.energy().getMaxEnergyStored(), suit).withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SuitStationBlockEntity station) {
            for (ItemStack stack : station.takeAll().values()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
