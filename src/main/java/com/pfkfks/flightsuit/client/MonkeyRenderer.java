package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.MonkeyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Cannon Shooter's monkey on its own model (MonkeyModel), half a player's size. */
public class MonkeyRenderer extends HumanoidMobRenderer<MonkeyEntity, MonkeyModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/monkey.png");
    public static final float SCALE = 0.5F;

    public MonkeyRenderer(EntityRendererProvider.Context context) {
        super(context, new MonkeyModel(context.bakeLayer(MonkeyModel.LAYER)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(MonkeyEntity monkey) {
        return TEXTURE;
    }

    @Override
    protected void scale(MonkeyEntity monkey, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
