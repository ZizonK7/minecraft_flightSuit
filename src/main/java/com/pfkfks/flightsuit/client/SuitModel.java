package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * A suit with a model of its own (Hulkbuster, Trunks) instead of a skin cut into four textures. Every limb
 * holds four piece children - head_piece_*, chest_piece_*, legs_piece_*, feet_piece_* - and one instance per
 * armour slot shows only its own pieces, so the four armour items still stack into one suit and come off one
 * at a time. One texture for the whole suit.
 */
public class SuitModel extends ShiftedHumanoidModel<LivingEntity> {
    private static final String[] PIECES = {"head_piece", "chest_piece", "legs_piece", "feet_piece"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] LIMBS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private final EquipmentSlot slot;
    private final ModelPart[][] pieces = new ModelPart[LIMBS.length][PIECES.length];
    /** Trunks: the sword's hilt on his back (null for suits without one), hidden while the sword is in his hand. */
    private final ModelPart hilt;
    private boolean swordDrawn;
    /** Trunks: the Super Saiyan 3 mane down the back (null for other suits), shown only then (M18 feedback). */
    private final ModelPart mane;
    private boolean ssj3;

    public SuitModel(ModelPart root, float[][] shifts, EquipmentSlot slot) {
        super(root, shifts);
        this.slot = slot;
        ModelPart[] limbs = limbs();
        for (int i = 0; i < LIMBS.length; i++) {
            for (int s = 0; s < PIECES.length; s++) {
                pieces[i][s] = limbs[i].getChild(PIECES[s] + "_" + LIMBS[i]);
            }
        }
        hilt = hiltOf(pieces[1][1]);
        mane = childOf(pieces[0][0], "mane");
    }

    private static ModelPart childOf(ModelPart part, String name) {
        try {
            return part.getChild(name);
        } catch (java.util.NoSuchElementException missing) {
            return null;
        }
    }

    private static ModelPart hiltOf(ModelPart chest) {
        try {
            return chest.getChild("sheath").getChild("hilt");
        } catch (java.util.NoSuchElementException missing) {
            return null;
        }
    }

    /** Set just before rendering for the wearer at hand (SuitArmorModels.forWearer). */
    public void setSwordDrawn(boolean drawn) {
        swordDrawn = drawn;
    }

    public void setSsj3(boolean on) {
        ssj3 = on;
    }

    private void showPieces(EquipmentSlot only) {
        for (ModelPart[] limb : pieces) {
            for (int s = 0; s < SLOTS.length; s++) {
                limb[s].visible = SLOTS[s] == only;
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        showPieces(slot);
        if (hilt != null) {
            hilt.visible = !swordDrawn;
        }
        if (mane != null) {
            mane.visible = ssj3;
        }
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** The first-person arm (ClientEvents#onRenderArm): one arm's chest piece where the player's arm would be. */
    public void renderArm(PoseStack poseStack, VertexConsumer buffer, int packedLight, boolean right) {
        showPieces(EquipmentSlot.CHEST);
        ModelPart arm = right ? rightArm : leftArm;
        arm.visible = true;
        arm.setPos(right ? -5.0F : 5.0F, 2.0F, 0.0F);
        arm.setRotation(0.0F, 0.0F, right ? 0.1F : -0.1F);
        arm.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
