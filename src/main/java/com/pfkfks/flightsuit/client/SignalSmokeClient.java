package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.SignalSmokeS2CPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * M18: the fortresses' signal fires seen from far off (DESIGN.md 5차 피드백 "성채 봉화") - a tall column of grey smoke
 * over each tower, drawn as a stack of soft puffs that rise, drift with a slow sway and thin out with height, with
 * ordinary translucent blending (KiDraw.SMOKE) so it reads as smoke against the sky, not light. The server keeps
 * each column alive while a player is within reach (SignalSmokeS2CPacket).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class SignalSmokeClient {
    private static final class Smoke {
        final BlockPos pos;
        int height;
        long until;
        final long born;

        Smoke(BlockPos pos, int height, long until, long born) {
            this.pos = pos;
            this.height = height;
            this.until = until;
            this.born = born;
        }
    }

    private static final Map<BlockPos, Smoke> SMOKES = new HashMap<>();
    private static final int PUFFS = 18;
    private static final int GREY = 0x6E6E72;
    private static final int GREY_LIGHT = 0x9A9A9E;

    private SignalSmokeClient() {
    }

    public static void handle(SignalSmokeS2CPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        Smoke smoke = SMOKES.get(packet.pos);
        if (smoke == null) {
            SMOKES.put(packet.pos.immutable(), new Smoke(packet.pos.immutable(), packet.height, now + packet.ticks, now));
        } else {
            smoke.height = packet.height;
            smoke.until = now + packet.ticks;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SMOKES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        SMOKES.values().removeIf(smoke -> smoke.until <= now);
        if (SMOKES.isEmpty()) {
            return;
        }
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        KiDraw.Ctx c = new KiDraw.Ctx(buffers.getBuffer(KiDraw.SMOKE), event.getPoseStack().last().pose(),
                new Vec3(camera.getLeftVector()), new Vec3(camera.getUpVector()), cam);
        float time = now + pt;
        for (Smoke smoke : SMOKES.values()) {
            Vec3 base = new Vec3(smoke.pos.getX() + 0.5D, smoke.pos.getY() + 0.6D, smoke.pos.getZ() + 0.5D);
            double distance = base.distanceTo(cam);
            if (distance > 420.0D) {
                continue;
            }
            // Fade in as it's lit, out as it dies; far away it's drawn bolder so it still reads.
            float age = time - smoke.born;
            float in = Mth.clamp(age / 60.0F, 0.0F, 1.0F);
            float out = Mth.clamp((smoke.until - time) / 40.0F, 0.0F, 1.0F);
            float a = Math.min(in, out);
            double far = Mth.clamp(distance / 160.0D, 0.6D, 2.2D);
            long seed = smoke.pos.asLong();
            for (int i = 0; i < PUFFS; i++) {
                // Each puff climbs the column over and over, drifting on a slow sine.
                double s = ((time * 0.012D + i / (double) PUFFS) % 1.0D);
                double h = s * smoke.height;
                double sway = Math.sin(time * 0.02D + i * 1.7D + seed % 7) * (0.4D + s * 1.6D);
                double drift = Math.cos(time * 0.013D + i * 0.9D + seed % 5) * (0.3D + s * 1.2D);
                Vec3 at = base.add(sway, h, drift);
                double r = (0.9D + s * 2.6D) * far;
                int alpha = (int) (a * (1.0D - s) * (i % 2 == 0 ? 95 : 70) / Math.sqrt(far));
                KiDraw.glow(c, at, r, i % 3 == 0 ? GREY_LIGHT : GREY, alpha);
            }
        }
        buffers.endBatch(KiDraw.SMOKE);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SMOKES.clear();
    }
}
