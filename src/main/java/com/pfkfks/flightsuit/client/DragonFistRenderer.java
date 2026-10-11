package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.DragonFistEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Dragon Fist's dragon (M18 feedback: Trunks' B): a golden Shenron whose head rides just ahead of the puncher's
 * fist and whose body coils back along the path the punch has taken - the trail is the puncher's own positions,
 * kept here frame by frame - drawn full-bright in the golden Shenron texture and dissolving from the tail when
 * the dash is done.
 */
public class DragonFistRenderer extends EntityRenderer<DragonFistEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/shenron_gold.png");
    private static final double SEGMENT = 1.1D;
    /** How much path the body covers behind the head. */
    private static final double BODY = 22.0D;
    private static final float HEAD_SCALE = 1.9F;
    private static final float ARM_SCALE = 1.5F;
    private static final int WHISKER_LINKS = 6;
    /** Trails by entity id (ids, not entities: client entities get re-created). */
    private static final Map<Integer, Deque<Vec3>> TRAILS = new HashMap<>();

    private final ShenronModel model;

    public DragonFistRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ShenronModel(context.bakeLayer(ShenronModel.LAYER));
    }

    public static void reset() {
        TRAILS.clear();
    }

    @Override
    public void render(DragonFistEntity dragon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity owner = dragon.owner();
        if (owner == null) {
            return;
        }
        float fade = dragon.fade(partialTick);
        float time = dragon.tickCount + partialTick;
        Deque<Vec3> trail = TRAILS.computeIfAbsent(dragon.getId(), id -> new ArrayDeque<>());
        if (dragon.isRemoved()) {
            TRAILS.remove(dragon.getId());
            return;
        }
        // The head leads the fist a little; the trail grows only while the dash is on.
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 front = eye.add(look.scale(1.6D)).add(0.0D, -0.3D, 0.0D);
        if (fade <= 0.0F && (trail.isEmpty() || trail.peekFirst().distanceTo(front) > 0.25D)) {
            trail.addFirst(front);
        }
        // Trim to BODY blocks of path.
        double total = 0.0D;
        List<Vec3> points = new ArrayList<>();
        Vec3 last = null;
        for (Vec3 p : trail) {
            if (last != null) {
                total += p.distanceTo(last);
                if (total > BODY) {
                    break;
                }
            }
            points.add(p);
            last = p;
        }
        while (trail.size() > points.size()) {
            trail.removeLast();
        }
        if (points.size() < 2) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        int bright = LightTexture.FULL_BRIGHT;
        Vec3 origin = new Vec3(Mth.lerp(partialTick, dragon.xo, dragon.getX()), Mth.lerp(partialTick, dragon.yo, dragon.getY()),
                Mth.lerp(partialTick, dragon.zo, dragon.getZ()));
        float alpha = 1.0F - fade;
        pose.pushPose();
        pose.translate(-origin.x, -origin.y, -origin.z);
        // The body: resampled every SEGMENT along the trail, coiling round it.
        List<Vec3> spine = resample(points, SEGMENT);
        int count = spine.size();
        for (int i = 0; i < count - 1; i++) {
            // u: 1 at the head, 0 at the tail end. The tail dissolves first.
            double u = 1.0D - (i + 0.5D) / Math.max(1, count - 1);
            if (u < fade * 1.1D) {
                continue;
            }
            Vec3 a = coil(spine, i, time, u);
            Vec3 b = coil(spine, i + 1, time, u - 1.0D / count);
            float thick = (float) (0.45D + 0.75D * Math.sqrt(Math.min(1.0D, u / 0.5D)) - 0.15D * Math.max(0.0D, (u - 0.9D) / 0.1D));
            pose.pushPose();
            Vec3 mid = a.add(b).scale(0.5D);
            pose.translate(mid.x, mid.y, mid.z);
            pose.mulPose(frame(b.subtract(a)));
            pose.scale(thick, thick, (float) a.distanceTo(b) + 0.25F);
            model.segment.render(pose, buffer, bright, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            pose.popPose();
        }
        if (fade < 0.9F) {
            Vec3 head = coil(spine, 0, time, 1.0D);
            Vec3 behind = spine.size() > 1 ? coil(spine, 1, time, 1.0D - 1.0D / count) : head.subtract(look);
            Vec3 forward = head.subtract(behind);
            drawArms(pose, buffer, bright, head, forward, time, alpha);
            drawHead(pose, buffer, bright, head, forward, time, alpha);
        }
        pose.popPose();
    }

    /** Points every {@code step} blocks along the trail (head first). */
    private static List<Vec3> resample(List<Vec3> points, double step) {
        List<Vec3> out = new ArrayList<>();
        out.add(points.get(0));
        double carry = 0.0D;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            double len = a.distanceTo(b);
            double at = step - carry;
            while (at <= len) {
                out.add(a.lerp(b, at / len));
                at += step;
            }
            carry = len - (at - step);
        }
        return out;
    }

    /** The spine point {@code i} swung round the trail in a slow helix (tighter towards the tail), rolling with time. */
    private static Vec3 coil(List<Vec3> spine, int i, float time, double u) {
        Vec3 p = spine.get(Math.min(i, spine.size() - 1));
        Vec3 dir = i + 1 < spine.size() ? spine.get(i).subtract(spine.get(i + 1)) : spine.get(Math.max(0, i - 1)).subtract(p);
        if (dir.lengthSqr() < 1.0E-6D) {
            return p;
        }
        dir = dir.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = side.cross(dir).normalize();
        double radius = 0.3D + 1.0D * (1.0D - u) * Math.min(1.0D, i / 3.0D);
        double theta = i * 0.75D - time * 0.25D;
        return p.add(side.scale(Math.cos(theta) * radius)).add(up.scale(Math.sin(theta) * radius));
    }

    /** -Z (toward the head) along {@code forward}, -Y (the back) as near straight up as the path allows. */
    private static Quaternionf frame(Vec3 forward) {
        Vector3f f = forward.toVector3f().normalize();
        Vector3f upHint = Math.abs(f.y) > 0.97F ? new Vector3f(0.0F, 0.0F, 1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f u = new Vector3f(upHint).sub(new Vector3f(f).mul(upHint.dot(f))).normalize();
        Vector3f x = new Vector3f(u).cross(f);
        Matrix3f m = new Matrix3f(x.x, x.y, x.z, -u.x, -u.y, -u.z, -f.x, -f.y, -f.z);
        return new Quaternionf().setFromNormalized(m);
    }

    private void drawArms(PoseStack pose, VertexConsumer buffer, int light, Vec3 head, Vec3 forward, float time, float alpha) {
        Vec3 at = head.subtract(forward.normalize().scale(1.4D));
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(frame(forward));
            pose.translate(side * 0.35F, 0.1F, 0.0F);
            pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
            // Claws reaching forward, as the dragon strikes.
            model.arm.setRotation(-1.2F + Mth.sin(time * 0.3F + side) * 0.1F, 0.0F, -side * 0.35F);
            model.forearm.xRot = -0.5F;
            model.arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
            pose.popPose();
        }
    }

    private void drawHead(PoseStack pose, VertexConsumer buffer, int light, Vec3 head, Vec3 forward, float time, float alpha) {
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        pose.mulPose(frame(forward));
        pose.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        // Jaws wide, roaring.
        model.jaw.xRot = 0.55F + Mth.sin(time * 0.5F) * 0.1F;
        model.head.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 3.5F / 16.0F, -1.0F / 16.0F, -12.0F / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-side * 140.0F));
            for (int link = 0; link < WHISKER_LINKS; link++) {
                float wave = Mth.sin(time * 0.4F + link * 0.8F + side) * 12.0F;
                pose.mulPose(Axis.YP.rotationDegrees(-side * 5.0F + wave * 0.4F));
                pose.mulPose(Axis.XP.rotationDegrees(5.0F + wave));
                model.whisker.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
                pose.translate(0.0F, 0.0F, -0.5F);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DragonFistEntity dragon) {
        return TEXTURE;
    }
}
