package com.pfkfks.flightsuit.suit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Snapshot of which suit pieces a player has on. Works on both sides (armor slots are synced). */
public record WornSuit(boolean helmet, boolean chest, boolean legs, boolean boots, SuitType fullSetType) {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static WornSuit of(Player player) {
        SuitType head = typeIn(player, EquipmentSlot.HEAD);
        SuitType chest = typeIn(player, EquipmentSlot.CHEST);
        SuitType legs = typeIn(player, EquipmentSlot.LEGS);
        SuitType feet = typeIn(player, EquipmentSlot.FEET);
        SuitType full = head != null && head == chest && chest == legs && legs == feet ? head : null;
        return new WornSuit(head != null, chest != null, legs != null, feet != null, full);
    }

    private static SuitType typeIn(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof SuitArmorItem armor ? armor.getSuitType() : null;
    }

    public boolean any() {
        return helmet || chest || legs || boots;
    }

    public boolean fullSet() {
        return fullSetType != null;
    }

    /** A full set of a suit that flies (Mark 4 fights on foot). */
    public boolean canFly() {
        return fullSetType != null && fullSetType.suitClass().canFly();
    }

    /** Boots whose thrusters work on their own (not a grounded suit's boots). */
    public static boolean hasThrusterBoots(Player player) {
        SuitType feet = typeIn(player, EquipmentSlot.FEET);
        return feet != null && feet.suitClass().canFly();
    }

    /** Any single suit type present, preferring the chest piece - used for HUD naming. */
    public static SuitType primaryType(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            SuitType type = typeIn(player, slot);
            if (type != null) {
                return type;
            }
        }
        return null;
    }
}
