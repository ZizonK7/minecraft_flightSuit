package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/** Dragon Ball Earth people (textures/entity/dbz/<id>.png); arms out when they fire ki. */
public class DbzFighterRenderer extends HumanoidMobRenderer<DbzFighterEntity, PlayerModel<DbzFighterEntity>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[DbzCharacter.values().length];

    static {
        for (DbzCharacter character : DbzCharacter.values()) {
            SKINS[character.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/" + character.id() + ".png");
        }
    }

    public DbzFighterRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public void render(DbzFighterEntity fighter, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        PlayerModel<DbzFighterEntity> model = getModel();
        HumanoidModel.ArmPose arms = fighter.isAggressive() && fighter.getCharacter() != DbzCharacter.SAIBAMAN
                ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = arms;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(fighter, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DbzFighterEntity fighter) {
        return SKINS[fighter.getCharacter().ordinal()];
    }

    @Override
    protected void scale(DbzFighterEntity fighter, PoseStack pose, float partialTick) {
        float scale = fighter.getCharacter().scale() * 0.9375F;
        pose.scale(scale, scale, scale);
    }
}
