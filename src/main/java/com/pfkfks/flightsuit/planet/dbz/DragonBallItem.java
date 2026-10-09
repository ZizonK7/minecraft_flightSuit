package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Dragon Ball in hand (1 to 7 stars, NBT "Stars"). With all seven, right-click one to call Shenron
 * (DragonBalls.summon). Dropped, it never despawns and nothing destroys it.
 */
public class DragonBallItem extends Item {
    public static final String STARS = "Stars";

    public DragonBallItem(Properties properties) {
        super(properties);
    }

    public static int stars(ItemStack stack) {
        return stack.getTag() == null ? 1 : Math.max(1, Math.min(7, stack.getTag().getInt(STARS)));
    }

    public static ItemStack of(Item item, int stars) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(STARS, stars);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            DragonBalls.summon(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.flightsuit.dragon_ball.stars", stars(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.dragon_ball").withStyle(ChatFormatting.GRAY));
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
