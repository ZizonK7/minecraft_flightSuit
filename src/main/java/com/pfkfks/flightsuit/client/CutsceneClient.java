package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.cutscene.CameraEntity;
import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscenes;
import com.pfkfks.flightsuit.network.CutsceneS2CPacket;
import com.pfkfks.flightsuit.network.CutsceneSkipC2SPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of a cutscene (M17): looks through a camera of its own (cutscene.CameraEntity) that it moves every
 * frame along the script's path - the server runs the same script for the actors, so they match - with the screen
 * letterboxed and subtitled (CutsceneOverlay) and the rest of the HUD hidden. The jump key asks to skip. Shakes come
 * through here too, cutscene or not (a transformation nearby).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class CutsceneClient {
    private static @Nullable Cutscene scene;
    private static Vec3 anchor = Vec3.ZERO;
    private static float yaw;
    private static long startTick;
    private static @Nullable CameraEntity camera;
    private static @Nullable CameraType cameraTypeBefore;
    private static Component speaker = Component.empty();
    private static Component text = Component.empty();
    private static boolean skipSent;
    private static long shakeUntil;
    private static int shakeTicks;
    private static final RandomSource RANDOM = RandomSource.create();

    private CutsceneClient() {
    }

    public static boolean isActive() {
        return scene != null;
    }

    public static Component speaker() {
        return speaker;
    }

    public static Component text() {
        return text;
    }

    public static void handle(CutsceneS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        switch (packet.kind) {
            case CutsceneS2CPacket.START -> start(minecraft, packet);
            case CutsceneS2CPacket.LINE -> {
                speaker = packet.speaker;
                text = packet.text;
            }
            case CutsceneS2CPacket.SHAKE -> {
                shakeTicks = packet.ticks;
                shakeUntil = minecraft.level.getGameTime() + packet.ticks;
            }
            default -> end(minecraft);
        }
    }

    private static void start(Minecraft minecraft, CutsceneS2CPacket packet) {
        Cutscene script = Cutscenes.get(packet.id);
        if (script == null) {
            return;
        }
        scene = script;
        anchor = packet.anchor;
        yaw = packet.yaw;
        startTick = minecraft.level.getGameTime();
        speaker = Component.empty();
        text = Component.empty();
        skipSent = false;
        camera = new CameraEntity(ModEntities.CUTSCENE_CAMERA.get(), minecraft.level);
        place(minecraft, 0.0F);
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        cameraTypeBefore = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(camera);
    }

    private static void end(Minecraft minecraft) {
        if (scene == null) {
            return;
        }
        scene = null;
        camera = null;
        speaker = Component.empty();
        text = Component.empty();
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        if (cameraTypeBefore != null) {
            minecraft.options.setCameraType(cameraTypeBefore);
            cameraTypeBefore = null;
        }
    }

    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        end(minecraft);
        shakeUntil = 0L;
    }

    /** Puts the camera where the script has it at this moment. */
    private static void place(Minecraft minecraft, float partialTick) {
        if (scene == null || camera == null || minecraft.level == null) {
            return;
        }
        float t = minecraft.level.getGameTime() - startTick + partialTick;
        Vec3[] cam = scene.camera(t);
        Vec3 eye = Cutscene.place(anchor, yaw, cam[0]);
        Vec3 look = Cutscene.place(anchor, yaw, cam[1]);
        long now = minecraft.level.getGameTime();
        if (now < shakeUntil && shakeTicks > 0) {
            double strength = 0.25D * (shakeUntil - now) / shakeTicks;
            eye = eye.add((RANDOM.nextDouble() - 0.5D) * strength, (RANDOM.nextDouble() - 0.5D) * strength, (RANDOM.nextDouble() - 0.5D) * strength);
        }
        Vec3 dir = look.subtract(eye);
        float camYaw = (float) (Math.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        float camPitch = (float) -(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0D / Math.PI));
        // The camera entity's eye is at its feet (it has no height worth speaking of).
        camera.snap(eye.x, eye.y - camera.getEyeHeight(), eye.z, camYaw, camPitch);
    }

    /** Every frame: the camera along its path (and a shake outside cutscenes on the player's own view). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (scene != null) {
            if (minecraft.level == null || minecraft.player == null) {
                reset();
                return;
            }
            if (minecraft.getCameraEntity() != camera && camera != null) {
                minecraft.setCameraEntity(camera);
            }
            place(minecraft, event.renderTickTime);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (scene == null || minecraft.level == null) {
            return;
        }
        // The jump key skips (once); and if the server's end never came, we stop by ourselves.
        while (minecraft.options.keyJump.consumeClick()) {
            if (!skipSent) {
                skipSent = true;
                ModNetwork.sendToServer(new CutsceneSkipC2SPacket());
            }
        }
        if (minecraft.level.getGameTime() - startTick > scene.length() + 100L) {
            end(minecraft);
        }
    }

    /** Only the letterbox and subtitles while a scene plays. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (scene != null && !(FlightSuitMod.MODID.equals(event.getOverlay().id().getNamespace())
                && "cutscene".equals(event.getOverlay().id().getPath()))) {
            event.setCanceled(true);
        }
    }

    /** No swinging or using things through the camera. */
    @SubscribeEvent
    public static void onClick(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (scene != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (scene != null) {
            event.setCanceled(true);
        }
    }

    /** The view shake outside a cutscene: a small jolt to the player's own camera. */
    @SubscribeEvent
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scene != null || minecraft.level == null) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (now < shakeUntil && shakeTicks > 0) {
            float strength = 2.0F * (shakeUntil - now) / shakeTicks;
            event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5F) * strength);
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5F) * strength);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5F) * strength);
        }
    }
}
