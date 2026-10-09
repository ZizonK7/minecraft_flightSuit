package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Residents are drawn like players (wide-arm model) in their job's clothes - textures/entity/resident/<job>.png,
 * wanderers in the unemployed outfit - with whatever they hold or wear (a suit, later). Downed residents lie
 * on their side.
 */
public class ResidentRenderer extends HumanoidMobRenderer<ResidentEntity, PlayerModel<ResidentEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[ResidentJob.values().length];

    static {
        for (ResidentJob job : ResidentJob.values()) {
            SKINS[job.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/resident/" + job.id() + ".png");
        }
    }

    public ResidentRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(ResidentEntity resident) {
        return SKINS[resident.isWanderer() ? ResidentJob.NONE.ordinal() : resident.getJob().ordinal()];
    }

    @Override
    protected void scale(ResidentEntity resident, PoseStack pose, float partialTick) {
        // Same as PlayerRenderer, so residents are exactly player-sized.
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(ResidentEntity resident, PoseStack pose, float bob, float bodyYaw, float partialTick) {
        super.setupRotations(resident, pose, bob, bodyYaw, partialTick);
        if (resident.isDowned() && resident.deathTime == 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}
