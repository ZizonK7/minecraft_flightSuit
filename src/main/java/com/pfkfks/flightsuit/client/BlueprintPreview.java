package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.village.Blueprint;
import com.pfkfks.flightsuit.village.BlueprintItem;
import com.pfkfks.flightsuit.village.Construction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While a blueprint is in hand: the building's outline where you look (or where you pinned it) - green on
 * clear ground, orange if something there gets bulldozed first, red if it can't go there at all, gold once
 * pinned - plus a small box on the side the door will face.
 * The server checks again (village bounds, other buildings) when you confirm.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class BlueprintPreview {
    private static Construction cached;
    private static boolean cachedFits;
    private static int cachedClear;

    private BlueprintPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        Blueprint blueprint = BlueprintItem.blueprint(held);
        if (blueprint == null) {
            cached = null;
            return;
        }
        BlockPos pinned = BlueprintItem.pending(held);
        BlockPos center = pinned;
        if (center == null) {
            if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            center = BlueprintItem.groundAt(hit.getBlockPos(), hit.getDirection());
        }
        Rotation rotation = BlueprintItem.rotation(held, player);
        if (cached == null || cached.blueprint() != blueprint || !cached.center().equals(center) || cached.rotation() != rotation
                || player.tickCount % 10 == 0) {
            cached = new Construction(blueprint, center, rotation);
            cachedFits = cached.terrainFits(minecraft.level);
            cachedClear = cachedFits ? cached.clearCount(minecraft.level) : 0;
        }

        // Gold = pinned, green = clear ground, orange = will be bulldozed first, red = can't go here.
        float r = pinned != null ? 1.0F : !cachedFits ? 1.0F : cachedClear > 0 ? 1.0F : 0.35F;
        float g = pinned != null ? 0.8F : !cachedFits ? 0.3F : cachedClear > 0 ? 0.55F : 1.0F;
        float b = pinned != null ? 0.2F : !cachedFits ? 0.3F : cachedClear > 0 ? 0.1F : 0.45F;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, lines, cached.bounds(), r, g, b, 1.0F);
        // The door side: a little box just outside the front, one block up.
        Direction front = cached.front();
        BlockPos door = center.relative(front, blueprint.reach() + 1).above();
        LevelRenderer.renderLineBox(pose, lines, new AABB(door).deflate(0.25D), 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
