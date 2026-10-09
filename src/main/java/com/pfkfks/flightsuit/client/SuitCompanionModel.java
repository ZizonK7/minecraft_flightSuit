package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose source for the companion suit. The body itself is never visible (transparent texture); the armor
 * layer copies these part poses onto the suit pieces. Airborne it holds the same hover stance as a flying
 * player (palms down, arms out), aiming raises the right palm at the target, unpowered it slumps.
 */
public class SuitCompanionModel extends HumanoidModel<SuitCompanionEntity> {
    public SuitCompanionModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(SuitCompanionEntity suit, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(suit, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!suit.isPowered()) {
            head.xRot = 0.6F;
            rightArm.xRot = 0.1F;
            leftArm.xRot = 0.1F;
            rightArm.zRot = 0.05F;
            leftArm.zRot = -0.05F;
            return;
        }
        if (suit.isFlying()) {
            // Hover stance (matches player_animation/hover.json), with a slow bob of the arms.
            float bob = Mth.sin(ageInTicks * 0.15F) * 0.05F;
            rightArm.xRot = 0.21F + bob;
            leftArm.xRot = 0.21F + bob;
            rightArm.zRot = 0.4F;
            leftArm.zRot = -0.4F;
            rightLeg.xRot = 0.1F;
            leftLeg.xRot = -0.07F;
            rightLeg.zRot = 0.05F;
            leftLeg.zRot = -0.05F;
        }
        if (suit.isAiming()) {
            rightArm.yRot = head.yRot;
            rightArm.xRot = -((float) Math.PI / 2.0F) + head.xRot;
            rightArm.zRot = 0.0F;
        }
    }
}
