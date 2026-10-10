package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.StoryGoalS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The story marker (after the 4th test round: "getting around the Dragon Ball story is hard"): at the top of the
 * screen, what's next and an arrow that turns with you towards it, with the distance. Gone once you're there
 * (the fight's boss bar takes the top of the screen then).
 */
public final class StoryGoalOverlay implements IGuiOverlay {
    public static final StoryGoalOverlay INSTANCE = new StoryGoalOverlay();
    /** Closer than this, the sight itself (and its light column) is in plain view. */
    private static final double HIDE_WITHIN = 40.0D;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static String dimension;
    private static BlockPos goal;
    private static Component label;

    private StoryGoalOverlay() {
    }

    public static void set(StoryGoalS2CPacket packet) {
        dimension = packet.active ? packet.dimension : null;
        goal = packet.pos;
        label = packet.label;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (dimension == null || player == null || mc.level == null || mc.options.hideGui
                || !mc.level.dimension().location().toString().equals(dimension)) {
            return;
        }
        double dx = goal.getX() + 0.5D - player.getX();
        double dz = goal.getZ() + 0.5D - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < HIDE_WITHIN) {
            return;
        }
        // Yaw 0 faces south (+z) and grows turning right, so the arrow is the goal's bearing minus where we face.
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(bearing - player.getYRot());
        String arrow = ARROWS[Math.floorMod(Math.round(relative / 45.0F), 8)];
        int dy = goal.getY() - Mth.floor(player.getY());
        Component line = Component.literal(arrow + " ").withStyle(s -> s.withColor(0xFFD84A).withBold(true))
                .append(label.copy().withStyle(s -> s.withColor(0xFFFFFF).withBold(false)))
                .append(Component.literal("  " + (int) distance + "m" + (Math.abs(dy) > 12 ? (dy > 0 ? " ▲" : " ▼") : ""))
                        .withStyle(s -> s.withColor(0x9FE8FF).withBold(false)));
        Font font = mc.font;
        int w = font.width(line);
        int x = (width - w) / 2;
        int y = 4;
        graphics.fill(x - 4, y - 2, x + w + 4, y + font.lineHeight + 1, 0x88000000);
        graphics.drawString(font, line, x, y, 0xFFFFFF, true);
    }
}
