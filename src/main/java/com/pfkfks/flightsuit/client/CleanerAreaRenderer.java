package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.cleaner.CleanArea;
import com.pfkfks.flightsuit.cleaner.CleanerDockBlockEntity;
import com.pfkfks.flightsuit.registry.ModBlocks;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EdithGlassesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Cleaning areas as amber line loops at their bottom/top heights with corner pillars (ported from
 * minebutler's guard area renderer). Shown while holding a cleaner dock/robot, or through EDITH glasses.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class CleanerAreaRenderer {
    private static final double VIEW_RANGE = 64.0D;

    private CleanerAreaRenderer() {
    }

    private static boolean shouldShow(LocalPlayer player) {
        for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.is(ModItems.CLEANER_ROBOT.get()) || held.is(ModBlocks.CLEANER_DOCK.get().asItem())) {
                return true;
            }
        }
        return EdithGlassesItem.isWearing(player);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || minecraft.player == null
                || !shouldShow(minecraft.player)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean drew = false;
        for (CleanerDockBlockEntity dock : CleanerDockBlockEntity.clientDocks()) {
            if (dock.isRemoved() || dock.getBlockPos().distToCenterSqr(minecraft.player.position()) > VIEW_RANGE * VIEW_RANGE) {
                continue;
            }
            for (CleanArea area : dock.getAreas()) {
                if (area.vertices().size() >= 3) {
                    renderArea(poseStack, lines, camera, area);
                    drew = true;
                }
            }
        }
        if (drew) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void renderArea(PoseStack poseStack, VertexConsumer lines, Vec3 camera, CleanArea area) {
        List<BlockPos> vertices = area.vertices();
        double bottomY = area.baseY() - area.downRange() - camera.y;
        double topY = area.baseY() + area.upRange() + 1.0D - camera.y;
        for (int i = 0; i < vertices.size(); i++) {
            BlockPos from = vertices.get(i);
            BlockPos to = vertices.get((i + 1) % vertices.size());
            double x1 = from.getX() + 0.5D - camera.x, z1 = from.getZ() + 0.5D - camera.z;
            double x2 = to.getX() + 0.5D - camera.x, z2 = to.getZ() + 0.5D - camera.z;
            line(poseStack, lines, x1, bottomY, z1, x2, bottomY, z2);
            line(poseStack, lines, x1, topY, z1, x2, topY, z2);
            line(poseStack, lines, x1, bottomY, z1, x1, topY, z1);
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        lines.vertex(matrix, (float) x1, (float) y1, (float) z1).color(1.0F, 0.78F, 0.25F, 0.9F).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(matrix, (float) x2, (float) y2, (float) z2).color(1.0F, 0.78F, 0.25F, 0.9F).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
