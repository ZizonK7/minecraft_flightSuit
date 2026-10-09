package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.CardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A thrown card: flat along its flight, spinning like a card flicked from the fingers. */
public class CardRenderer extends EntityRenderer<CardEntity> {
    private static final ResourceLocation BLANCHE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/card_blanche.png");
    private static final ResourceLocation NOIR = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/card_noir.png");
    private static final float HALF = 0.22F;

    public CardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(CardEntity card, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, card.yRotO, card.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, card.xRotO, card.getXRot())));
        poseStack.mulPose(Axis.YP.rotationDegrees((card.tickCount + partialTick) * 47.0F));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(card)));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        // Lying flat (in the x-z plane), both sides drawn by the no-cull render type.
        vertex(consumer, pose, normal, -HALF, -HALF, 0.0F, 1.0F, light);
        vertex(consumer, pose, normal, HALF, -HALF, 1.0F, 1.0F, light);
        vertex(consumer, pose, normal, HALF, HALF, 1.0F, 0.0F, light);
        vertex(consumer, pose, normal, -HALF, HALF, 0.0F, 0.0F, light);
        poseStack.popPose();
        super.render(card, yaw, partialTick, poseStack, buffers, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float z, float u, float v, int light) {
        consumer.vertex(pose, x, 0.0F, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(CardEntity card) {
        return card.getStyle() == CardEntity.NOIR ? NOIR : BLANCHE;
    }
}
