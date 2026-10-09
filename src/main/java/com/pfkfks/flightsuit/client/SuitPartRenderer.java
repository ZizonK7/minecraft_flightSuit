package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
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
 * Draws a flying suit piece exactly as it will sit on the body (same model, same texture, same suit-up
 * pose), placed relative to a phantom player standing at the entity's position. Because the entity
 * converges onto the owner's feet, "arriving" visually means snapping into place on the body.
 */
public class SuitPartRenderer extends EntityRenderer<SuitPartEntity> {
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
        EquipmentSlot slot = entity.getSlot();
        // Client entity can lag the server by a tick; once the real piece is on, stop drawing the copy.
        if (progress >= 1.0F || (progress > 0.8F && owner.getItemBySlot(slot).getItem() instanceof SuitArmorItem)) {
            return;
        }

        float remaining = 1.0F - SuitPartEntity.ease(progress);
        float bodyYaw = Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
        float pivot = (float) entity.pieceHeight();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw + remaining * 540.0F));
        // Tumble around the piece's own center while in the air, level out on arrival.
        poseStack.translate(0.0D, pivot, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(remaining * 200.0F));
        poseStack.translate(0.0D, -pivot, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(slot);
        SuitUpPose.apply(model);
        SuitArmorModels.showOnly(model, slot);
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(getTextureLocation(entity)));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SuitPartEntity entity) {
        return SuitArmorModels.texture(entity.getSuitId(), entity.getSlot());
    }
}
