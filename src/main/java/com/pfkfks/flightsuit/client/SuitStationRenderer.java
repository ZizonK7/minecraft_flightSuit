package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.suit.SuitType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** The docked suit standing on its station, facing the way the station was placed. */
public class SuitStationRenderer implements BlockEntityRenderer<SuitStationBlockEntity> {
    public SuitStationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SuitStationBlockEntity station, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        SuitType type = station.getSuitType();
        if (type == null || station.getLevel() == null) {
            return;
        }
        Direction facing = station.getBlockState().getValue(SuitStationBlock.FACING);
        int light = LevelRenderer.getLightColor(station.getLevel(), station.getBlockPos().above());

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.25D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : station.getParts().entrySet()) {
            HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(entry.getKey());
            SuitUpPose.applyStanding(model);
            SuitPartRenderer.draw(model, entry.getKey(), type.id(), poseStack, buffers, light);
        }
        poseStack.popPose();
    }
}
