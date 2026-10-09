package com.pfkfks.flightsuit.suit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Energy stored on each suit piece's NBT. A piece with no energy tag yet counts as fully charged, so
 * freshly crafted / creative-tab pieces work straight away.
 *
 * Drain rule (DESIGN.md 4-3): a worn suit chestplate is the shared battery for every piece; without one,
 * each piece runs off its own small buffer.
 */
public final class SuitEnergy {
    private static final String TAG = "SuitEnergy";

    private SuitEnergy() {
    }

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof SuitArmorItem armor ? armor.getEnergyCapacity() : 0;
    }

    public static int get(ItemStack stack) {
        if (!(stack.getItem() instanceof SuitArmorItem)) {
            return 0;
        }
        if (stack.getTag() == null || !stack.getTag().contains(TAG)) {
            return capacity(stack);
        }
        return stack.getTag().getInt(TAG);
    }

    public static void set(ItemStack stack, int energy) {
        stack.getOrCreateTag().putInt(TAG, Math.max(0, Math.min(capacity(stack), energy)));
    }

    /** @return how much was actually accepted. */
    public static int receive(ItemStack stack, int amount) {
        int current = get(stack);
        int accepted = Math.min(amount, capacity(stack) - current);
        if (accepted > 0) {
            set(stack, current + accepted);
        }
        return Math.max(0, accepted);
    }

    /** The stack that pays for an ability used by the piece in {@code user}: the chest battery if worn, else the piece itself. */
    public static ItemStack source(Player player, EquipmentSlot user) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof SuitArmorItem) {
            return chest;
        }
        ItemStack own = player.getItemBySlot(user);
        return own.getItem() instanceof SuitArmorItem ? own : ItemStack.EMPTY;
    }

    public static int available(Player player, EquipmentSlot user) {
        return get(source(player, user));
    }

    /** Drains all-or-nothing; creative players never pay. */
    public static boolean tryDrain(Player player, EquipmentSlot user, int amount) {
        ItemStack source = source(player, user);
        if (source.isEmpty()) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        int current = get(source);
        if (current < amount) {
            return false;
        }
        set(source, current - amount);
        return true;
    }
}
