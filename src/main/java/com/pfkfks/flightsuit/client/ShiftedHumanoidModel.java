package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * A humanoid whose limbs sit somewhere other than a player's (the Hulk's shoulders are far wider, his head
 * hangs low and forward). Poses still come from HumanoidModel / the armour layer's copy, which put every
 * pivot at the player's spot; the offsets are added for the draw only and taken off again, so they never
 * pile up from frame to frame and nothing that reads the pose (held items, PlayerAnimator) sees them.
 */
public class ShiftedHumanoidModel<T extends LivingEntity> extends HumanoidModel<T> {
    /** Per part, in order head, body, right arm, left arm, right leg, left leg: x, y, z in pixels. */
    private final float[][] shifts;
    private final float[] saved = new float[18];

    public ShiftedHumanoidModel(ModelPart root, float[][] shifts) {
        super(root);
        this.shifts = shifts;
    }

    protected ModelPart[] limbs() {
        return new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg};
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        ModelPart[] limbs = limbs();
        for (int i = 0; i < limbs.length; i++) {
            saved[i * 3] = limbs[i].x;
            saved[i * 3 + 1] = limbs[i].y;
            saved[i * 3 + 2] = limbs[i].z;
            limbs[i].x += shifts[i][0];
            limbs[i].y += shifts[i][1];
            limbs[i].z += shifts[i][2];
        }
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (int i = 0; i < limbs.length; i++) {
            limbs[i].x = saved[i * 3];
            limbs[i].y = saved[i * 3 + 1];
            limbs[i].z = saved[i * 3 + 2];
        }
    }
}
