package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.ShenronEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shenron along his spine (ShenronEntity#spine): one ShenronModel segment per 1.6 blocks of body, each stretched
 * between two points on the path and turned so its back faces up - thickest through the middle, tapering to the
 * tail - then the arms just behind the head, the head itself (jaw working while he speaks) and two long
 * whiskers that drift. He glows: everything is drawn full-bright. Dissolving, the body goes from the tail up.
 */
public class ShenronRenderer extends EntityRenderer<ShenronEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/shenron.png");
    private static final float HEAD_SCALE = 2.6F;
    private static final float ARM_SCALE = 2.2F;
    private static final int WHISKER_LINKS = 7;

    private final ShenronModel model;

    public ShenronRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ShenronModel(context.bakeLayer(ShenronModel.LAYER));
        shadowRadius = 0.0F;
    }

    @Override
    public void render(ShenronEntity dragon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = dragon.age(partialTick);
        float leave = dragon.leaving(partialTick);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int bright = LightTexture.FULL_BRIGHT;
        double head = ShenronEntity.headAt(age);
        double length = ShenronEntity.LENGTH;
        double seg = ShenronEntity.SEGMENT;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-dragon.getYRot()));
        int count = (int) (length / seg);
        for (int k = 0; k < count; k++) {
            double front = head - k * seg;
            double back = front - seg;
            if (back < 0.0D) {
                break;
            }
            // u: 1 at the neck, 0 at the tail tip. The tail goes first when he dissolves.
            double u = 1.0D - (k + 0.5D) * seg / length;
            if (u < leave * 1.15D) {
                continue;
            }
            Vec3 a = ShenronEntity.along(back, age);
            Vec3 b = ShenronEntity.along(front, age);
            float thick = thickness(u);
            pose.pushPose();
            Vec3 mid = a.add(b).scale(0.5D);
            pose.translate(mid.x, mid.y, mid.z);
            pose.mulPose(frame(b.subtract(a)));
            pose.scale(thick, thick, (float) a.distanceTo(b) + 0.3F);
            model.segment.render(pose, buffer, bright, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if (leave < 0.95F && head > seg) {
            drawArms(pose, buffer, bright, head, age);
            drawHead(dragon, pose, buffer, bright, head, age, leave);
        }
        pose.popPose();
    }

    /** Body scale (1 = a 0.75-block-thick segment): thin at the tail, full through the middle, a little less at the neck. */
    private static float thickness(double u) {
        double grow = Mth.clamp(u / 0.4D, 0.0D, 1.0D);
        double neck = Mth.clamp((u - 0.9D) / 0.1D, 0.0D, 1.0D);
        return (float) (0.7D + 1.5D * Math.sqrt(grow) - 0.3D * neck);
    }

    /**
     * Rotation taking a piece's own axes onto the path: -Z (toward the head) along {@code forward}, -Y (its back)
     * as close to straight up as the path allows, +X to its left.
     */
    private static Quaternionf frame(Vec3 forward) {
        Vector3f f = forward.toVector3f().normalize();
        Vector3f up = Math.abs(f.y) > 0.97F ? new Vector3f(0.0F, 0.0F, 1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f u = new Vector3f(up).sub(new Vector3f(f).mul(up.dot(f))).normalize();
        Vector3f x = new Vector3f(u).cross(f);
        Matrix3f m = new Matrix3f(x.x, x.y, x.z, -u.x, -u.y, -u.z, -f.x, -f.y, -f.z);
        return new Quaternionf().setFromNormalized(m);
    }

    private static Vec3 tangent(double arc, float age) {
        return ShenronEntity.along(arc, age).subtract(ShenronEntity.along(arc - 0.6D, age));
    }

    /** Two arms reaching forward from just behind the head, claws open, slowly working. */
    private void drawArms(PoseStack pose, VertexConsumer buffer, int light, double head, float age) {
        double at = head - 2.2D * ShenronEntity.SEGMENT;
        if (at < 1.0D) {
            return;
        }
        Vec3 p = ShenronEntity.along(at, age);
        float thick = thickness(0.97D);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(frame(tangent(at, age)));
            // Shoulder low on the flank (this frame: +x left, +y down).
            pose.translate(side * 0.3F * thick, 0.15F * thick, 0.0F);
            pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
            model.arm.setRotation(-0.45F + Mth.sin(age * 0.08F + side) * 0.15F, 0.0F, -side * 0.6F);
            model.forearm.xRot = -0.9F + Mth.sin(age * 0.08F + side + 1.0F) * 0.12F;
            model.arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private void drawHead(ShenronEntity dragon, PoseStack pose, VertexConsumer buffer, int light, double head, float age, float leave) {
        Vec3 p = ShenronEntity.along(head, age);
        pose.pushPose();
        pose.translate(p.x, p.y, p.z);
        pose.mulPose(frame(tangent(head, age)));
        pose.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        model.jaw.xRot = 0.06F + speaking(age, dragon) * 0.5F;
        model.head.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        // Whiskers from the sides of the snout, sweeping out and back and drifting.
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 3.5F / 16.0F, -1.0F / 16.0F, -12.0F / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-side * 130.0F));
            for (int link = 0; link < WHISKER_LINKS; link++) {
                float wave = Mth.sin(age * 0.09F + link * 0.7F + side) * 9.0F;
                pose.mulPose(Axis.YP.rotationDegrees(-side * 6.0F + wave * 0.4F));
                pose.mulPose(Axis.XP.rotationDegrees(7.0F + wave));
                model.whisker.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
                pose.translate(0.0F, 0.0F, -0.5F);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /** How far the jaw is open: talking once he has risen ("Speak your wish") and again as he leaves. */
    private static float speaking(float age, ShenronEntity dragon) {
        float talk = 0.0F;
        float since = age - ShenronEntity.RISE_TICKS;
        if (since > 5.0F && since < 75.0F) {
            talk = 1.0F;
        }
        float leave = dragon.leaving(age - (int) age);
        if (leave > 0.0F && leave < 0.5F) {
            talk = 1.0F;
        }
        return talk * (0.5F + 0.5F * Mth.sin(age * 0.6F));
    }

    @Override
    public ResourceLocation getTextureLocation(ShenronEntity dragon) {
        return TEXTURE;
    }
}
