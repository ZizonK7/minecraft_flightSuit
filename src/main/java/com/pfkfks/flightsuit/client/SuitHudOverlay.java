package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Minimal suit-wearing HUD (DESIGN.md 4-1 "전투 HUD", first pass): suit name, which pieces are on,
 * battery, and what the suit can do right now. The glasses-only command HUD comes with the EDITH
 * glasses in M2.
 */
public final class SuitHudOverlay implements IGuiOverlay {
    public static final SuitHudOverlay INSTANCE = new SuitHudOverlay();

    private static final int CYAN = 0xFF5FE3FF;
    private static final int DIM = 0xFF3A4A55;
    private static final int TEXT = 0xFFDDF6FF;
    private static final int WARN = 0xFFFF6A4D;

    private SuitHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        WornSuit worn = WornSuit.of(player);
        if (!worn.any()) {
            return;
        }
        Font font = minecraft.font;
        int x = 6;
        int y = 6;

        SuitType type = WornSuit.primaryType(player);
        String name = worn.fullSet() ? type.hudName() : type.hudName() + " (PARTIAL)";
        graphics.fill(x - 3, y - 3, x + 124, y + 42, 0x66000000);
        graphics.drawString(font, name, x, y, CYAN, true);

        // H C L B piece lights.
        int px = x;
        String[] labels = {"H", "C", "L", "B"};
        boolean[] on = {worn.helmet(), worn.chest(), worn.legs(), worn.boots()};
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(font, labels[i], px, y + 11, on[i] ? CYAN : DIM, false);
            px += 9;
        }

        // Battery: chest reactor if worn, else whichever piece is on.
        EquipmentSlot sourceSlot = worn.chest() ? EquipmentSlot.CHEST
                : worn.boots() ? EquipmentSlot.FEET : worn.legs() ? EquipmentSlot.LEGS : EquipmentSlot.HEAD;
        ItemStack source = SuitEnergy.source(player, sourceSlot);
        int energy = SuitEnergy.get(source);
        int capacity = Math.max(1, SuitEnergy.capacity(source));
        float ratio = energy / (float) capacity;
        int barX = x + 40;
        int barY = y + 12;
        int barW = 78;
        graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF10181D);
        graphics.fill(barX, barY, barX + Math.round(barW * ratio), barY + 5, ratio < 0.15F ? WARN : CYAN);
        graphics.drawString(font, energy + " FE", barX, y + 20, TEXT, false);

        graphics.drawString(font, status(player, worn, energy), x, y + 31, energy <= 0 ? WARN : TEXT, false);
    }

    private static Component status(LocalPlayer player, WornSuit worn, int energy) {
        if (CinematicCamera.isActive()) {
            return Component.translatable("hud.flightsuit.assembling");
        }
        if (energy <= 0) {
            return Component.translatable("hud.flightsuit.no_power");
        }
        FlightPose pose = SuitAnimator.poseOf(player);
        if (pose == FlightPose.BOOST) {
            return Component.translatable("hud.flightsuit.boost");
        }
        if (pose == FlightPose.HOVER) {
            return Component.translatable("hud.flightsuit.hover");
        }
        if (worn.fullSet()) {
            return Component.translatable("hud.flightsuit.flight_ready");
        }
        if (worn.boots()) {
            return Component.translatable("hud.flightsuit.thrusters");
        }
        return Component.translatable("hud.flightsuit.partial");
    }
}
