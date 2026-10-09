package com.pfkfks.flightsuit.suit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-use charge pack - the only way to recharge until the station + generators arrive (M2).
 * Charges the worn suit (chest battery first); with nothing worn, charges the parts inside a capsule
 * held in the other hand.
 */
public class EnergyCellItem extends Item {
    public EnergyCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack cell = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(cell);
        }

        int remaining = SuitTuning.ENERGY_CELL_CHARGE;
        List<EquipmentSlot> order = List.of(EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        for (EquipmentSlot slot : order) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.getItem() instanceof SuitArmorItem) {
                remaining -= SuitEnergy.receive(piece, remaining);
            }
        }

        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (remaining == SuitTuning.ENERGY_CELL_CHARGE && other.getItem() instanceof SuitCapsuleItem) {
            List<EquipmentSlot> stored = new ArrayList<>(SuitCapsuleItem.getParts(other).keySet());
            stored.sort((a, b) -> a == EquipmentSlot.CHEST ? -1 : b == EquipmentSlot.CHEST ? 1 : 0);
            for (EquipmentSlot slot : stored) {
                ItemStack part = SuitCapsuleItem.getParts(other).get(slot);
                remaining -= SuitEnergy.receive(part, remaining);
                SuitCapsuleItem.setPart(other, slot, part);
            }
        }

        int used = SuitTuning.ENERGY_CELL_CHARGE - remaining;
        if (used <= 0) {
            player.displayClientMessage(Component.translatable("message.flightsuit.cell_nothing"), true);
            return InteractionResultHolder.fail(cell);
        }
        if (!player.getAbilities().instabuild) {
            cell.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("message.flightsuit.cell_used", used), true);
        return InteractionResultHolder.consume(cell);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.energy_cell", SuitTuning.ENERGY_CELL_CHARGE)
                .withStyle(ChatFormatting.AQUA));
    }
}
