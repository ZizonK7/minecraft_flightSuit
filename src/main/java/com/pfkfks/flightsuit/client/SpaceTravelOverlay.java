package com.pfkfks.flightsuit.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Crossing space (DESIGN.md 4-16): the screen fades to black, stars stream past from the middle and the
 * destination is written across it, then it fades back in over the far side. Timed on the wall clock - the
 * world (and dimension) changes underneath it.
 */
public final class SpaceTravelOverlay implements IGuiOverlay {
    public static final SpaceTravelOverlay INSTANCE = new SpaceTravelOverlay();
    private static final int STARS = 140;

    private static Component destination;
    private static long startedAt;
    private static long lengthMs;

    private SpaceTravelOverlay() {
    }

    public static void show(Component where, int ticks) {
        destination = where;
        startedAt = Util.getMillis();
        lengthMs = ticks * 50L;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.6F));
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (destination == null) {
            return;
        }
        long age = Util.getMillis() - startedAt;
        if (age > lengthMs || age < 0) {
            destination = null;
            return;
        }
        float t = age / (float) lengthMs;
        // In over the first 15%, hold, out over the last 25%.
        float alpha = t < 0.15F ? t / 0.15F : (t > 0.75F ? (1.0F - t) / 0.25F : 1.0F);
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);
        int a = (int) (alpha * 255.0F);
        if (a < 8) {
            return;
        }
        graphics.fill(0, 0, width, height, a << 24);
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float seconds = age / 1000.0F;
        for (int i = 0; i < STARS; i++) {
            // Each star: a fixed direction, flying outwards and looping.
            double angle = (i * 2.399963D) % (Math.PI * 2.0D);
            float speed = 0.25F + (i % 7) * 0.08F;
            float dist = ((seconds * speed + (i * 0.6180339F) % 1.0F) % 1.0F);
            float r = dist * dist * Math.max(width, height) * 0.75F;
            int x = (int) (cx + Math.cos(angle) * r);
            int y = (int) (cy + Math.sin(angle) * r);
            int size = dist > 0.6F ? 2 : 1;
            int bright = (int) (alpha * (120 + 135 * dist));
            graphics.fill(x, y, x + size, y + size, (bright << 24) | 0xFFFFFF);
        }
        Font font = Minecraft.getInstance().font;
        Component line = Component.translatable("space.flightsuit.crossing", destination);
        int text = (a << 24) | 0xFFFFFF;
        graphics.drawString(font, line, (int) (cx - font.width(line) / 2.0F), (int) cy - 4, text, true);
    }
}
