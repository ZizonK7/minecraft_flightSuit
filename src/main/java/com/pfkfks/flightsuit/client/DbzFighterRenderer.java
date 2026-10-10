package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.DbzAction;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Dragon Ball people (textures/entity/dbz/<id>.png) on the posed model (M17, DbzFighterModel). Whole-body leans
 * here: forward while flying, flat out for the headbutt, on the back when down. The Great Ape has a model of its own
 * (OozaruRenderer takes over for it). Size follows the character on the model and the hitbox alike.
 */
public class DbzFighterRenderer extends HumanoidMobRenderer<DbzFighterEntity, DbzFighterModel> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[DbzCharacter.values().length];

    static {
        for (DbzCharacter character : DbzCharacter.values()) {
            SKINS[character.ordinal()] = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/" + character.id() + ".png");
        }
    }

    private final OozaruRenderer oozaru;

    public DbzFighterRenderer(EntityRendererProvider.Context context) {
        super(context, new DbzFighterModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.oozaru = new OozaruRenderer(context);
    }

    @Override
    public void render(DbzFighterEntity fighter, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (fighter.getCharacter() == DbzCharacter.OOZARU_VEGETA) {
            oozaru.render(fighter, yaw, partialTick, pose, buffers, light);
            return;
        }
        DbzFighterModel model = getModel();
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        shadowRadius = 0.5F * fighter.getCharacter().scale();
        super.render(fighter, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void setupRotations(DbzFighterEntity fighter, PoseStack pose, float ageInTicks, float bodyYaw, float partialTick) {
        super.setupRotations(fighter, pose, ageInTicks, bodyYaw, partialTick);
        DbzAction action = fighter.getAction();
        float height = fighter.getBbHeight();
        switch (action) {
            case FLY, HEADBUTT -> {
                // Lean forward round the middle of the body.
                pose.translate(0.0D, height / 2.0D, 0.0D);
                pose.mulPose(Axis.XP.rotationDegrees(action == DbzAction.HEADBUTT ? -80.0F : -50.0F));
                pose.translate(0.0D, -height / 2.0D, 0.0D);
            }
            case DOWN -> {
                // Flat on the back.
                pose.translate(0.0D, 0.15D * fighter.getCharacter().scale(), 0.0D);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.translate(0.0D, -height * 0.05D, 0.0D);
            }
            default -> {
            }
        }
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
