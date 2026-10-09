package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Endgame nano shield: a curved-looking wall of small amber hexagons held out in front of the player,
 * shimmering cell by cell. Drawn in world space for every shielding player (including yourself in first
 * person) with the additive "lightning" render type, so it glows and never hides what's behind it.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class ShieldRenderer {
    private static final double DISTANCE = 0.95D;
    private static final float CELL = 0.17F;
    /** Hex rings around the center cell (0 = just the center). */
    private static final int RINGS = 3;

    private ShieldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean drew = false;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!SuitAnimator.isShielding(player)) {
                continue;
            }
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 center = eye.add(0.0D, -0.3D, 0.0D).add(look.scale(DISTANCE));
            drawShield(event.getPoseStack(), buffers.getBuffer(RenderType.lightning()), center.subtract(camera), look,
                    player.tickCount + partialTick);
            drew = true;
        }
        if (drew) {
            buffers.endBatch(RenderType.lightning());
        }
    }

    private static void drawShield(PoseStack poseStack, VertexConsumer buffer, Vec3 center, Vec3 forward, float time) {
        Vec3 up = Math.abs(forward.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(up).normalize();
        Vec3 realUp = right.cross(forward).normalize();
        Matrix4f matrix = poseStack.last().pose();

        // Axial hex grid (pointy-top), rings of cells around the center.
        int index = 0;
        for (int q = -RINGS; q <= RINGS; q++) {
            for (int r = Math.max(-RINGS, -q - RINGS); r <= Math.min(RINGS, -q + RINGS); r++) {
                double x = CELL * Math.sqrt(3.0D) * (q + r / 2.0D);
                double y = CELL * 1.5D * r;
                int ring = Math.max(Math.abs(q), Math.max(Math.abs(r), Math.abs(-q - r)));
                // Bow the wall toward the player at the edges so it reads as curved.
                double bow = -0.05D * ring * ring;
                Vec3 cellCenter = center.add(right.scale(x)).add(realUp.scale(y)).add(forward.scale(bow));
                float shimmer = 0.5F + 0.5F * (float) Math.sin(time * 0.35F + index * 1.7F);
                float alpha = 0.18F + 0.22F * shimmer - 0.03F * ring;
                hexagon(buffer, matrix, cellCenter, right, realUp, CELL * 0.9F, 1.0F, 0.62F + 0.2F * shimmer, 0.15F, alpha);
                index++;
            }
        }
    }

    /** A filled hexagon as six triangles (degenerate quads), emitted in both windings so it shows from either side. */
    private static void hexagon(VertexConsumer buffer, Matrix4f matrix, Vec3 c, Vec3 right, Vec3 up, float radius,
                                float red, float green, float blue, float alpha) {
        Vec3[] corners = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3.0D * i + Math.PI / 6.0D;
            corners[i] = c.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        }
        for (int i = 0; i < 6; i++) {
            Vec3 a = corners[i];
            Vec3 b = corners[(i + 1) % 6];
            // Center fainter than the rim, like a lit cell edge.
            vertex(buffer, matrix, c, red, green, blue, alpha * 0.4F);
            vertex(buffer, matrix, a, red, green, blue, alpha);
            vertex(buffer, matrix, b, red, green, blue, alpha);
            vertex(buffer, matrix, b, red, green, blue, alpha);

            vertex(buffer, matrix, c, red, green, blue, alpha * 0.4F);
            vertex(buffer, matrix, b, red, green, blue, alpha);
            vertex(buffer, matrix, a, red, green, blue, alpha);
            vertex(buffer, matrix, a, red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Math.max(0.0F, a)).endVertex();
    }
}
