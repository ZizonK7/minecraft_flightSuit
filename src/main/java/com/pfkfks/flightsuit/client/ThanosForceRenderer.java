package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.thanos.ThanosForce;
import com.pfkfks.flightsuit.thanos.ThanosForceEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/** Thanos and his forces (textures/entity/thanos/<id>.png); arms up when they fight. */
public class ThanosForceRenderer extends HumanoidMobRenderer<ThanosForceEntity, PlayerModel<ThanosForceEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[ThanosForce.values().length];

    static {
        for (ThanosForce force : ThanosForce.values()) {
            SKINS[force.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/thanos/" + force.id() + ".png");
        }
    }

    public ThanosForceRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(ThanosForceEntity fighter, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<ThanosForceEntity> model = getModel();
        HumanoidModel.ArmPose arms = !fighter.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.ITEM
                : fighter.isAggressive() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = arms;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(fighter, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThanosForceEntity fighter) {
        return SKINS[fighter.getForce().ordinal()];
    }

    @Override
    protected void scale(ThanosForceEntity fighter, PoseStack pose, float partialTick) {
        float scale = fighter.getForce().scale() * 0.9375F;
        pose.scale(scale, scale, scale);
    }
}
