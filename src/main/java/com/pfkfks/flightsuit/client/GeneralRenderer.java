package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.war.General;
import com.pfkfks.flightsuit.war.GeneralEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Generals: their own skin (textures/entity/kingdom/<general>.png), a head taller than their men, kneeling when beaten. */
public class GeneralRenderer extends HumanoidMobRenderer<GeneralEntity, PlayerModel<GeneralEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[General.values().length];

    static {
        for (General general : General.values()) {
            SKINS[general.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/kingdom/" + general.id() + ".png");
        }
    }

    public GeneralRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.6F);
    }

    @Override
    public void render(GeneralEntity general, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<GeneralEntity> model = getModel();
        model.crouching = general.hasYielded() || general.isWounded();
        HumanoidModel.ArmPose arm = general.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (general.isAggressive() && general.getGeneral() == General.ZHANG_FEI) {
            arm = HumanoidModel.ArmPose.THROW_SPEAR;
        }
        model.rightArmPose = arm;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(general, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GeneralEntity general) {
        return SKINS[general.getGeneral().ordinal()];
    }

    @Override
    protected void scale(GeneralEntity general, PoseStack pose, float partialTick) {
        pose.scale(1.02F, 1.02F, 1.02F);
    }
}
