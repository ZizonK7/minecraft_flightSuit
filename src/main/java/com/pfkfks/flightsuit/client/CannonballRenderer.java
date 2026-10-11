package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.CannonballEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * M18: an iron cannonball - a black ball built of chamfered boxes (a core and three thinner slabs across it, which
 * reads as round from any side), a dull highlight on top and a sparking fuse; it tumbles as it flies. The giant one
 * is the same, five times over.
 */
public class CannonballRenderer extends EntityRenderer<CannonballEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/station_arm.png");
    private static final int[] IRON = {34, 34, 40};
    private static final int[] IRON_LIGHT = {70, 70, 80};
    private static final int[] IRON_DARK = {20, 20, 24};
    private static final int[] FUSE = {255, 190, 70};

    public CannonballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(CannonballEntity ball, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        float size = ball.size();
        poseStack.translate(0.0F, size * 0.5F, 0.0F);
        // Tumbling end over end along its flight.
        float spin = (ball.tickCount + partialTick) * (ball.giant() ? 6.0F : 18.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-ball.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin));
        poseStack.scale(size, size, size);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // A core cube and three slabs crossing it: the silhouette rounds off.
        BoxDraw.box(poseStack, consumer, -0.36F, -0.36F, -0.36F, 0.36F, 0.36F, 0.36F, IRON, light);
        BoxDraw.box(poseStack, consumer, -0.5F, -0.26F, -0.26F, 0.5F, 0.26F, 0.26F, IRON, light);
        BoxDraw.box(poseStack, consumer, -0.26F, -0.5F, -0.26F, 0.26F, 0.5F, 0.26F, IRON, light);
        BoxDraw.box(poseStack, consumer, -0.26F, -0.26F, -0.5F, 0.26F, 0.26F, 0.5F, IRON, light);
        BoxDraw.box(poseStack, consumer, -0.44F, -0.44F, -0.12F, 0.44F, 0.44F, 0.12F, IRON_DARK, light);
        BoxDraw.box(poseStack, consumer, -0.12F, -0.44F, -0.44F, 0.12F, 0.44F, 0.44F, IRON_DARK, light);
        // The glint, and the fuse poking out the top.
        BoxDraw.box(poseStack, consumer, -0.14F, 0.42F, -0.14F, 0.1F, 0.52F, 0.1F, IRON_LIGHT, light);
        BoxDraw.box(poseStack, consumer, -0.04F, 0.5F, -0.04F, 0.04F, 0.66F, 0.04F, IRON_DARK, light);
        BoxDraw.box(poseStack, consumer, -0.06F, 0.64F, -0.06F, 0.06F, 0.74F, 0.06F, FUSE, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
        super.render(ball, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonballEntity ball) {
        return TEXTURE;
    }
}
