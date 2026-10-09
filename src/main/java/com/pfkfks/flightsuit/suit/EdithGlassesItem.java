package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.client.SuitArmorModels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * EDITH glasses (DESIGN.md 4-1): what you wear day to day. With them on (or carried while suited), G calls
 * the main suit from its station and sends it back home, and the glasses-only command HUD is shown.
 * Head-slot item, so suiting up moves them to the inventory and sending the suit home puts them back on.
 */
public class EdithGlassesItem extends ArmorItem {
    public EdithGlassesItem(Properties properties) {
        super(SuitArmorMaterial.EDITH, Type.HELMET, properties);
    }

    /** Worn, or carried (they go to the inventory while the suit helmet is on). */
    public static boolean has(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof EdithGlassesItem) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof EdithGlassesItem) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof EdithGlassesItem;
    }

    /** Puts carried glasses back on if the head slot is free. */
    public static void reequip(Player player) {
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem() instanceof EdithGlassesItem) {
                player.setItemSlot(EquipmentSlot.HEAD, items.get(i).copy());
                items.set(i, ItemStack.EMPTY);
                return;
            }
        }
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return FlightSuitMod.MODID + ":textures/models/armor/edith_glasses.png";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.edith").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                         EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                return SuitArmorModels.glasses();
            }
        });
    }
}
