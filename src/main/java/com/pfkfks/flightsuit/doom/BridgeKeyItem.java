package com.pfkfks.flightsuit.doom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Reed Richards' bridge key (DESIGN.md 4-17): touch it to an amethyst frame (or the space inside one) and the
 * multiverse gate lights - Multiverse.light. Never used up; Reed has a spare if you lose it.
 */
public class BridgeKeyItem extends Item {
    public BridgeKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        // Inside the frame first (the face clicked), then any empty block beside a clicked frame block - so clicking
        // the frame's outer face works too.
        java.util.List<BlockPos> starts = new java.util.ArrayList<>();
        starts.add(level.getBlockState(clicked).isAir() ? clicked : clicked.relative(context.getClickedFace()));
        if (Multiverse.isFrame(level.getBlockState(clicked))) {
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                BlockPos next = clicked.relative(side);
                if (level.getBlockState(next).isAir() && !starts.contains(next)) {
                    starts.add(next);
                }
            }
        }
        for (BlockPos start : starts) {
            if (Multiverse.light(level, start)) {
                return InteractionResult.CONSUME;
            }
        }
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable("doom.flightsuit.gate.no_frame").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
