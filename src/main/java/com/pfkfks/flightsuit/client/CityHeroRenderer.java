package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.hero.CityHeroEntity;
import com.pfkfks.flightsuit.hero.HeroType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Hero City's people: each hero their own skin (textures/entity/hero/<id>.png), Hulk drawn half again as big. */
public class CityHeroRenderer extends HumanoidMobRenderer<CityHeroEntity, PlayerModel<CityHeroEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[HeroType.values().length];

    static {
        for (HeroType type : HeroType.values()) {
            SKINS[type.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/hero/" + type.id() + ".png");
        }
    }

    public CityHeroRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(CityHeroEntity hero, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<CityHeroEntity> model = getModel();
        HeroType type = hero.getHeroType();
        HumanoidModel.ArmPose right = hero.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (type == HeroType.HAWKEYE && hero.isAggressive()) {
            right = HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        model.rightArmPose = right;
        model.leftArmPose = hero.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        super.render(hero, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CityHeroEntity hero) {
        return SKINS[hero.getHeroType().ordinal()];
    }

    @Override
    protected void scale(CityHeroEntity hero, PoseStack pose, float partialTick) {
        float scale = hero.getHeroType().scale() * 0.97F;
        pose.scale(scale, scale, scale);
    }
}
