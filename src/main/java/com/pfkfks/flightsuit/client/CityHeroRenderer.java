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

/** Hero City's people: each hero their own skin (textures/entity/hero/<id>.png); the Hulk has a model of his own (HulkRenderer). */
public class CityHeroRenderer extends HumanoidMobRenderer<CityHeroEntity, PlayerModel<CityHeroEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[HeroType.values().length];
    private final HulkRenderer hulk;

    static {
        for (HeroType type : HeroType.values()) {
            SKINS[type.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/hero/" + type.id() + ".png");
        }
    }

    public CityHeroRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        hulk = new HulkRenderer(context);
    }

    @Override
    public void render(CityHeroEntity hero, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        HeroType type = hero.getHeroType();
        if (type == HeroType.HULK) {
            hulk.render(hero, yaw, partialTick, pose, buffers, light);
            return;
        }
        PlayerModel<CityHeroEntity> model = getModel();
        HumanoidModel.ArmPose right = hero.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (type == HeroType.HAWKEYE && hero.isAggressive()) {
            right = HumanoidModel.ArmPose.BOW_AND_ARROW;
        } else if (type == HeroType.AGENT && hero.isAggressive()) {
            // Both hands on the pistol, aimed.
            right = HumanoidModel.ArmPose.CROSSBOW_HOLD;
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
