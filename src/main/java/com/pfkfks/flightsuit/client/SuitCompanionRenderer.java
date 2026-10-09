package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
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

    @Override
    public ResourceLocation getTextureLocation(SuitCompanionEntity suit) {
        return EMPTY;
    }
}
