package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.hero.CityHeroEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Hulk on his own model (HulkModel); CityHeroRenderer hands him over. */
public class HulkRenderer extends HumanoidMobRenderer<CityHeroEntity, HulkModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/hero/hulk_model.png");

    public HulkRenderer(EntityRendererProvider.Context context) {
        super(context, new HulkModel(context.bakeLayer(HulkModel.LAYER)), 0.9F);
    }

    @Override
    public ResourceLocation getTextureLocation(CityHeroEntity hulk) {
        return TEXTURE;
    }

    @Override
    protected void scale(CityHeroEntity hulk, PoseStack poseStack, float partialTick) {
        poseStack.scale(HulkModel.SCALE, HulkModel.SCALE, HulkModel.SCALE);
    }
}
