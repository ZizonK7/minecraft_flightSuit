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

public class GeneratorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public GeneratorBlock(Properties properties) {
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
        return new GeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.GENERATOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> GeneratorBlockEntity.serverTick(lvl, pos, st, (GeneratorBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (GeneratorBlockEntity.isFuel(held)) {
            int taken = generator.insertFuel(held);
            if (taken > 0) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(taken);
                }
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
                return InteractionResult.CONSUME;
            }
        }
        if (hand == InteractionHand.MAIN_HAND) {
            ItemStack fuel = generator.getFuel();
            player.displayClientMessage(Component.translatable("message.flightsuit.generator_status",
                    generator.energy().getEnergyStored(), generator.energy().getMaxEnergyStored(),
                    fuel.isEmpty() ? Component.translatable("message.flightsuit.generator_no_fuel") : fuel.getHoverName(),
                    fuel.getCount()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.52D;
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.52D;
        level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.4D, z, 0.0D, 0.02D, 0.0D);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.3D, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, generator.getFuel());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
