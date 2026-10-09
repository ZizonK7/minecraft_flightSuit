package com.pfkfks.flightsuit.client;

import net.minecraft.client.model.HumanoidModel;

/**
 * Fixed body poses for suit pieces drawn without a wearer (flying pieces, the docked suit). The suit-up
 * poses must match the held keyframes of the matching player animation JSON in
 * assets/flightsuit/player_animation/, so a flying piece lines up with the body it lands on.
 */
public final class SuitUpPose {
    // suit_up_ground.json
    public static final float ARM_ROLL_DEG = 75.0F;
    public static final float LEG_ROLL_DEG = 7.0F;
    public static final float HEAD_PITCH_DEG = -15.0F;

    // suit_up_fall.json (spread-eagle)
    public static final float FALL_BODY_PITCH_DEG = -60.0F;
    public static final float FALL_ARM_ROLL_DEG = 110.0F;
    public static final float FALL_LEG_ROLL_DEG = 20.0F;
    public static final float FALL_HEAD_PITCH_DEG = -35.0F;

    // station_rig.json / station_unrig.json (standing on the rig, arms a little out for the sleeves; legs
    // aren't animated so the walk on and off stays natural)
    public static final float RIG_ARM_ROLL_DEG = 25.0F;
    private static final float STANDING_ARM_ROLL_DEG = 5.0F;

    private static final float DEG = (float) (Math.PI / 180.0D);

    private SuitUpPose() {
    }

    /** Ground suit-up: arms thrown out, chin up. */
    public static void apply(HumanoidModel<?> model) {
        set(model, HEAD_PITCH_DEG, ARM_ROLL_DEG, LEG_ROLL_DEG);
    }

    /** Mid-air suit-up: spread-eagle (the body pitch is applied by the caller's matrix). */
    public static void applyFall(HumanoidModel<?> model) {
        set(model, FALL_HEAD_PITCH_DEG, FALL_ARM_ROLL_DEG, FALL_LEG_ROLL_DEG);
    }

    /** Station rig: from the docked suit's at-ease pose (0) to the wearer's arms-out rig pose (1). */
    public static void applyRig(HumanoidModel<?> model, float blend) {
        set(model, 0.0F, STANDING_ARM_ROLL_DEG + (RIG_ARM_ROLL_DEG - STANDING_ARM_ROLL_DEG) * blend, 0.0F);
    }

    /** Standing at ease - the docked suit and pieces leaving the body. */
    public static void applyStanding(HumanoidModel<?> model) {
        set(model, 0.0F, STANDING_ARM_ROLL_DEG, 0.0F);
    }

    private static void set(HumanoidModel<?> model, float headPitch, float armRoll, float legRoll) {
        model.head.setPos(0.0F, 0.0F, 0.0F);
        model.head.setRotation(headPitch * DEG, 0.0F, 0.0F);
        model.body.setPos(0.0F, 0.0F, 0.0F);
        model.body.setRotation(0.0F, 0.0F, 0.0F);
        model.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        model.rightArm.setRotation(0.0F, 0.0F, armRoll * DEG);
        model.leftArm.setPos(5.0F, 2.0F, 0.0F);
        model.leftArm.setRotation(0.0F, 0.0F, -armRoll * DEG);
        model.rightLeg.setPos(-1.9F, 12.0F, 0.0F);
        model.rightLeg.setRotation(0.0F, 0.0F, legRoll * DEG);
        model.leftLeg.setPos(1.9F, 12.0F, 0.0F);
        model.leftLeg.setRotation(0.0F, 0.0F, -legRoll * DEG);
    }
}
