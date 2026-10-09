package com.pfkfks.flightsuit.client;

import net.minecraft.client.model.HumanoidModel;

/**
 * The held "arms out, chin up" pose of the ground suit-up. Must match the held keyframes in
 * assets/flightsuit/player_animation/suit_up_ground.json, so a flying piece lines up with the body it
 * lands on.
 */
public final class SuitUpPose {
    public static final float ARM_ROLL_DEG = 75.0F;
    public static final float LEG_ROLL_DEG = 7.0F;
    public static final float HEAD_PITCH_DEG = -15.0F;

    private static final float DEG = (float) (Math.PI / 180.0D);

    private SuitUpPose() {
    }

    public static void apply(HumanoidModel<?> model) {
        model.head.setPos(0.0F, 0.0F, 0.0F);
        model.head.setRotation(HEAD_PITCH_DEG * DEG, 0.0F, 0.0F);
        model.body.setPos(0.0F, 0.0F, 0.0F);
        model.body.setRotation(0.0F, 0.0F, 0.0F);
        model.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        model.rightArm.setRotation(0.0F, 0.0F, ARM_ROLL_DEG * DEG);
        model.leftArm.setPos(5.0F, 2.0F, 0.0F);
        model.leftArm.setRotation(0.0F, 0.0F, -ARM_ROLL_DEG * DEG);
        model.rightLeg.setPos(-1.9F, 12.0F, 0.0F);
        model.rightLeg.setRotation(0.0F, 0.0F, LEG_ROLL_DEG * DEG);
        model.leftLeg.setPos(1.9F, 12.0F, 0.0F);
        model.leftLeg.setRotation(0.0F, 0.0F, -LEG_ROLL_DEG * DEG);
    }
}
