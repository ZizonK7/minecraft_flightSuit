package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Vegeta's Great Ape on its own model (OozaruModel); DbzFighterRenderer hands it over. */
public class OozaruRenderer extends HumanoidMobRenderer<DbzFighterEntity, OozaruModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/oozaru_vegeta.png");

    public OozaruRenderer(EntityRendererProvider.Context context) {
        super(context, new OozaruModel(context.bakeLayer(OozaruModel.LAYER)), 2.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(DbzFighterEntity ape) {
        return TEXTURE;
    }

    @Override
    protected void scale(DbzFighterEntity ape, PoseStack poseStack, float partialTick) {
        poseStack.scale(OozaruModel.SCALE, OozaruModel.SCALE, OozaruModel.SCALE);
    }
}
