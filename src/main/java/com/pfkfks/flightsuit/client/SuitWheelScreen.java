package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitWheelC2SPacket;
import com.pfkfks.flightsuit.suit.SuitWheel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Suit wheel (DESIGN.md 4-1): every reachable suit as a card on a ring, House Party Protocol in the middle.
 * Click = deploy as companion, Shift+click = swap into it, right click = make that station the main one.
 */
public class SuitWheelScreen extends Screen {
    private static final int CARD_W = 118;
    private static final int CARD_H = 30;
    private static final int RING_RADIUS = 96;
    private static final int CENTER_W = 92;
    private static final int CENTER_H = 30;

    private static final int CYAN = 0xFF5FE3FF;
    private static final int TEXT = 0xFFDDF6FF;
    private static final int DIM = 0xFF8FA9B5;
    private static final int WARN = 0xFFFF6A4D;
    private static final int GOLD = 0xFFFFC94D;

    private List<SuitWheel.Entry> entries;

    public SuitWheelScreen(List<SuitWheel.Entry> entries) {
        super(Component.translatable("screen.flightsuit.suit_wheel"));
        this.entries = entries;
    }

    public void setEntries(List<SuitWheel.Entry> entries) {
        this.entries = entries;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cardX(int i) {
        double angle = -Math.PI / 2.0D + 2.0D * Math.PI * i / Math.max(1, entries.size());
        return width / 2 + (int) (Math.cos(angle) * RING_RADIUS * 1.5D) - CARD_W / 2;
    }

    private int cardY(int i) {
        double angle = -Math.PI / 2.0D + 2.0D * Math.PI * i / Math.max(1, entries.size());
        return height / 2 + (int) (Math.sin(angle) * RING_RADIUS) - CARD_H / 2;
    }

    private int hovered(double mouseX, double mouseY) {
        for (int i = 0; i < entries.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                return i;
            }
        }
        return -1;
    }

    private boolean overCenter(double mouseX, double mouseY) {
        return Math.abs(mouseX - width / 2.0D) < CENTER_W / 2.0D && Math.abs(mouseY - height / 2.0D) < CENTER_H / 2.0D;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        int cy = height / 2;

        boolean center = overCenter(mouseX, mouseY);
        frame(graphics, cx - CENTER_W / 2, cy - CENTER_H / 2, CENTER_W, CENTER_H, center ? GOLD : 0xFF4A3A10);
        graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.house_party"), cx, cy - 10, GOLD);
        graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.house_party_sub", SuitWheel.CONTROL_CAPACITY), cx, cy + 2, DIM);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.no_suits"), cx, cy + 40, WARN);
        }
        int hover = hovered(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++) {
            SuitWheel.Entry entry = entries.get(i);
            int x = cardX(i);
            int y = cardY(i);
            frame(graphics, x, y, CARD_W, CARD_H, i == hover ? CYAN : 0xFF24414D);
            String name = (entry.main() ? "★ " : "") + entry.name();
            graphics.drawString(font, font.plainSubstrByWidth(name, CARD_W - 8), x + 4, y + 4, entry.broken() ? WARN : TEXT, false);
            graphics.drawString(font, detail(entry), x + 4, y + 16, entry.broken() ? WARN : DIM, false);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.wheel_hint"), cx, height - 24, DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component detail(SuitWheel.Entry entry) {
        Component where = switch (entry.kind()) {
            case SuitWheel.STATION -> Component.translatable("screen.flightsuit.source_station", entry.distance());
            case SuitWheel.COMPANION -> Component.translatable("screen.flightsuit.source_companion", entry.distance());
            default -> Component.translatable("screen.flightsuit.source_capsule");
        };
        return Component.translatable("screen.flightsuit.entry_detail", where, entry.charge(), entry.durability());
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int border) {
        graphics.fill(x, y, x + w, y + h, 0xCC0B1418);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overCenter(mouseX, mouseY)) {
            send(SuitWheel.ALL, (byte) 0, 0L);
            return true;
        }
        int index = hovered(mouseX, mouseY);
        if (index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        SuitWheel.Entry entry = entries.get(index);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (entry.kind() == SuitWheel.STATION) {
                send(SuitWheel.SET_MAIN, entry.kind(), entry.key());
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            send(hasShiftDown() ? SuitWheel.WEAR : SuitWheel.SUMMON, entry.kind(), entry.key());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(byte action, byte kind, long key) {
        ModNetwork.sendToServer(SuitWheelC2SPacket.act(action, kind, key));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.SUIT_WHEEL.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
