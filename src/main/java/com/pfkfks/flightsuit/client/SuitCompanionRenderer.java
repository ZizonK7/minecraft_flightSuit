package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.suit.SuitSize;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * An empty suit: the humanoid body uses a fully transparent texture, so only the armor layer shows - the
 * same suit models and textures as when a player wears it.
 */
public class SuitCompanionRenderer extends HumanoidMobRenderer<SuitCompanionEntity, SuitCompanionModel> {
    private static final ResourceLocation EMPTY = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/empty.png");

    public SuitCompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new SuitCompanionModel(context.bakeLayer(ModelLayers.PLAYER)), 0.4F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    /**
     * Streaking in from afar, the suit lies along its flight path like a boosting player (same rotation as
     * vanilla's elytra glide), pivoting around its middle; eased in and out by flightPoseTicks.
     */
    @Override
    protected void setupRotations(SuitCompanionEntity suit, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.setupRotations(suit, poseStack, ageInTicks, rotationYaw, partialTick);
        float blend = SuitCompanionModel.flightBlend(suit, partialTick);
        if (blend > 0.0F) {
            poseStack.translate(0.0D, 0.9D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(blend * (-90.0F - suit.getViewXRot(partialTick))));
            poseStack.translate(0.0D, -0.9D, 0.0D);
        }
    }

    /** A Hulkbuster companion stands half again as big, like its wearer would (SuitSize). */
    @Override
    protected void scale(SuitCompanionEntity suit, PoseStack poseStack, float partialTick) {
        float size = SuitSize.drawn(suit);
        poseStack.scale(size, size, size);
    }

    @Override
    public ResourceLocation getTextureLocation(SuitCompanionEntity suit) {
        return EMPTY;
    }
}
