package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Code-drawn boxes for things without a model of their own (the station's robot arms, missiles): each face
 * maps the whole texture, tinted by a vertex color. Use with an entity render type (entityCutoutNoCull).
 */
public final class BoxDraw {
    private BoxDraw() {
    }

    public static void segment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float thickness, int[] color, int light) {
        Vec3 d = to.subtract(from);
        float length = (float) d.length();
        if (length < 1.0E-4F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), d.normalize().toVector3f()));
        float h = thickness / 2.0F;
        box(poseStack, consumer, -h, -h, 0.0F, h, h, length, color, light);
        poseStack.popPose();
    }

    public static void cube(PoseStack poseStack, VertexConsumer consumer, Vec3 center, float size, int[] color, int light) {
        float h = size / 2.0F;
        box(poseStack, consumer, (float) center.x - h, (float) center.y - h, (float) center.z - h,
                (float) center.x + h, (float) center.y + h, (float) center.z + h, color, light);
    }

    public static void box(PoseStack poseStack, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1,
                            int[] color, int light) {
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();
        // down, up, north (-z), south (+z), west (-x), east (+x)
        quad(pose, normal, consumer, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, normal, consumer, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(pose, normal, consumer, color, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, normal, consumer, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, normal, consumer, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, normal, consumer, color, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    public static void quad(Matrix4f pose, Matrix3f normal, VertexConsumer consumer, int[] color, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, normal, consumer, color, light, nx, ny, nz, ax, ay, az, 0.0F, 1.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, bx, by, bz, 1.0F, 1.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, cx, cy, cz, 1.0F, 0.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, dx, dy, dz, 0.0F, 0.0F);
    }

    public static void vertex(Matrix4f pose, Matrix3f normal, VertexConsumer consumer, int[] color, int light,
                               float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        consumer.vertex(pose, x, y, z).color(color[0], color[1], color[2], 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, nx, ny, nz).endVertex();
    }
}
