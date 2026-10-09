package com.pfkfks.flightsuit.thanos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An Infinity Stone (DESIGN.md 4-16). Whoever carries one has it on their record (Thanos comes for them - see
 * ThanosRaid); six fill the Infinity Gauntlet. It never despawns and nothing destroys it.
 */
public class InfinityStoneItem extends Item {
    private final InfinityStone stone;

    public InfinityStoneItem(InfinityStone stone, Properties properties) {
        super(properties);
        this.stone = stone;
    }

    public InfinityStone stone() {
        return stone;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && player.tickCount % 40 == 0) {
            ThanosSaga.record(player, stone);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return stone.displayName();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.infinity_stone").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canBeHurtBy(DamageSource source) {
        return false;
    }
}
