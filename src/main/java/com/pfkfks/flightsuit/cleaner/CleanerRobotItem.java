package com.pfkfks.flightsuit.cleaner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Boxed cleaner robot: use it on a cleaner dock to set it down there (one robot per dock). */
public class CleanerRobotItem extends Item {
    public CleanerRobotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof CleanerDockBlockEntity dock)) {
            if (context.getPlayer() != null && !level.isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable("message.flightsuit.cleaner_needs_dock"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        if (CleanerRobotEntity.findFor(serverLevel, dock.getBlockPos()) != null) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.flightsuit.cleaner_dock_taken"), true);
            }
            return InteractionResult.FAIL;
        }
        CleanerRobotEntity.spawnAt(serverLevel, dock);
        level.playSound(null, dock.getBlockPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.2F);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.cleaner_robot").withStyle(ChatFormatting.GRAY));
    }
}
