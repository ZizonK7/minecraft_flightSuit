package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.CardDuelC2SPacket;
import com.pfkfks.flightsuit.network.CardDuelS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.suit.CardDuel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The card duel (CardDuel) as a 2D stage the phantom and the monster are dragged into: a storm of cards
 * sweeps the world away, the two face each other under spotlights across a felt table, cards fly off the deck
 * as they're dealt, and when it's decided the storm sweeps back and the world returns.
 * Escape can't walk out of a hand in progress - only hit, stand, or the bottom deal.
 */
public class CardDuelScreen extends Screen {
    private static final int CARD_W = 30;
    private static final int CARD_H = 42;
    private static final int CARD_GAP = 6;
    private static final float DEAL_TICKS = 5.0F;
    private static final int STORM_TICKS = 14;
    private static final int OUTRO_TICKS = 12;
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    private static final int GOLD = 0xFFE0AE3A;
    private static final int CREAM = 0xFFF2E8D0;
    private static final int RED = 0xFFC8102E;
    private static final int INK = 0xFF16121C;

    private CardDuelS2CPacket state;
    /** Screen ticks when each card in each hand landed on the table (for the deal animation). */
    private final List<Float> mineDealt = new ArrayList<>();
    private final List<Float> theirsDealt = new ArrayList<>();
    private float ticks;
    private float resultAt = -1.0F;
    private float outroAt = -1.0F;
    /** A move is on its way to the server: don't send another until the table updates. */
    private boolean waiting;

    public CardDuelScreen(CardDuelS2CPacket state) {
        super(Component.translatable("screen.flightsuit.duel"));
        this.state = state;
        noteDeals();
        noteResult();
    }

    public static void handle(CardDuelS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.result == CardDuel.CANCELLED) {
            if (minecraft.screen instanceof CardDuelScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (minecraft.screen instanceof CardDuelScreen screen) {
            screen.update(packet);
        } else {
            minecraft.setScreen(new CardDuelScreen(packet));
        }
    }

    private void update(CardDuelS2CPacket packet) {
        this.state = packet;
        this.waiting = false;
        noteDeals();
        noteResult();
        rebuildWidgets();
    }

    /** New cards start flying from the deck now (once the stage is up), one after another. */
    private void noteDeals() {
        float start = Math.max(ticks, STORM_TICKS);
        int fresh = 0;
        while (mineDealt.size() < state.mine.size()) {
            mineDealt.add(start + 3.0F * fresh++);
        }
        while (theirsDealt.size() < state.theirs.size()) {
            theirsDealt.add(start + 3.0F * fresh++);
        }
    }

    /** The verdict shows once the last card has landed. */
    private void noteResult() {
        if (state.result == CardDuel.PLAYING || resultAt >= 0.0F) {
            return;
        }
        float last = ticks;
        for (float dealt : mineDealt) {
            last = Math.max(last, dealt + DEAL_TICKS);
        }
        for (float dealt : theirsDealt) {
            last = Math.max(last, dealt + DEAL_TICKS);
        }
        resultAt = last + 2.0F;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return state.result != CardDuel.PLAYING;
    }

    @Override
    public void tick() {
        ticks++;
        if (outroAt >= 0.0F && ticks - outroAt >= OUTRO_TICKS) {
            onClose();
        } else if (resultAt >= 0.0F && outroAt < 0.0F && ticks - resultAt > 80.0F) {
            outroAt = ticks;
        }
    }

    // ---------------------------------------------------------------- layout

    private int tableLeft() {
        return width / 2 - Math.min(170, width / 2 - 90);
    }

    private int tableRight() {
        return width / 2 + Math.min(170, width / 2 - 90);
    }

    private int tableTop() {
        return height / 2 - 78;
    }

    private int tableBottom() {
        return height / 2 + 62;
    }

    private int deckX() {
        return tableLeft() + 14;
    }

    private int deckY() {
        return height / 2 - CARD_H / 2 - 8;
    }

    @Override
    protected void init() {
        if (state.result != CardDuel.PLAYING) {
            return;
        }
        int y = tableBottom() + 10;
        int cx = width / 2;
        if (state.peekTop != CardDuel.FACE_DOWN) {
            addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.duel.take_top"), b -> send(CardDuel.TAKE_TOP))
                    .bounds(cx - 102, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.duel.take_bottom"), b -> send(CardDuel.TAKE_BOTTOM))
                    .bounds(cx + 2, y, 100, 20).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.duel.hit"), b -> send(CardDuel.HIT))
                .bounds(cx - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.duel.stand"), b -> send(CardDuel.STAND))
                .bounds(cx - 50, y, 100, 20).build());
        Button sleight = Button.builder(Component.translatable("screen.flightsuit.duel.sleight"), b -> send(CardDuel.SLEIGHT))
                .bounds(cx + 54, y, 100, 20).build();
        sleight.active = !state.sleightUsed;
        addRenderableWidget(sleight);
    }

    private void send(byte action) {
        if (!waiting && state.result == CardDuel.PLAYING && ticks > STORM_TICKS) {
            waiting = true;
            ModNetwork.sendToServer(new CardDuelC2SPacket(action));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (resultAt >= 0.0F && outroAt < 0.0F && ticks - resultAt > 10.0F) {
            outroAt = ticks;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float t = ticks + partialTick;
        // The card storm sweeps across and, at the moment it covers the whole screen, the world behind becomes
        // the stage (and on the way out, the stage becomes the world again).
        float in = Math.min(1.0F, t / STORM_TICKS);
        float out = outroAt < 0.0F ? 0.0F : Math.min(1.0F, (t - outroAt) / OUTRO_TICKS);
        if (in >= 0.5F && out < 0.5F) {
            renderStage(graphics);
            renderDuelists(graphics);
            renderTable(graphics, t);
            super.render(graphics, mouseX, mouseY, partialTick);
            renderResult(graphics, t);
        }
        if (in < 1.0F) {
            renderStorm(graphics, in, true);
        } else if (out > 0.0F) {
            renderStorm(graphics, out, false);
        }
    }

    private void renderStage(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xFF241038, 0xFF07040E);
        int floor = height - height / 4;
        graphics.fillGradient(0, floor, width, height, 0xFF1A0C26, 0xFF050208);
        graphics.fill(0, floor, width, floor + 1, 0xFF8A6A2A);
        // Curtain folds along the top.
        for (int x = 0; x < width; x += 24) {
            graphics.fillGradient(x, 0, x + 12, 36, 0xFF5A0E1E, 0xFF2A0610);
            graphics.fillGradient(x + 12, 0, x + 24, 30, 0xFF420A16, 0xFF20050C);
        }
        graphics.fill(0, 36, width, 38, GOLD);
        // Spotlights on the two duelists.
        for (int cx : new int[]{width / 7, width - width / 7}) {
            for (int i = 0; i < 6; i++) {
                int half = 14 + i * 6;
                graphics.fillGradient(cx - half, 38, cx + half, floor + 6, 0x00FFF2C0, 0x14FFF2C0);
            }
        }
    }

    private void renderDuelists(GuiGraphics graphics) {
        int floor = height - height / 4;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            int x = width / 7;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, floor, 42, -60.0F, -20.0F, minecraft.player);
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.you"), x, floor + 8, GOLD);
        }
        int x = width - width / 7;
        if (minecraft.level != null && minecraft.level.getEntity(state.opponentId) instanceof LivingEntity opponent) {
            float size = Math.max(opponent.getBbHeight(), opponent.getBbWidth() * 1.2F);
            int scale = (int) Mth.clamp(80.0F / size, 12.0F, 60.0F);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, floor, scale, 60.0F, -20.0F, opponent);
        }
        graphics.drawCenteredString(font, state.opponentName, x, floor + 8, state.boss ? RED : CREAM);
        graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.vs", state.opponentName), width / 2, 44, CREAM);
    }

    private void renderTable(GuiGraphics graphics, float t) {
        int left = tableLeft();
        int right = tableRight();
        int top = tableTop();
        int bottom = tableBottom();
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFF3A2410);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, GOLD);
        graphics.fillGradient(left, top, right, bottom, 0xFF1E6B3A, 0xFF0E3E20);

        // The deck.
        for (int i = 2; i >= 0; i--) {
            drawBack(graphics, deckX() + i, deckY() - i);
        }
        int rowX = deckX() + CARD_W + 20;
        int theirsY = top + 10;
        int mineY = bottom - CARD_H - 10;
        drawHand(graphics, state.theirs, theirsDealt, rowX, theirsY, t);
        drawHand(graphics, state.mine, mineDealt, rowX, mineY, t);

        boolean hidden = state.theirs.contains(CardDuel.FACE_DOWN);
        String theirTotal = CardDuel.total(state.theirs) + (hidden ? " + ?" : "");
        graphics.drawString(font, Component.translatable("screen.flightsuit.duel.total", theirTotal), rowX, theirsY + CARD_H + 3, CREAM, false);
        int mine = CardDuel.total(state.mine);
        graphics.drawString(font, Component.translatable("screen.flightsuit.duel.total", mine), rowX, mineY - 11,
                mine > 21 ? RED : mine == 21 ? GOLD : CREAM, false);

        if (state.peekTop != CardDuel.FACE_DOWN) {
            // Dealing from the bottom: both candidates turned up beside the deck.
            int px = right - 2 * CARD_W - 18;
            int py = height / 2 - CARD_H / 2 - 8;
            drawFace(graphics, px, py, state.peekTop);
            drawFace(graphics, px + CARD_W + 8, py, state.peekBottom);
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.take_top"), px + CARD_W / 2, py - 10, GOLD);
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.take_bottom"), px + CARD_W * 3 / 2 + 8, py - 10, GOLD);
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.sleight_hint"), width / 2, bottom + 34, 0xFFB8A8C8);
        } else if (state.result == CardDuel.PLAYING) {
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.hint"), width / 2, bottom + 34, 0xFFB8A8C8);
        }
    }

    /** A hand laid out left to right; each card flies over from the deck and flips as it lands. */
    private void drawHand(GuiGraphics graphics, List<Byte> hand, List<Float> dealt, int x, int y, float t) {
        for (int i = 0; i < hand.size(); i++) {
            float p = Mth.clamp((t - dealt.get(i)) / DEAL_TICKS, 0.0F, 1.0F);
            if (p <= 0.0F) {
                continue;
            }
            float ease = 1.0F - (1.0F - p) * (1.0F - p);
            int cx = (int) Mth.lerp(ease, deckX(), x + i * (CARD_W + CARD_GAP));
            int cy = (int) Mth.lerp(ease, deckY(), y);
            byte card = hand.get(i);
            if (p < 1.0F || card == CardDuel.FACE_DOWN) {
                drawBack(graphics, cx, cy);
            } else {
                drawFace(graphics, cx, cy, card);
            }
        }
    }

    private void drawBack(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xFF2A1450);
        graphics.renderOutline(x, y, CARD_W, CARD_H, GOLD);
        graphics.renderOutline(x + 3, y + 3, CARD_W - 6, CARD_H - 6, 0xFF8A6A2A);
        for (int i = 0; i < 4; i++) {
            int cy = y + 9 + i * 8;
            graphics.fill(x + CARD_W / 2 - 1, cy - 2, x + CARD_W / 2 + 1, cy + 2, GOLD);
            graphics.fill(x + CARD_W / 2 - 3, cy - 1, x + CARD_W / 2 + 3, cy + 1, GOLD);
        }
    }

    private void drawFace(GuiGraphics graphics, int x, int y, byte card) {
        int rank = card % 13;
        int suit = card / 13;
        int color = suit == 1 || suit == 2 ? RED : INK;
        graphics.fill(x, y, x + CARD_W, y + CARD_H, 0xFFF8F6F0);
        graphics.renderOutline(x, y, CARD_W, CARD_H, 0xFF3A3340);
        graphics.drawString(font, RANKS[rank], x + 3, y + 3, color, false);
        graphics.drawString(font, SUITS[suit], x + 3, y + 12, color, false);
        graphics.pose().pushPose();
        graphics.pose().translate(x + CARD_W / 2.0F, y + CARD_H / 2.0F + 3.0F, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawString(font, SUITS[suit], -font.width(SUITS[suit]) / 2, -4, color, false);
        graphics.pose().popPose();
    }

    private void renderResult(GuiGraphics graphics, float t) {
        if (resultAt < 0.0F || t < resultAt + 6.0F) {
            return;
        }
        boolean win = state.result == CardDuel.WIN;
        Component banner = Component.translatable(win ? (state.boss ? "screen.flightsuit.duel.win_boss" : "screen.flightsuit.duel.win")
                : "screen.flightsuit.duel.lose");
        Component detail = null;
        if (win && state.mine.size() == 2 && CardDuel.total(state.mine) == 21) {
            detail = Component.translatable("screen.flightsuit.duel.blackjack");
        } else if (!win && CardDuel.total(state.mine) > 21) {
            detail = Component.translatable("screen.flightsuit.duel.bust");
        }
        float grow = Math.min(1.0F, (t - resultAt - 6.0F) / 6.0F);
        int y = height / 2 - 20;
        graphics.fill(0, y - 14, width, y + 34, (int) (0xA0 * grow) << 24);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0.0F);
        float scale = 1.0F + 1.5F * grow;
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, banner, 0, 0, win ? GOLD : RED);
        graphics.pose().popPose();
        if (detail != null) {
            graphics.drawCenteredString(font, detail, width / 2, y + 20, CREAM);
        }
        if (t > resultAt + 16.0F) {
            graphics.drawCenteredString(font, Component.translatable("screen.flightsuit.duel.leave"), width / 2, tableBottom() + 34, 0xFFB8A8C8);
        }
    }

    /**
     * A train of overlapping card backs per row, crossing the screen: it fully covers it halfway through
     * (p = 0.5) and is gone by the end. In: right to left; out: left to right.
     */
    private void renderStorm(GuiGraphics graphics, float p, boolean in) {
        int step = 20;
        int length = (int) (width * 1.25F);
        int rows = height / (CARD_H - 14) + 2;
        for (int row = 0; row < rows; row++) {
            int lead = (int) (p * (width + length + CARD_W * 2)) + (row * 7 % 3) * 6;
            int y = row * (CARD_H - 14) - 10;
            for (int k = 0; k * step < length; k++) {
                int x = in ? width - lead + k * step : lead - k * step - CARD_W;
                if (x > -CARD_W && x < width) {
                    drawBack(graphics, x, y);
                }
            }
        }
    }
}
