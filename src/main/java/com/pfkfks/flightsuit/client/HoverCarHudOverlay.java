package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.car.HoverCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Dashboard while driving the hover car: speed and battery, just above the hotbar. */
public final class HoverCarHudOverlay implements IGuiOverlay {
    public static final HoverCarHudOverlay INSTANCE = new HoverCarHudOverlay();

    private HoverCarHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !(minecraft.player.getVehicle() instanceof HoverCarEntity car)) {
            return;
        }
        Font font = minecraft.font;
        int kmh = (int) Math.round(car.getDeltaMovement().horizontalDistance() * 20.0D * 3.6D);
        float ratio = car.getEnergy() / (float) HoverCarEntity.CAPACITY;
        int width = 120;
        int x = screenWidth / 2 - width / 2;
        int y = screenHeight - 72;
        graphics.fill(x - 3, y - 3, x + width + 3, y + 21, 0x66000000);
        Component speed = Component.translatable("hud.flightsuit.car_speed", kmh);
        graphics.drawString(font, speed, x, y, 0xFFFFE08A, true);
        graphics.fill(x, y + 11, x + width, y + 16, 0xFF10181D);
        graphics.fill(x, y + 11, x + Math.round(width * ratio), y + 16, ratio < 0.15F ? 0xFFFF6A4D : 0xFF5FE3FF);
        String fe = car.getEnergy() + " FE";
        graphics.drawString(font, fe, x + width - font.width(fe), y, 0xFFDDF6FF, false);
    }
}
