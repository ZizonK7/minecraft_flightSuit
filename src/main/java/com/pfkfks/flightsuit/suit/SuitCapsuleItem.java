package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Portable suit storage - the M1 stand-in for the station (Hoi-Poi capsule flavor). It holds up to one
 * piece per armor slot. Suiting up empties it (the capsule itself stays in the inventory) and packing the
 * suit away fills it again, so pieces always exist exactly once.
 */
public class SuitCapsuleItem extends Item {
    private static final String PARTS_TAG = "SuitParts";

    private final SuitType suitType;

    public SuitCapsuleItem(SuitType suitType, Properties properties) {
        super(properties);
        this.suitType = suitType;
    }

    public SuitType getSuitType() {
        return suitType;
    }

    public ItemStack createFilledCapsule() {
        ItemStack capsule = new ItemStack(this);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            setPart(capsule, slot, new ItemStack(ModItems.pieceFor(suitType, slot)));
        }
        return capsule;
    }

    public static Map<EquipmentSlot, ItemStack> getParts(ItemStack capsule) {
        Map<EquipmentSlot, ItemStack> parts = new EnumMap<>(EquipmentSlot.class);
        CompoundTag tag = capsule.getTag();
        if (tag == null || !tag.contains(PARTS_TAG)) {
            return parts;
        }
        CompoundTag partsTag = tag.getCompound(PARTS_TAG);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (partsTag.contains(slot.getName())) {
                ItemStack stack = ItemStack.of(partsTag.getCompound(slot.getName()));
                if (!stack.isEmpty()) {
                    parts.put(slot, stack);
                }
            }
        }
        return parts;
    }

    public static boolean hasParts(ItemStack capsule) {
        return !getParts(capsule).isEmpty();
    }

    public static boolean hasPart(ItemStack capsule, EquipmentSlot slot) {
        return getParts(capsule).containsKey(slot);
    }

    public static void setPart(ItemStack capsule, EquipmentSlot slot, ItemStack stack) {
        CompoundTag partsTag = capsule.getOrCreateTag().getCompound(PARTS_TAG);
        if (stack.isEmpty()) {
            partsTag.remove(slot.getName());
        } else {
            partsTag.put(slot.getName(), stack.save(new CompoundTag()));
        }
        capsule.getOrCreateTag().put(PARTS_TAG, partsTag);
    }

    public static void clearParts(ItemStack capsule) {
        if (capsule.getTag() != null) {
            capsule.getTag().remove(PARTS_TAG);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack capsule = player.getItemInHand(hand);
        if (!hasParts(capsule)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.flightsuit.capsule_empty"), true);
            }
            return InteractionResultHolder.fail(capsule);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SuitUpManager.startFromCapsule(serverPlayer, capsule);
        }
        return InteractionResultHolder.sidedSuccess(capsule, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasParts(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Map<EquipmentSlot, ItemStack> parts = getParts(stack);
        if (parts.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.flightsuit.capsule.empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Map.Entry<EquipmentSlot, ItemStack> entry : parts.entrySet()) {
                ItemStack part = entry.getValue();
                tooltip.add(Component.literal("- ").append(part.getHoverName())
                        .append(Component.literal("  " + SuitEnergy.get(part) + "/" + SuitEnergy.capacity(part) + " FE"))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.flightsuit.capsule.hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}
