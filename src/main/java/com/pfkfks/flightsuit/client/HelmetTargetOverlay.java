package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.suit.SuitEnergy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Comparator;
import java.util.List;

/**
 * Helmet sensor HUD (DESIGN.md 4-3 "헬멧 = 감각"): targeting brackets around every hostile mob within
 * range - through walls - with distance and a health bar. Close threats are red, the rest amber.
 * Needs a suit helmet with power.
 */
public final class HelmetTargetOverlay implements IGuiOverlay {
    public static final HelmetTargetOverlay INSTANCE = new HelmetTargetOverlay();

    private static final double RANGE = 32.0D;
    private static final int MAX_TARGETS = 24;
    private static final double CLOSE = 8.0D;
    private static final int RED = 0xFFFF4A3D;
    private static final int AMBER = 0xFFFFB347;

    private HelmetTargetOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui
                || !(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof com.pfkfks.flightsuit.suit.SuitArmorItem)
                || SuitEnergy.available(player, EquipmentSlot.HEAD) <= 0) {
            return;
        }
        List<Mob> targets = minecraft.level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                mob -> mob instanceof Enemy && mob.isAlive() && !mob.isInvisible());
        targets.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        Font font = minecraft.font;
        int drawn = 0;
        for (Mob mob : targets) {
            if (drawn >= MAX_TARGETS) {
                break;
            }
            Vec3 pos = mob.getPosition(partialTick);
            AABB box = mob.getBoundingBox().move(pos.subtract(mob.position()));
            Vec2 top = ClientRenderMatrixCache.worldToScreen(new Vec3(pos.x, box.maxY, pos.z), screenWidth, screenHeight);
            Vec2 bottom = ClientRenderMatrixCache.worldToScreen(new Vec3(pos.x, box.minY, pos.z), screenWidth, screenHeight);
            if (top == null || bottom == null) {
                continue;
            }
            drawn++;
            double distance = Math.sqrt(mob.distanceToSqr(player));
            int color = distance < CLOSE ? RED : AMBER;
            int height = Math.max(8, (int) (bottom.y - top.y));
            int width = Math.max(6, (int) (height * 0.6F));
            int cx = (int) top.x;
            int x0 = cx - width / 2;
            int y0 = (int) top.y;
            int x1 = x0 + width;
            int y1 = y0 + height;
            brackets(graphics, x0, y0, x1, y1, Math.max(3, width / 4), color);

            // Health bar under the brackets, then the distance.
            float health = Math.max(0.0F, Math.min(1.0F, mob.getHealth() / mob.getMaxHealth()));
            graphics.fill(x0, y1 + 2, x1, y1 + 4, 0xAA000000);
            graphics.fill(x0, y1 + 2, x0 + Math.round(width * health), y1 + 4, color);
            String label = (int) distance + "m";
            graphics.drawString(font, label, cx - font.width(label) / 2, y1 + 6, color, true);
        }
    }

    private static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, int len, int color) {
        // Top-left, top-right, bottom-left, bottom-right corners.
        g.fill(x0, y0, x0 + len, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + len, color);
        g.fill(x1 - len, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + len, color);
        g.fill(x0, y1 - 1, x0 + len, y1, color);
        g.fill(x0, y1 - len, x0 + 1, y1, color);
        g.fill(x1 - len, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - len, x1, y1, color);
    }
}
