package com.pfkfks.flightsuit.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Launch pad (DESIGN.md 4-16): right-click shows its charge and where the ship can fly. Placed by a player it lays
 * out the landing platform round itself (2026-10-12, for the Capsule Corp size ship): a white disc nine blocks across
 * with a yellow ring and four lights, only where nothing stands.
 */
public class LaunchPadBlock extends Block implements EntityBlock {
    public static final int PLATFORM_RADIUS = 4;

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player)) {
            return;
        }
        double outer = PLATFORM_RADIUS + 0.3D;
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > outer || dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos at = pos.offset(dx, 0, dz);
                BlockState there = level.getBlockState(at);
                if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
                    continue;
                }
                boolean light = r > PLATFORM_RADIUS - 0.5D && (dx == 0 || dz == 0);
                Block block = light ? Blocks.SEA_LANTERN : r > PLATFORM_RADIUS - 1.2D ? Blocks.YELLOW_CONCRETE : Blocks.WHITE_CONCRETE;
                level.setBlock(at, block.defaultBlockState(), 3);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
            SpaceTravel.padMenu(serverPlayer, pad);
        }
        return InteractionResult.CONSUME;
    }
}
