package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.town.TownRole;
import com.pfkfks.flightsuit.town.TownsfolkEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The towns' people (textures/entity/townsfolk/<role>.png); the children at three fifths the size. */
public class TownsfolkRenderer extends HumanoidMobRenderer<TownsfolkEntity, PlayerModel<TownsfolkEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[TownRole.values().length];

    static {
        for (TownRole role : TownRole.values()) {
            SKINS[role.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/townsfolk/" + role.id() + ".png");
        }
    }

    public TownsfolkRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(TownsfolkEntity person, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<TownsfolkEntity> model = getModel();
        model.rightArmPose = person.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(person, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TownsfolkEntity person) {
        return SKINS[person.getRole().ordinal()];
    }

    @Override
    protected void scale(TownsfolkEntity person, PoseStack pose, float partialTick) {
        float scale = person.getRole().isChild() ? 0.6F * 0.9375F : 0.9375F;
        pose.scale(scale, scale, scale);
    }
}
