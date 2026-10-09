package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the hover car, banking into turns. */
public class HoverCarRenderer extends EntityRenderer<HoverCarEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/hover_car.png");

    private final HoverCarModel model;

    public HoverCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HoverCarModel(context.bakeLayer(HoverCarModel.LAYER));
        this.shadowRadius = 1.1F;
    }

    @Override
    public void render(HoverCarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        float yaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, car.bankO, car.bank)));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        model.renderToBuffer(poseStack, buffers.getBuffer(model.renderType(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        model.renderGlass(poseStack, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(car, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HoverCarEntity car) {
        return TEXTURE;
    }
}
