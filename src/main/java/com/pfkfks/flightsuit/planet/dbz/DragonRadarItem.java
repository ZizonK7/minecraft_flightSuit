package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Bulma's Dragon Radar (DESIGN.md 4-16): held, it points to the nearest Dragon Ball still lying out there. */
public class DragonRadarItem extends Item {
    public DragonRadarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && player.tickCount % 10 == 0
                && (selected || player.getOffhandItem() == stack)) {
            player.displayClientMessage(DragonBalls.radar(player), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.dragon_radar").withStyle(ChatFormatting.GRAY));
    }
}
