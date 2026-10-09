package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.RemoteLinkS2CPacket;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Client view of the local player's remote link (see RemoteLink): state for the remote feed HUD. */
public final class RemoteLinkClient {
    public static final int TRANSITION_TICKS = 14;
    private static final int HIT_FLASH_TICKS = 6;

    private static boolean active;
    private static Vec3 bodyPos = Vec3.ZERO;
    private static String suitName = "";
    /** Counts down after the link opens or closes: the feed cuts in / out through static. */
    private static int transition;
    /** Total wear on the worn pieces last tick, to flash the feed when the suit takes a hit (-1 = unknown). */
    private static int lastWear = -1;
    private static int hitFlash;

    private RemoteLinkClient() {
    }

    public static void handle(RemoteLinkS2CPacket packet) {
        active = packet.active;
        bodyPos = packet.bodyPos;
        suitName = packet.suitName;
        transition = TRANSITION_TICKS;
        lastWear = -1;
        hitFlash = 0;
    }

    public static void tick(LocalPlayer player) {
        if (transition > 0) {
            transition--;
        }
        if (hitFlash > 0) {
            hitFlash--;
        }
        if (!active) {
            return;
        }
        int wear = 0;
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.getItem() instanceof SuitArmorItem) {
                wear += piece.getDamageValue();
            }
        }
        if (lastWear >= 0 && wear > lastWear) {
            hitFlash = HIT_FLASH_TICKS;
        }
        lastWear = wear;
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 bodyPos() {
        return bodyPos;
    }

    public static String suitName() {
        return suitName;
    }

    /** 1 right after the link switched, fading to 0. */
    public static float transition(float partialTick) {
        return transition <= 0 ? 0.0F : Math.max(0.0F, (transition - partialTick) / TRANSITION_TICKS);
    }

    public static float hitFlash(float partialTick) {
        return hitFlash <= 0 ? 0.0F : Math.max(0.0F, (hitFlash - partialTick) / HIT_FLASH_TICKS);
    }

    public static void reset() {
        active = false;
        transition = 0;
        lastWear = -1;
        hitFlash = 0;
    }
}
