package com.pfkfks.flightsuit.car;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Hoi-Poi capsule with a hover car inside: use it on the ground and - pop - the car appears. */
public class HoverCarCapsuleItem extends Item {
    private static final String ENERGY_TAG = "CarEnergy";

    public HoverCarCapsuleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack withEnergy(ItemStack stack, int energy) {
        stack.getOrCreateTag().putInt(ENERGY_TAG, energy);
        return stack;
    }

    public static int energyOf(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(ENERGY_TAG) ? stack.getTag().getInt(ENERGY_TAG) : HoverCarEntity.CAPACITY;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Vec3 spot = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
        HoverCarEntity car = HoverCarEntity.spawn(level, spot, yaw, energyOf(context.getItemInHand()));
        car.poof();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.energy", energyOf(stack), HoverCarEntity.CAPACITY)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.flightsuit.hover_car").withStyle(ChatFormatting.GRAY));
    }
}
