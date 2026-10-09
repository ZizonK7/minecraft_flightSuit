package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.SpaceshipEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the spaceship; in flight it wobbles a little on its thrust. */
public class SpaceshipRenderer extends EntityRenderer<SpaceshipEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/spaceship.png");

    private final SpaceshipModel model;

    public SpaceshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpaceshipModel(context.bakeLayer(SpaceshipModel.LAYER));
        this.shadowRadius = 1.3F;
    }

    @Override
    public void render(SpaceshipEntity ship, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() != null && ship.hasPassenger(minecraft.getCameraEntity())) {
            // The pilot looks out from inside the hull - don't wall them in.
            return;
        }
        poseStack.pushPose();
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (ship.inFlight()) {
            float t = ship.tickCount + partialTick;
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.7F) * 1.5F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 0.5F) * 1.5F));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        model.renderToBuffer(poseStack, buffers.getBuffer(model.renderType(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(ship, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceshipEntity ship) {
        return TEXTURE;
    }
}
