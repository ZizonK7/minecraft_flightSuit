package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.cleaner.CleanerRobotEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Robot vacuum drawn directly in code (ported from minebutler's guard cleaner): a low white octagonal puck
 * with a warm-white top plate, gray rim, black front intake, a top button and two sensor dots. It should
 * read as "white/gray robot vacuum" first - keep accent colors from taking over the silhouette.
 */
public class CleanerRobotRenderer extends EntityRenderer<CleanerRobotEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private static final RenderType BODY = RenderType.entityCutoutNoCull(TEXTURE);

    public CleanerRobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(CleanerRobotEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Docked: a slow "charging" breathe; working: sits on the floor.
        double bob = entity.isDocked() ? 0.01D + Math.sin((entity.tickCount + partialTick) * 0.1D) * 0.01D : 0.0D;
        poseStack.translate(0.0D, bob + 0.02D, 0.0D);
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        VertexConsumer consumer = buffer.getBuffer(BODY);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        octagonalPrism(consumer, matrix, normal, packedLight, 0.44F, 0.18F,
                new int[]{255, 255, 250, 255}, new int[]{205, 207, 202, 255});
        topPlate(consumer, matrix, normal, packedLight);
        frontPanel(consumer, matrix, normal, packedLight);
        topButton(consumer, matrix, normal, packedLight);
        sensorDot(consumer, matrix, normal, packedLight, -0.18F);
        sensorDot(consumer, matrix, normal, packedLight, 0.18F);
        outline(buffer.getBuffer(RenderType.lines()), matrix, normal, 0.47F, 0.23F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CleanerRobotEntity entity) {
        return TEXTURE;
    }

    private static void octagonalPrism(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight,
                                       float radius, float height, int[] topColor, int[] sideColor) {
        float[][] points = octagon(radius);
        for (int i = 0; i < points.length; i++) {
            float[] a = points[i];
            float[] b = points[(i + 1) % points.length];
            vertex(consumer, matrix, normal, packedLight, a[0], height, a[1], topColor);
            vertex(consumer, matrix, normal, packedLight, b[0], height, b[1], topColor);
            vertex(consumer, matrix, normal, packedLight, 0.0F, height + 0.015F, 0.0F, topColor);
            vertex(consumer, matrix, normal, packedLight, 0.0F, height + 0.015F, 0.0F, topColor);

            vertex(consumer, matrix, normal, packedLight, a[0], 0.0F, a[1], sideColor);
            vertex(consumer, matrix, normal, packedLight, a[0], height, a[1], sideColor);
            vertex(consumer, matrix, normal, packedLight, b[0], height, b[1], sideColor);
            vertex(consumer, matrix, normal, packedLight, b[0], 0.0F, b[1], sideColor);
        }
    }

    private static float[][] octagon(float radius) {
        float[][] points = new float[8][2];
        for (int i = 0; i < points.length; i++) {
            double angle = Math.PI / 8.0D + i * Math.PI / 4.0D;
            points[i][0] = (float) Math.sin(angle) * radius;
            points[i][1] = (float) Math.cos(angle) * radius;
        }
        return points;
    }

    private static void frontPanel(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight) {
        int[] black = {12, 16, 18, 255};
        quad(consumer, matrix, normal, packedLight,
                -0.21F, 0.055F, -0.42F, 0.21F, 0.055F, -0.42F, 0.21F, 0.135F, -0.445F, -0.21F, 0.135F, -0.445F, black);
    }

    private static void topPlate(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight) {
        int[] warmWhite = {255, 255, 252, 255};
        int[] rimGray = {158, 162, 158, 255};
        quad(consumer, matrix, normal, packedLight,
                -0.28F, 0.207F, -0.23F, 0.28F, 0.207F, -0.23F, 0.28F, 0.207F, 0.26F, -0.28F, 0.207F, 0.26F, warmWhite);
        quad(consumer, matrix, normal, packedLight,
                -0.32F, 0.205F, -0.28F, 0.32F, 0.205F, -0.28F, 0.28F, 0.205F, -0.23F, -0.28F, 0.205F, -0.23F, rimGray);
        quad(consumer, matrix, normal, packedLight,
                -0.32F, 0.205F, 0.31F, -0.28F, 0.205F, 0.26F, 0.28F, 0.205F, 0.26F, 0.32F, 0.205F, 0.31F, rimGray);
    }

    private static void topButton(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight) {
        int[] gray = {116, 120, 118, 255};
        quad(consumer, matrix, normal, packedLight,
                -0.09F, 0.212F, -0.03F, 0.09F, 0.212F, -0.03F, 0.09F, 0.212F, 0.11F, -0.09F, 0.212F, 0.11F, gray);
    }

    private static void sensorDot(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight, float x) {
        int[] dark = {42, 46, 48, 255};
        quad(consumer, matrix, normal, packedLight,
                x - 0.045F, 0.214F, -0.18F, x + 0.045F, 0.214F, -0.18F, x + 0.045F, 0.214F, -0.11F, x - 0.045F, 0.214F, -0.11F, dark);
    }

    private static void outline(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float radius, float y) {
        float[][] points = octagon(radius);
        for (int i = 0; i < points.length; i++) {
            float[] a = points[i];
            float[] b = points[(i + 1) % points.length];
            consumer.vertex(matrix, a[0], y, a[1]).color(150, 154, 150, 255).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
            consumer.vertex(matrix, b[0], y, b[1]).color(150, 154, 150, 255).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int[] color) {
        vertex(consumer, matrix, normal, packedLight, x1, y1, z1, color);
        vertex(consumer, matrix, normal, packedLight, x2, y2, z2, color);
        vertex(consumer, matrix, normal, packedLight, x3, y3, z3, color);
        vertex(consumer, matrix, normal, packedLight, x4, y4, z4, color);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int packedLight,
                               float x, float y, float z, int[] color) {
        consumer.vertex(matrix, x, y, z)
                .color(color[0], color[1], color[2], color[3])
                .uv(0.0F, 0.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
