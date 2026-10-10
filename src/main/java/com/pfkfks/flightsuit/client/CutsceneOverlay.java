package com.pfkfks.flightsuit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * The cutscene's screen (M17): black bars top and bottom (12% of the height each), the subtitle centred in the lower
 * bar with the speaker's name in their colour (gold for our side, red for the enemy, none for narration), and a small
 * "jump: skip" hint.
 */
public final class CutsceneOverlay implements IGuiOverlay {
    public static final CutsceneOverlay INSTANCE = new CutsceneOverlay();

    private CutsceneOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!CutsceneClient.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int bar = Math.round(height * 0.12F);
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);
        Component hint = Component.translatable("cutscene.flightsuit.skip", minecraft.options.keyJump.getTranslatedKeyMessage());
        graphics.drawString(font, hint, width - font.width(hint) - 6, Math.max(2, bar / 2 - 4), 0xFF8A8A8A, false);
        Component speaker = CutsceneClient.speaker();
        Component text = CutsceneClient.text();
        if (text.getString().isEmpty()) {
            return;
        }
        Component line = speaker.getString().isEmpty() ? text : Component.empty().append(speaker).append(": ").append(text);
        List<FormattedCharSequence> rows = font.split(line, Math.max(100, (int) (width * 0.8F)));
        int y = height - bar + Math.max(2, (bar - rows.size() * 10) / 2);
        for (FormattedCharSequence row : rows) {
            graphics.drawString(font, row, (width - font.width(row)) / 2, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }
}
