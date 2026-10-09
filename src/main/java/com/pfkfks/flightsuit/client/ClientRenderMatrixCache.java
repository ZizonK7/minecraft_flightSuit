package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * Snapshots the camera-relative view/projection matrices during world rendering so the (2D) HUD can project
 * world positions to the screen afterwards - the same transform the renderer uses. Ported from minebutler,
 * where it drives the full-sight target labels. In this Forge build the matrices are org.joml types.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class ClientRenderMatrixCache {
    @Nullable
    private static Matrix4f viewMatrix;
    @Nullable
    private static Matrix4f projectionMatrix;
    @Nullable
    private static Camera camera;

    private ClientRenderMatrixCache() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            viewMatrix = new Matrix4f(event.getPoseStack().last().pose());
            projectionMatrix = new Matrix4f(event.getProjectionMatrix());
            camera = event.getCamera();
        }
    }

    /** GUI-space position of a world point, or null when it's behind the camera or off screen. */
    @Nullable
    public static Vec2 worldToScreen(Vec3 worldPos, int screenWidth, int screenHeight) {
        if (viewMatrix == null || projectionMatrix == null || camera == null) {
            return null;
        }
        Vec3 relative = worldPos.subtract(camera.getPosition());
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0F);
        clip.mul(viewMatrix).mul(projectionMatrix);
        if (clip.w() < 0.05F) {
            return null;
        }
        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F) {
            return null;
        }
        return new Vec2((ndcX * 0.5F + 0.5F) * screenWidth, (1.0F - (ndcY * 0.5F + 0.5F)) * screenHeight);
    }
}
