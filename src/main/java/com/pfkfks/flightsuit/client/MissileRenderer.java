package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.MissileEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Micro-missile: white body, red nose, four fins and a glowing motor, pointed along its flight. */
public class MissileRenderer extends EntityRenderer<MissileEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/station_arm.png");
    private static final int[] BODY = {236, 238, 242};
    private static final int[] NOSE = {200, 40, 34};
    private static final int[] FIN = {70, 74, 82};
    private static final int[] MOTOR = {255, 170, 60};

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(MissileEntity missile, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        // Projectile rotation: yRot from atan2(x, z), xRot = climb angle. Model points along +z.
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, missile.yRotO, missile.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, missile.xRotO, missile.getXRot())));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        BoxDraw.box(poseStack, consumer, -0.04F, -0.04F, -0.22F, 0.04F, 0.04F, 0.16F, BODY, light);
        BoxDraw.box(poseStack, consumer, -0.03F, -0.03F, 0.16F, 0.03F, 0.03F, 0.24F, NOSE, light);
        BoxDraw.box(poseStack, consumer, -0.1F, -0.01F, -0.22F, 0.1F, 0.01F, -0.12F, FIN, light);
        BoxDraw.box(poseStack, consumer, -0.01F, -0.1F, -0.22F, 0.01F, 0.1F, -0.12F, FIN, light);
        BoxDraw.box(poseStack, consumer, -0.03F, -0.03F, -0.27F, 0.03F, 0.03F, -0.22F, MOTOR, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
        super.render(missile, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity missile) {
        return TEXTURE;
    }
}
