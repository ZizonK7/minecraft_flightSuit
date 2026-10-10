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
        clearFor = null;
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

    /** Puts the camera where the script has it at this moment (moved out from behind whatever's in the way). */
    private static void place(Minecraft minecraft, float partialTick) {
        if (scene == null || camera == null || minecraft.level == null) {
            return;
        }
        float t = minecraft.level.getGameTime() - startTick + partialTick;
        Cutscene.Shot shot = scene.shotAt(t);
        if (shot != clearFor) {
            // A new shot: start again from the script's own spot.
            clearFor = shot;
            swing = 0.0D;
            clearShare = 1.0D;
        }
        Vec3[] cam = scene.camera(t);
        Vec3 look = clearLook(minecraft, Cutscene.place(anchor, yaw, cam[1]));
        Vec3 eye = clearEye(minecraft, look, Cutscene.place(anchor, yaw, cam[0]));
        long now = minecraft.level.getGameTime();
        if (now < shakeUntil && shakeTicks > 0) {
            double strength = 0.25D * (shakeUntil - now) / shakeTicks;
            eye = eye.add((RANDOM.nextDouble() - 0.5D) * strength, (RANDOM.nextDouble() - 0.5D) * strength, (RANDOM.nextDouble() - 0.5D) * strength);
        }
        viewEye = eye;
        viewLook = look;
        Vec3 dir = look.subtract(eye);
        float camYaw = (float) (Math.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
        float camPitch = (float) -(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0D / Math.PI));
        // The camera entity's eye is at its feet (it has no height worth speaking of).
        camera.snap(eye.x, eye.y - camera.getEyeHeight(), eye.z, camYaw, camPitch);
    }

    // ---------------------------------------------------------------- keeping the view clear (after the M17 test)

    /** Where the camera is and what it looks at this frame (for hiding what stands in the way). */
    private static Vec3 viewEye = Vec3.ZERO;
    private static Vec3 viewLook = Vec3.ZERO;

    /**
     * While a scene plays, the players (the watchers are held where they stood - often right in front of the camera)
     * aren't drawn, nor anyone else but the actors standing between the camera and what it looks at.
     */
    @SubscribeEvent
    public static void onRenderLiving(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (scene == null) {
            return;
        }
        net.minecraft.world.entity.LivingEntity e = event.getEntity();
        if (e instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter && fighter.isActing()) {
            return;
        }
        net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(0.4D);
        if (e instanceof net.minecraft.world.entity.player.Player || box.contains(viewEye) || box.clip(viewEye, viewLook).isPresent()) {
            event.setCanceled(true);
        }
    }

    /** The shot the clearing below was worked out for; the turn round the subject it settled on, and how far out is clear. */
    private static @Nullable Cutscene.Shot clearFor;
    private static double swing;
    private static double clearShare = 1.0D;
    /** The camera stays at least this far from what it looks at, if it can. */
    private static final double MIN_DISTANCE = 2.2D;
    private static final double[] SWINGS = {35.0D, -35.0D, 70.0D, -70.0D, 110.0D, -110.0D, 150.0D, -150.0D, 180.0D};

    /** The spot looked at, lifted out of the ground if the scene's ground there is higher than the script thought. */
    private static Vec3 clearLook(Minecraft minecraft, Vec3 look) {
        Vec3 at = look;
        for (int i = 0; i < 8 && solid(minecraft, at); i++) {
            at = at.add(0.0D, 0.5D, 0.0D);
        }
        return solid(minecraft, at) ? look : at;
    }

    private static boolean solid(Minecraft minecraft, Vec3 at) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
        return minecraft.level != null && !minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos).isEmpty();
    }

    /**
     * Where the camera can be: the script's spot if nothing stands between it and what it looks at; else, if a
     * turn round the subject (kept for the rest of the shot) gives a clear view, there; else as far out as is
     * clear along the way - like the game's own third-person camera. Pulling in is at once (never a frame inside a
     * wall), easing back out is gradual.
     */
    private static Vec3 clearEye(Minecraft minecraft, Vec3 look, Vec3 eye) {
        Vec3 arm = eye.subtract(look);
        double length = arm.length();
        if (length < 0.5D) {
            return eye;
        }
        double share = clear(minecraft, look, rotated(arm, swing));
        if (share * length < Math.min(MIN_DISTANCE, length * 0.8D)) {
            // Blocked close up: look for a turn round the subject that sees it - a cut there, not a sweep through the wall.
            for (double turn : SWINGS) {
                double s = clear(minecraft, look, rotated(arm, turn));
                if (s > share + 0.05D) {
                    share = s;
                    swing = turn;
                }
                if (s >= 0.95D) {
                    break;
                }
            }
        }
        Vec3 turned = rotated(arm, swing);
        clearShare = share < clearShare ? share : Math.min(share, clearShare + 0.03D);
        return look.add(turned.scale(clearShare));
    }

    /** {@code arm} turned {@code degrees} round the vertical. */
    private static Vec3 rotated(Vec3 arm, double degrees) {
        return degrees == 0.0D ? arm : arm.yRot((float) Math.toRadians(degrees));
    }

    /**
     * How much of the way out along {@code arm} is clear (0-1): five rays (the middle and four a little off it, so
     * the edges of the view don't clip into a wall either), each stopping a bit short of what it hits.
     */
    private static double clear(Minecraft minecraft, Vec3 look, Vec3 arm) {
        if (minecraft.level == null || camera == null) {
            return 1.0D;
        }
        double length = arm.length();
        Vec3 unit = arm.scale(1.0D / length);
        Vec3 side = unit.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 up = side.cross(unit).normalize();
        double free = length;
        Vec3[] offsets = {Vec3.ZERO, side.scale(0.2D), side.scale(-0.2D), up.scale(0.2D), up.scale(-0.2D)};
        for (Vec3 off : offsets) {
            Vec3 from = look.add(off.scale(0.3D));
            Vec3 to = look.add(arm).add(off);
            net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, camera));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                free = Math.min(free, hit.getLocation().distanceTo(from) - 0.35D);
            }
        }
        return Math.max(0.0D, Math.min(1.0D, free / length));
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
