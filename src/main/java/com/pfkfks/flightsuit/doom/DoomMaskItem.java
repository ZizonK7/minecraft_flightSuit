package com.pfkfks.flightsuit.doom;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Doom's mask (DESIGN.md 4-17): what he left behind in Castle Doom. A trophy you can wear - it goes on the head like a
 * carved pumpkin (right-click), drawn by its 3D item model (models/item/doom_mask.json, the "head" display).
 */
public class DoomMaskItem extends Item implements Equipable {
    public DoomMaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }
}
