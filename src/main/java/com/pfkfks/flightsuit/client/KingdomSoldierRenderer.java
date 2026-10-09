package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.war.Kingdom;
import com.pfkfks.flightsuit.war.KingdomSoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Three Kingdoms soldiers: player-shaped, in their kingdom's lamellar (textures/entity/kingdom/soldier_<kingdom>.png),
 * drawing the bow when shooting, spear raised when charging, and down on one knee after a surrender.
 */
public class KingdomSoldierRenderer extends HumanoidMobRenderer<KingdomSoldierEntity, PlayerModel<KingdomSoldierEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[Kingdom.values().length];

    static {
        for (Kingdom kingdom : Kingdom.values()) {
            SKINS[kingdom.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/kingdom/soldier_" + kingdom.id() + ".png");
        }
    }

    public KingdomSoldierRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(KingdomSoldierEntity soldier, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<KingdomSoldierEntity> model = getModel();
        model.crouching = soldier.hasYielded();
        HumanoidModel.ArmPose arm = HumanoidModel.ArmPose.EMPTY;
        if (!soldier.getMainHandItem().isEmpty()) {
            arm = HumanoidModel.ArmPose.ITEM;
            if (soldier.isAggressive()) {
                if (soldier.getSoldierType() == KingdomSoldierEntity.Type.ARCHER) {
                    arm = HumanoidModel.ArmPose.BOW_AND_ARROW;
                } else if (soldier.getSoldierType() == KingdomSoldierEntity.Type.SPEAR) {
                    arm = HumanoidModel.ArmPose.THROW_SPEAR;
                }
            }
        }
        model.rightArmPose = arm;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(soldier, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KingdomSoldierEntity soldier) {
        return SKINS[soldier.getKingdom().ordinal()];
    }

    @Override
    protected void scale(KingdomSoldierEntity soldier, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
