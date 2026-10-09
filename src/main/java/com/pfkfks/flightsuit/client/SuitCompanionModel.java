package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Pose source for the companion suit. The body itself is never visible (transparent texture); the armor
 * layer copies these part poses onto the suit pieces. Airborne it holds the same hover stance as a flying
 * player (palms down, arms out), streaking in from afar it lies out like a boosting player, aiming raises the
 * right palm at the target, unpowered it slumps.
 */
public class SuitCompanionModel extends HumanoidModel<SuitCompanionEntity> {
    public SuitCompanionModel(ModelPart root) {
        super(root);
    }

    /** 0 = upright, 1 = fully laid out in fast flight (see SuitCompanionEntity#flightPoseTicks). */
    public static float flightBlend(SuitCompanionEntity suit, float partialTick) {
        float ticks = suit.flightPoseTicks + (suit.isArriving() ? partialTick : -partialTick);
        return Mth.clamp(ticks / 5.0F, 0.0F, 1.0F);
    }

    @Override
    public void setupAnim(SuitCompanionEntity suit, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // The opening pose moves parts that vanilla setupAnim never resets; put them back first (the model
        // instance is shared by every companion).
        head.z = 0.0F;
        body.z = 0.0F;
        rightArm.x = -5.0F;
        leftArm.x = 5.0F;
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
        float flight = flightBlend(suit, ageInTicks - (int) ageInTicks);
        if (flight > 0.0F) {
            // Laid out along the flight path (the renderer tilts the whole body): arms back along the sides,
            // legs together, head up to see where it's going - player_animation/boost.json.
            head.xRot = Mth.lerp(flight, head.xRot, -0.785F);
            head.yRot = Mth.lerp(flight, head.yRot, 0.0F);
            rightArm.xRot = Mth.lerp(flight, rightArm.xRot, 0.0F);
            leftArm.xRot = Mth.lerp(flight, leftArm.xRot, 0.0F);
            rightArm.zRot = Mth.lerp(flight, rightArm.zRot, 0.14F);
            leftArm.zRot = Mth.lerp(flight, leftArm.zRot, -0.14F);
            rightLeg.xRot = Mth.lerp(flight, rightLeg.xRot, 0.0F);
            leftLeg.xRot = Mth.lerp(flight, leftLeg.xRot, 0.0F);
            rightLeg.zRot = Mth.lerp(flight, rightLeg.zRot, 0.02F);
            leftLeg.zRot = Mth.lerp(flight, leftLeg.zRot, -0.02F);
            return;
        }
        if (suit.isOpening()) {
            // Splitting open for the owner to step in from behind: helmet lifts and tips forward, arms swing
            // wide, the torso shell slides forward and the legs part.
            float t = Math.min(1.0F, (suit.openTicks + ageInTicks - (int) ageInTicks) / 6.0F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            head.y = -3.0F * ease;
            head.z = -1.5F * ease;
            head.xRot = 0.7F * ease;
            head.yRot = 0.0F;
            body.z = -1.5F * ease;
            rightArm.x = -5.0F - 1.5F * ease;
            leftArm.x = 5.0F + 1.5F * ease;
            rightArm.xRot = 0.0F;
            leftArm.xRot = 0.0F;
            rightArm.zRot = 0.1F + 0.9F * ease;
            leftArm.zRot = -0.1F - 0.9F * ease;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
            rightLeg.zRot = 0.2F * ease;
            leftLeg.zRot = -0.2F * ease;
            return;
        }
        if (suit.isAiming()) {
            rightArm.yRot = head.yRot;
            rightArm.xRot = -((float) Math.PI / 2.0F) + head.xRot;
            rightArm.zRot = 0.0F;
        }
    }
}
