package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.entity.VeronicaPodEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Veronica, the Hulkbuster's pod (M17): a capsule of iron plate - a tall body, a narrower cap, a Hulkbuster-red band
 * - built from scaled block models, with a front door that folds down like a ramp when it opens.
 */
public class VeronicaPodRenderer extends EntityRenderer<VeronicaPodEntity> {
    private static final BlockState PLATE = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState BAND = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState DARK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

    public VeronicaPodRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(VeronicaPodEntity pod, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        // Local +Z = the way it faces (its door).
        poseStack.mulPose(Axis.YP.rotationDegrees(-pod.getYRot()));
        // Body, cap, base, red band, the light on top.
        box(blocks, poseStack, buffers, light, PLATE, -0.9F, 0.0F, -0.9F, 1.8F, 2.6F, 1.8F);
        box(blocks, poseStack, buffers, light, PLATE, -0.7F, 2.6F, -0.7F, 1.4F, 0.4F, 1.4F);
        box(blocks, poseStack, buffers, light, DARK, -1.0F, 0.0F, -1.0F, 2.0F, 0.25F, 2.0F);
        box(blocks, poseStack, buffers, light, BAND, -0.95F, 1.7F, -0.95F, 1.9F, 0.3F, 1.9F);
        box(blocks, poseStack, buffers, 0xF000F0, LIGHT, -0.2F, 3.0F, -0.2F, 0.4F, 0.15F, 0.4F);
        // The door on the front (+Z here), hinged at its bottom edge, folding down open.
        float open = pod.doorOpen(partialTick);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.25F, 0.92F);
        poseStack.mulPose(Axis.XP.rotationDegrees(open * 95.0F));
        box(blocks, poseStack, buffers, light, PLATE, -0.7F, 0.0F, 0.0F, 1.4F, 2.2F, 0.12F);
        box(blocks, poseStack, buffers, light, BAND, -0.72F, 1.45F, 0.02F, 1.44F, 0.25F, 0.12F);
        poseStack.popPose();
        if (open > 0.05F) {
            // The dark inside behind the open door.
            box(blocks, poseStack, buffers, light, DARK, -0.68F, 0.26F, 0.86F, 1.36F, 2.15F, 0.08F);
        }
        poseStack.popPose();
        super.render(pod, yaw, partialTick, poseStack, buffers, light);
    }

    private static void box(BlockRenderDispatcher blocks, PoseStack poseStack, MultiBufferSource buffers, int light, BlockState state,
                            float x, float y, float z, float w, float h, float d) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(w, h, d);
        blocks.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(VeronicaPodEntity pod) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
