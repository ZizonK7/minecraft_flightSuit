package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Draws flying suit pieces exactly as they sit on the body (same model, texture and pose as the matching
 * player animation), placed relative to a phantom owner standing at the entity's position. Because the
 * entity converges onto the owner's feet, "arriving" visually means snapping into place on the body.
 */
public class SuitPartRenderer extends EntityRenderer<SuitPartEntity> {
    /** PlayerAnimator rotates the whole body around this height (blocks above the feet). */
    private static final float BODY_PIVOT = 0.7F;

    public SuitPartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SuitPartEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        Player owner = entity.getOwner();
        if (owner == null) {
            return;
        }
        float progress = entity.getProgress(partialTick);
        if (progress >= 1.0F) {
            return;
        }
        float bodyYaw = Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
        if (entity.isWhole()) {
            // Client entity can lag the server by a tick; once the real suit is on, stop drawing the copy.
            if (progress > 0.8F && WornSuit.of(owner).chest()) {
                return;
            }
            renderWhole(entity, progress, bodyYaw, poseStack, buffers, packedLight);
        } else {
            EquipmentSlot slot = entity.getSlot();
            if (!entity.isLeaving() && progress > 0.8F && owner.getItemBySlot(slot).getItem() instanceof SuitArmorItem) {
                return;
            }
            renderPiece(entity, progress, bodyYaw, poseStack, buffers, packedLight);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private void renderPiece(SuitPartEntity entity, float progress, float bodyYaw, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight) {
        float factor = entity.distanceFactor(progress);
        float pivot = (float) entity.pieceHeight();
        poseStack.pushPose();
        if (entity.isClamp()) {
            // Station assembly: held upright and square to the body by the rig, no tumbling.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw + factor * 540.0F));
            // Tumble around the piece's own center while in the air, level out on the body.
            poseStack.translate(0.0D, pivot, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(factor * 200.0F));
            poseStack.translate(0.0D, -pivot, 0.0D);
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        EquipmentSlot slot = entity.getSlot();
        HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(slot);
        if (entity.isLeaving()) {
            SuitUpPose.applyStanding(model);
        } else if (entity.isClamp()) {
            SuitUpPose.applyStation(model);
        } else {
            SuitUpPose.apply(model);
        }
        draw(model, slot, entity.getSuitId(), poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    /** Mid-air suit-up: the whole suit, spread-eagle like its owner, swelling open and closing onto them. */
    private void renderWhole(SuitPartEntity entity, float progress, float bodyYaw, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight) {
        float factor = entity.distanceFactor(progress);
        float open = 1.0F + 0.35F * factor;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.translate(0.0D, BODY_PIVOT, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(SuitUpPose.FALL_BODY_PITCH_DEG));
        poseStack.scale(open, open, open);
        poseStack.translate(0.0D, -BODY_PIVOT, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(slot);
            SuitUpPose.applyFall(model);
            draw(model, slot, entity.getSuitId(), poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }

    static void draw(HumanoidModel<LivingEntity> model, EquipmentSlot slot, String suitId, PoseStack poseStack,
                     MultiBufferSource buffers, int packedLight) {
        SuitArmorModels.showOnly(model, slot);
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(SuitArmorModels.texture(suitId, slot)));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SuitPartEntity entity) {
        return SuitArmorModels.texture(entity.getSuitId(), entity.getSlot());
    }
}
