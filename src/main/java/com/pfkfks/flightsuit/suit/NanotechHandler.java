package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Nanotech Mark 50 (DESIGN.md 4-4 Mark 50, 4-16): nanites knit the worn pieces back together - each damaged piece
 * mends a point every two seconds, and the chest battery trickles back while it's not in use.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class NanotechHandler {
    private static final int REPAIR_INTERVAL = 40;
    private static final int RECHARGE = 20;

    private NanotechHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % REPAIR_INTERVAL != 0) {
            return;
        }
        boolean any = false;
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem armor && armor.getSuitType() == SuitType.NANO_MK50) {
                any = true;
                if (stack.isDamaged()) {
                    stack.setDamageValue(stack.getDamageValue() - 1);
                }
            }
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (any && chest.getItem() instanceof SuitArmorItem armor && armor.getSuitType() == SuitType.NANO_MK50) {
            SuitEnergy.receive(chest, RECHARGE);
        }
    }
}
