package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.planet.dbz.DbzAction;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A Dragon Ball fighter's body (M17): the player model, posed in code from the fighter's action (DbzAction) and the
 * time since it started - punches that snap out and back, a kick, arms crossed to guard, the crouched charge and the
 * two-handed push of a beam, reeling from a hit, shaking with rising power, the Ginyu Force's poses... Whole-body
 * leans (flying, lying down, the headbutt) are the renderer's (DbzFighterRenderer.setupRotations).
 */
public class DbzFighterModel extends PlayerModel<DbzFighterEntity> {
    private static final float PI = (float) Math.PI;

    public DbzFighterModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(DbzFighterEntity fighter, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(fighter, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partial = ageInTicks - fighter.tickCount;
        DbzAction action = fighter.getAction();
        float age = fighter.actionAge(partial);
        float len = action.ticks() > 0 ? action.ticks() : 20.0F;
        // 0 -> 1 quickly, then back to 0 by the end (a strike); held poses use 1.
        float strike = action.ticks() > 0 ? snap(age / len) : 1.0F;
        switch (action) {
            case FLY -> {
                rightArm.xRot = 0.7F;
                leftArm.xRot = 0.7F;
                rightArm.zRot = 0.15F;
                leftArm.zRot = -0.15F;
                rightLeg.xRot = 0.25F;
                leftLeg.xRot = 0.45F;
                head.xRot = -0.7F + headPitch * Mth.DEG_TO_RAD * 0.3F;
            }
            case PUNCH_L -> punch(leftArm, rightArm, strike, -1.0F);
            case PUNCH_R -> punch(rightArm, leftArm, strike, 1.0F);
            case KICK -> {
                rightLeg.xRot = -1.7F * strike;
                leftLeg.xRot = 0.2F * strike;
                body.xRot = -0.15F * strike;
                rightArm.zRot = 0.7F * strike;
                leftArm.zRot = -0.7F * strike;
                rightArm.xRot = -0.3F;
                leftArm.xRot = -0.3F;
            }
            case GUARD -> {
                rightArm.xRot = -1.6F;
                rightArm.yRot = -0.65F;
                leftArm.xRot = -1.5F;
                leftArm.yRot = 0.65F;
                head.xRot = 0.2F;
            }
            case CHARGE -> {
                // Both hands cupped at the right hip, crouched, trembling a little.
                float shiver = Mth.sin(ageInTicks * 2.3F) * 0.03F;
                rightArm.xRot = -0.35F + shiver;
                rightArm.yRot = 0.25F;
                leftArm.xRot = -0.55F + shiver;
                leftArm.yRot = 1.05F;
                body.yRot = 0.45F;
                body.xRot = 0.15F;
                rightLeg.xRot = -0.45F;
                leftLeg.xRot = 0.35F;
                head.yRot -= 0.3F;
            }
            case FIRE -> {
                float push = Math.min(1.0F, age / 3.0F);
                rightArm.xRot = -PI / 2.0F * push;
                leftArm.xRot = -PI / 2.0F * push;
                rightArm.yRot = -0.15F;
                leftArm.yRot = 0.15F;
                rightLeg.xRot = -0.35F;
                leftLeg.xRot = 0.3F;
            }
            case HURT -> {
                rightArm.xRot = -0.7F * strike;
                rightArm.zRot = 0.6F * strike;
                leftArm.xRot = -0.7F * strike;
                leftArm.zRot = -0.6F * strike;
                body.xRot = -0.3F * strike;
                head.xRot = -0.5F * strike;
                rightLeg.xRot = -0.3F * strike;
            }
            case DOWN -> {
                rightArm.xRot = 0.0F;
                leftArm.xRot = 0.0F;
                rightArm.zRot = 0.45F;
                leftArm.zRot = -0.45F;
                rightLeg.xRot = 0.0F;
                leftLeg.xRot = 0.0F;
                rightLeg.zRot = 0.12F;
                leftLeg.zRot = -0.12F;
                head.xRot = 0.0F;
                head.yRot = 0.4F;
            }
            case TRANSFORM -> {
                float shake = Mth.sin(ageInTicks * 3.1F) * 0.06F;
                rightArm.zRot = 0.9F + shake;
                leftArm.zRot = -0.9F - shake;
                rightArm.xRot = 0.2F;
                leftArm.xRot = 0.2F;
                body.xRot = -0.15F + shake;
                head.xRot = -0.6F + shake;
                rightLeg.zRot = 0.15F;
                leftLeg.zRot = -0.15F;
            }
            case POSE_GINYU -> ginyuPose(fighter.getCharacter());
            case HEADBUTT -> {
                rightArm.xRot = 0.9F;
                leftArm.xRot = 0.9F;
                rightLeg.xRot = 0.3F;
                leftLeg.xRot = 0.3F;
                head.xRot = -1.0F;
            }
            case CARRY -> {
                rightArm.xRot = -1.25F;
                leftArm.xRot = -1.25F;
                rightArm.yRot = 0.55F;
                leftArm.yRot = -0.55F;
                body.xRot = 0.35F;
                head.xRot = 0.3F;
            }
            case HANDS_UP -> {
                rightArm.xRot = -3.0F;
                leftArm.xRot = -3.0F;
                rightArm.zRot = -0.15F;
                leftArm.zRot = 0.15F;
                head.xRot = -0.5F;
            }
            default -> {
            }
        }
        if (fighter.isClashing() && action == DbzAction.IDLE) {
            rightArm.xRot = -1.2F;
            leftArm.xRot = -1.2F;
        }
        shape(fighter.getCharacter(), action);
        hat.copyFrom(head);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
    }

    /**
     * Body shapes besides the plain one (the model is shared, so every call sets them): the Saibaman's big bulging
     * head on a hunched neck and thin arms and legs (after the M17 test).
     */
    private void shape(DbzCharacter who, DbzAction action) {
        boolean saibaman = who == DbzCharacter.SAIBAMAN;
        float wide = saibaman ? 1.45F : 1.0F;
        float tall = saibaman ? 1.3F : 1.0F;
        float thin = saibaman ? 0.72F : 1.0F;
        for (ModelPart part : new ModelPart[]{head, hat}) {
            part.xScale = wide;
            part.zScale = wide;
            part.yScale = tall;
        }
        for (ModelPart part : new ModelPart[]{rightArm, leftArm, rightLeg, leftLeg, rightSleeve, leftSleeve, rightPants, leftPants}) {
            part.xScale = thin;
            part.zScale = thin;
        }
        // The head pushed forward and low, the arms hanging a little in front (set outright: nothing else resets z).
        boolean hunched = saibaman && action != DbzAction.FLY && action != DbzAction.DOWN;
        head.z = hunched ? -1.2F : 0.0F;
        head.y = hunched ? 0.8F : 0.0F;
        if (hunched) {
            rightArm.xRot -= 0.15F;
            leftArm.xRot -= 0.15F;
        }
    }

    /** Out fast, back slower: 0 at the start, 1 at a third of the way, 0 again at the end. */
    private static float snap(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t < 0.3F ? t / 0.3F : 1.0F - (t - 0.3F) / 0.7F;
    }

    /** A straight punch with {@code arm}, the other up on guard, the body turning behind it. */
    private void punch(ModelPart arm, ModelPart other, float strike, float side) {
        arm.xRot = -0.6F - (PI / 2.0F - 0.6F) * strike;
        arm.yRot = -0.15F * side * strike;
        other.xRot = -1.1F;
        other.yRot = 0.4F * side;
        body.yRot = -0.35F * side * strike;
        head.yRot += 0.2F * side * strike;
    }

    /** The Ginyu Force poses: each member his own. */
    private void ginyuPose(DbzCharacter who) {
        switch (who) {
            case GINYU -> {
                rightArm.xRot = -2.9F;
                rightArm.zRot = 0.3F;
                leftArm.xRot = -0.3F;
                leftArm.zRot = -1.2F;
                rightLeg.zRot = 0.4F;
                leftLeg.xRot = -0.6F;
            }
            case RECOOME -> {
                rightArm.xRot = -2.6F;
                leftArm.xRot = -2.6F;
                rightArm.zRot = -0.5F;
                leftArm.zRot = 0.5F;
                rightLeg.zRot = 0.35F;
                leftLeg.zRot = -0.35F;
            }
            case BURTER -> {
                rightArm.zRot = 1.5F;
                leftArm.zRot = -1.5F;
                body.xRot = 0.4F;
                rightLeg.xRot = -0.6F;
                leftLeg.xRot = 0.5F;
            }
            case JEICE -> {
                rightArm.xRot = -2.3F;
                rightArm.zRot = 0.8F;
                leftArm.xRot = -0.4F;
                leftArm.zRot = -1.0F;
                leftLeg.zRot = -0.5F;
            }
            case GULDO -> {
                rightArm.zRot = 1.2F;
                leftArm.zRot = -1.2F;
                rightLeg.xRot = -1.4F;
                rightLeg.zRot = 0.3F;
            }
            default -> {
                rightArm.zRot = 1.2F;
                leftArm.zRot = -1.2F;
            }
        }
    }
}
