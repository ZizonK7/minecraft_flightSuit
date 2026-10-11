package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
 * M18 power plant block: a boiler with a firebox door on the front and a stack on top. Use it with fuel to load
 * the hopper; use it empty-handed for its state. Smokes from the stack while it burns.
 */
public class PowerPlantBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public PowerPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerPlantBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.POWER_PLANT.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> PowerPlantBlockEntity.serverTick(lvl, pos, st, (PowerPlantBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PowerPlantBlockEntity plant)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (PowerPlantBlockEntity.isFuel(held)) {
            ItemStack toInsert = player.getAbilities().instabuild ? held.copy() : held;
            int taken = plant.insertFuel(toInsert);
            if (taken > 0) {
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 0.8F);
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.3F);
                player.displayClientMessage(Component.translatable("message.flightsuit.power_plant_loaded", taken, plant.fuelCount()), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.flightsuit.power_plant_full"), true);
            return InteractionResult.CONSUME;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            player.displayClientMessage(Component.translatable("message.flightsuit.power_plant_status",
                    plant.energy().getEnergyStored(), plant.energy().getMaxEnergyStored(), plant.fuelCount(),
                    plant.isBurning() ? Component.translatable("message.flightsuit.power_plant_burning", com.pfkfks.flightsuit.energy.PowerTuning.POWER_PLANT_OUTPUT)
                            : Component.translatable("message.flightsuit.power_plant_cold")), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        // Smoke from the stack, a glow at the firebox door.
        level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D, pos.getY() + 1.1D,
                pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D, 0.0D, 0.07D, 0.0D);
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.52D;
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.52D;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.35D, z, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextInt(8) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6F, 0.7F, false);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PowerPlantBlockEntity plant) {
            Containers.dropContents(level, pos, plant.fuel());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
