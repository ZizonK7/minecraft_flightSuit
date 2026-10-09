package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.thief.ThiefEntity;
import com.pfkfks.flightsuit.thief.ThiefType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Batman's crew (textures/entity/thief/<id>.png): crouched while they sneak - though while hidden nobody sees them anyway. */
public class ThiefRenderer extends HumanoidMobRenderer<ThiefEntity, PlayerModel<ThiefEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[ThiefType.values().length];

    static {
        for (ThiefType type : ThiefType.values()) {
            SKINS[type.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/thief/" + type.id() + ".png");
        }
    }

    public ThiefRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(ThiefEntity thief, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<ThiefEntity> model = getModel();
        model.crouching = !thief.isSpotted();
        model.rightArmPose = thief.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(thief, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThiefEntity thief) {
        return SKINS[thief.getThiefType().ordinal()];
    }

    @Override
    protected void scale(ThiefEntity thief, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
