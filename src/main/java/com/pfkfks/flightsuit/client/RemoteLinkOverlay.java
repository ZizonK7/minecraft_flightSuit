package com.pfkfks.flightsuit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Remote piloting reads as a camera feed: faint scanlines, corner brackets, a REMOTE banner with the distance
 * back to your body, a red edge when the suit takes a hit - and the feed cutting in or out through static
 * when the link opens or closes.
 */
public final class RemoteLinkOverlay implements IGuiOverlay {
    public static final RemoteLinkOverlay INSTANCE = new RemoteLinkOverlay();

    private static final int CYAN = 0x5FE3FF;
    private static final int DIM_TEXT = 0xFF8FA9B5;
    private static final int WARN = 0xFF4D3A;

    private final RandomSource noise = RandomSource.create();

    private RemoteLinkOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (RemoteLinkClient.isActive() && !minecraft.options.hideGui) {
            renderFeed(minecraft, player, graphics, partialTick, width, height);
        }
        float transition = RemoteLinkClient.transition(partialTick);
        if (transition > 0.0F) {
            renderStatic(minecraft, graphics, transition, width, height);
        }
    }

    private void renderFeed(Minecraft minecraft, LocalPlayer player, GuiGraphics graphics, float partialTick, int width, int height) {
        for (int y = 0; y < height; y += 3) {
            graphics.fill(0, y, width, y + 1, argb(0x0C, CYAN));
        }
        int bracket = argb(0xCC, CYAN);
        int inset = 10;
        int length = 18;
        corner(graphics, inset, inset, length, 1, 1, bracket);
        corner(graphics, width - inset, inset, length, -1, 1, bracket);
        corner(graphics, inset, height - inset, length, 1, -1, bracket);
        corner(graphics, width - inset, height - inset, length, -1, -1, bracket);

        float hit = RemoteLinkClient.hitFlash(partialTick);
        if (hit > 0.0F) {
            int red = argb((int) (hit * 0x90), WARN);
            graphics.fill(0, 0, width, 4, red);
            graphics.fill(0, height - 4, width, height, red);
            graphics.fill(0, 4, 4, height - 4, red);
            graphics.fill(width - 4, 4, width, height - 4, red);
        }

        Font font = minecraft.font;
        boolean blink = (player.tickCount / 10) % 2 == 0;
        Component title = Component.translatable("hud.flightsuit.remote.title", RemoteLinkClient.suitName());
        int cx = width / 2;
        int titleWidth = font.width(title);
        graphics.fill(cx - titleWidth / 2 - 14, 6, cx + titleWidth / 2 + 6, 30, 0x66000000);
        if (blink) {
            graphics.fill(cx - titleWidth / 2 - 10, 10, cx - titleWidth / 2 - 5, 15, argb(0xFF, WARN));
        }
        graphics.drawString(font, title, cx - titleWidth / 2, 9, argb(0xFF, CYAN), true);
        int distance = (int) player.position().distanceTo(RemoteLinkClient.bodyPos());
        graphics.drawCenteredString(font, Component.translatable("hud.flightsuit.remote.body", distance), cx, 20, DIM_TEXT);
    }

    /** Four L-shaped corner marks; dx/dy point inward. */
    private static void corner(GuiGraphics graphics, int x, int y, int length, int dx, int dy, int color) {
        int x2 = x + dx * length;
        int y2 = y + dy * length;
        graphics.fill(Math.min(x, x2), Math.min(y, y + dy * 2), Math.max(x, x2), Math.max(y, y + dy * 2), color);
        graphics.fill(Math.min(x, x + dx * 2), Math.min(y, y2), Math.max(x, x + dx * 2), Math.max(y, y2), color);
    }

    private void renderStatic(Minecraft minecraft, GuiGraphics graphics, float amount, int width, int height) {
        graphics.fill(0, 0, width, height, argb((int) (amount * 0xE0), 0x000000));
        for (int i = 0; i < 24; i++) {
            int y = noise.nextInt(Math.max(1, height));
            int x = noise.nextInt(Math.max(1, width));
            int w = 20 + noise.nextInt(Math.max(1, width / 2));
            graphics.fill(x - w / 2, y, x + w / 2, y + 1 + noise.nextInt(2), argb((int) (amount * (0x40 + noise.nextInt(0x80))), CYAN));
        }
        Component label = Component.translatable(RemoteLinkClient.isActive() ? "hud.flightsuit.remote.link_up" : "hud.flightsuit.remote.link_down");
        graphics.drawCenteredString(minecraft.font, label, width / 2, height / 2 - 4, argb(Math.max(0x10, (int) (amount * 0xFF)), CYAN));
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
