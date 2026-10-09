package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.EdithAlertS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * EDITH's alert banner (DESIGN.md 4-15): a framed box at the top of the screen that flashes in, says what is
 * happening and where - distance and compass bearing in this dimension, or which dimension it is in - and
 * fades after a few seconds.
 */
public final class EdithAlertOverlay implements IGuiOverlay {
    public static final EdithAlertOverlay INSTANCE = new EdithAlertOverlay();

    private static final int SHOW_TICKS = 160;

    private static EdithAlertS2CPacket current;
    private static long shownAt;

    private EdithAlertOverlay() {
    }

    public static void show(EdithAlertS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        current = packet;
        shownAt = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1.6F));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F));
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (current == null || player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        float age = minecraft.level.getGameTime() - shownAt + partialTick;
        if (age > SHOW_TICKS || age < 0.0F) {
            current = null;
            return;
        }
        float alpha = age < 5.0F ? age / 5.0F : (age > SHOW_TICKS - 20 ? (SHOW_TICKS - age) / 20.0F : 1.0F);
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);
        if (alpha * 255.0F < 8.0F) {
            // Font treats an alpha under 4 as opaque - skip the faintest frames instead of flashing.
            return;
        }
        Font font = minecraft.font;
        Component where = where(player);
        int boxWidth = Math.max(180, Math.max(font.width(current.title), Math.max(font.width(current.detail), font.width(where))) + 24);
        int left = (width - boxWidth) / 2;
        int top = 14;
        int boxHeight = 44;
        boolean blink = ((int) (age / 6.0F)) % 2 == 0 && age < 60.0F;
        int frame = argb((int) (alpha * (blink ? 255 : 170)), current.color);
        graphics.fill(left, top, left + boxWidth, top + boxHeight, argb((int) (alpha * 150), 0x0A0E12));
        graphics.fill(left, top, left + boxWidth, top + 1, frame);
        graphics.fill(left, top + boxHeight - 1, left + boxWidth, top + boxHeight, frame);
        graphics.fill(left, top, left + 1, top + boxHeight, frame);
        graphics.fill(left + boxWidth - 1, top, left + boxWidth, top + boxHeight, frame);
        graphics.fill(left + 1, top + 1, left + 4, top + boxHeight - 1, frame);
        int text = argb((int) (alpha * 255), 0xFFFFFF);
        graphics.drawString(font, Component.literal("EDITH"), left + 8, top + 4, argb((int) (alpha * 255), current.color), false);
        graphics.drawString(font, current.title, left + 8 + font.width("EDITH") + 6, top + 4, text, false);
        graphics.drawString(font, current.detail, left + 8, top + 17, argb((int) (alpha * 230), 0xD8E6EC), false);
        graphics.drawString(font, where, left + 8, top + 30, argb((int) (alpha * 200), 0x8FA9B5), false);
    }

    private static Component where(LocalPlayer player) {
        if (!current.hasPos) {
            return Component.empty();
        }
        String here = player.level().dimension().location().toString();
        if (!here.equals(current.dimension)) {
            return Component.translatable("edith.flightsuit.other_dimension", current.dimension);
        }
        double dx = current.pos.getX() + 0.5D - player.getX();
        double dz = current.pos.getZ() + 0.5D - player.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        // Minecraft yaw: 0 = south (+z), 90 = west; compass from north, clockwise.
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = Math.floorMod((int) Math.round(bearing / 45.0D), 8);
        return Component.translatable("edith.flightsuit.where", distance, Component.translatable("edith.flightsuit.dir." + sector));
    }

    private static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
