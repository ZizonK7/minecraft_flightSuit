package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.BeamStateS2CPacket;
import com.pfkfks.flightsuit.network.WeaponStatusS2CPacket;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the suit weapons: which players are firing (beams drawn from the palm to wherever their aim
 * lands, re-traced every frame so they sweep smoothly), impact particles, and the local player's cooldowns
 * and card gauge for the HUD.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class ClientWeapons {
    /** Firing players by entity id (ids, not entities: see SuitAnimator on why entity-keyed maps break). */
    private static final Map<Integer, Byte> FIRING = new HashMap<>();
    /** What each player fired last (to tell when Trunks stops cutting). */
    private static final Map<Integer, Byte> FIRING_BEFORE = new HashMap<>();
    /** When each player started firing what they fire now (game time) - how far Link has drawn his bow. */
    private static final Map<Integer, Long> FIRING_SINCE = new HashMap<>();

    public static long skill1Ready;
    public static long skill2Ready;
    public static int gauge;
    public static long spadeUntil;
    /** M17: the ultimate's cooldown, Super Saiyan on/off and its lockout, the stolen skill ("" = none) and its cooldown. */
    public static long ultReady;
    public static boolean superSaiyan;
    public static long ssjLockedUntil;
    public static String stolen = "";
    public static long stolenReady;
    /** M18: the third skill's (B) cooldown, the timed buff's end, the Hulkbuster's armour mode. */
    public static long skill3Ready;
    public static long buffUntil;
    public static boolean armourMode;
    /** M18 feedback: the hero's chain, and the Hulkbuster holding a chunk of ground to throw. */
    public static int chain;
    public static boolean holding;

    private ClientWeapons() {
    }

    public static void handleBeam(BeamStateS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.kind == SuitWeapons.FIRE_NONE) {
            FIRING.remove(packet.entityId);
            FIRING_SINCE.remove(packet.entityId);
        } else {
            Byte was = FIRING.put(packet.entityId, packet.kind);
            if ((was == null || was != packet.kind) && minecraft.level != null) {
                FIRING_SINCE.put(packet.entityId, minecraft.level.getGameTime());
            }
        }
        if (minecraft.level != null && minecraft.level.getEntity(packet.entityId) instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            SuitAnimator.setAiming(player, SuitWeapons.aimsArm(packet.kind), packet.kind == SuitWeapons.FIRE_BOW);
        }
        FIRING_BEFORE.put(packet.entityId, packet.kind);
    }

    public static void handleStatus(WeaponStatusS2CPacket packet) {
        skill1Ready = packet.skill1Ready;
        skill2Ready = packet.skill2Ready;
        gauge = packet.gauge;
        spadeUntil = packet.spadeUntil;
        ultReady = packet.ultReady;
        superSaiyan = packet.superSaiyan;
        ssjLockedUntil = packet.ssjLockedUntil;
        stolen = packet.stolen;
        stolenReady = packet.stolenReady;
        skill3Ready = packet.skill3Ready;
        buffUntil = packet.buffUntil;
        armourMode = packet.armourMode;
        chain = packet.chain;
        holding = packet.holding;
    }

    /** What a player's primary is firing right now (FIRE_NONE if nothing). */
    public static byte firing(int entityId) {
        return FIRING.getOrDefault(entityId, SuitWeapons.FIRE_NONE);
    }

    /** Ticks since the player started firing what they fire now (0 if nothing). */
    public static float firingTicks(int entityId, float partialTick) {
        Long since = FIRING_SINCE.get(entityId);
        ClientLevel level = Minecraft.getInstance().level;
        return since == null || level == null ? 0.0F : Math.max(0.0F, level.getGameTime() - since + partialTick);
    }

    /** Seconds until a skill is ready again (0 = ready). */
    public static float secondsLeft(long readyTick) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : Math.max(0.0F, (readyTick - level.getGameTime()) / 20.0F);
    }

    public static void reset() {
        FIRING.clear();
        FIRING_SINCE.clear();
        skill1Ready = 0L;
        skill2Ready = 0L;
        gauge = 0;
        spadeUntil = 0L;
        ultReady = 0L;
        superSaiyan = false;
        ssjLockedUntil = 0L;
        stolen = "";
        stolenReady = 0L;
        skill3Ready = 0L;
        buffUntil = 0L;
        armourMode = false;
        chain = 0;
        holding = false;
        FIRING_BEFORE.clear();
        com.pfkfks.flightsuit.suit.SwordArts.GOLDEN.clear();
        com.pfkfks.flightsuit.suit.SwordArts.SSJ3.clear();
        DragonFistRenderer.reset();
        com.pfkfks.flightsuit.suit.CandyShrink.CLIENT.clear();
        HeroRenderer.reset();
    }

    // ---------------------------------------------------------------- beams

    private record Beam(Vec3 from, Vec3 to) {
    }

    /** Palm to aim point, as the player sees it this frame. */
    private static Beam beamOf(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 palm = FIRING.getOrDefault(player.getId(), SuitWeapons.FIRE_NONE) == SuitWeapons.FIRE_HOLY
                ? eye.add(look.scale(0.8D)).add(right.scale(0.3D)).add(0.0D, 0.1D, 0.0D)
                : eye.add(look.scale(0.55D)).add(right.scale(0.33D)).add(0.0D, -0.28D, 0.0D);
        Vec3 far = eye.add(look.scale(SuitTuning.BEAM_RANGE));
        BlockHitResult block = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.isPickable()
                        && (FIRING.getOrDefault(player.getId(), SuitWeapons.FIRE_NONE) == SuitWeapons.FIRE_HOLY
                        || !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity)));
        return new Beam(palm, hit == null ? end : hit.getLocation());
    }

    /** The scrap suit's flame: from the right palm, as far as FLAME_REACH or the first wall. */
    private static Beam flameOf(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 palm = eye.add(look.scale(0.6D)).add(right.scale(0.35D)).add(0.0D, -0.3D, 0.0D);
        Vec3 far = palm.add(look.scale(SuitTuning.FLAME_REACH));
        BlockHitResult block = player.level().clip(new ClipContext(palm, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return new Beam(palm, block.getType() == HitResult.Type.MISS ? far : block.getLocation());
    }

    /** Impact sparks / frost where each beam lands; soot off the end of a flame. */
    public static void tick(ClientLevel level) {
        for (Map.Entry<Integer, Byte> entry : FIRING.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof Player player)) {
                continue;
            }
            if (entry.getValue() == SuitWeapons.FIRE_FLAME) {
                Beam flame = flameOf(player, 1.0F);
                Vec3 end = flame.to().add((level.random.nextDouble() - 0.5D) * 0.8D, (level.random.nextDouble() - 0.3D) * 0.6D,
                        (level.random.nextDouble() - 0.5D) * 0.8D);
                level.addParticle(level.random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, end.x, end.y, end.z,
                        0.0D, 0.04D + level.random.nextDouble() * 0.04D, 0.0D);
                continue;
            }
            if (!SuitWeapons.isBeam(entry.getValue())) {
                continue;
            }
            Beam beam = beamOf(player, 1.0F);
            Vec3 end = beam.to();
            if (entry.getValue() == SuitWeapons.FIRE_HOLY) {
                // Motes of light drifting up where the staff's beam lands.
                level.addParticle(ParticleTypes.END_ROD, end.x, end.y, end.z, (level.random.nextDouble() - 0.5D) * 0.05D,
                        0.04D + level.random.nextDouble() * 0.04D, (level.random.nextDouble() - 0.5D) * 0.05D);
            } else if (entry.getValue() == SuitWeapons.FIRE_CRYO) {
                level.addParticle(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, (level.random.nextDouble() - 0.5D) * 0.2D,
                        level.random.nextDouble() * 0.1D, (level.random.nextDouble() - 0.5D) * 0.2D);
                Vec3 along = beam.from().lerp(end, level.random.nextDouble());
                level.addParticle(ParticleTypes.SNOWFLAKE, along.x, along.y, along.z, 0.0D, -0.02D, 0.0D);
            } else {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, (level.random.nextDouble() - 0.5D) * 0.4D,
                        level.random.nextDouble() * 0.3D, (level.random.nextDouble() - 0.5D) * 0.4D);
                if (level.random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0.0D, 0.03D, 0.0D);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FIRING.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        float time = level.getGameTime() + partialTick;
        for (Map.Entry<Integer, Byte> entry : FIRING.entrySet()) {
            byte kind = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) {
                continue;
            }
            if (kind == SuitWeapons.FIRE_FLAME) {
                flame(consumer, pose, player, partialTick, time);
                continue;
            }
            if (!SuitWeapons.isBeam(kind)) {
                continue;
            }
            Beam beam = beamOf(player, partialTick);
            float flicker = 1.0F + 0.15F * (float) Math.sin(time * 1.7F + entry.getKey());
            boolean cryo = kind == SuitWeapons.FIRE_CRYO;
            boolean holy = kind == SuitWeapons.FIRE_HOLY;
            // Mark 1's repulsor is red (the first M18 test: it looked the same as Mark 2's cryo beam), the cryo beam ice-blue, the staff gold.
            boolean red = kind == SuitWeapons.FIRE_REPULSOR && SuitWeapons.armedClass(player) == com.pfkfks.flightsuit.suit.SuitClass.STANDARD;
            int r = holy ? 255 : red ? 255 : cryo ? 170 : 95;
            int g = holy ? 236 : red ? 70 : cryo ? 230 : 227;
            int b = holy ? 150 : red ? 45 : 255;
            // Soft glow, colored body, white-hot core.
            ribbon(consumer, pose, beam, 0.17F * flicker, r, g, b, 70);
            ribbon(consumer, pose, beam, 0.08F * flicker, r, g, b, 160);
            ribbon(consumer, pose, beam, 0.03F, 255, red ? 235 : 255, red ? 225 : 255, 230);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lightning());
    }

    // ---------------------------------------------------------------- the scrap suit's flame

    /**
     * A tongue of fire out of the right palm: a few turbulent ribbons, white-hot at the hand, orange along the
     * middle and dark red where they thin out and die, waves of heat running down them (no particles - drawn, like
     * the ki effects, DESIGN.md M17 feedback).
     */
    private static void flame(VertexConsumer consumer, Matrix4f pose, Player player, float partialTick, float time) {
        Beam flame = flameOf(player, partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 up = right.cross(look).normalize();
        double reach = flame.to().distanceTo(flame.from());
        if (reach < 0.3D) {
            return;
        }
        int segments = 10;
        float seed = player.getId() % 17;
        for (int tongue = 0; tongue < 4; tongue++) {
            float phase = seed + tongue * 1.9F;
            Vec3[] points = new Vec3[segments + 1];
            float[] widths = new float[segments + 1];
            int[][] colours = new int[segments + 1][];
            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                double along = t * reach;
                double amp = 0.04D + t * 0.5D;
                double ox = Math.sin(time * 0.9D + phase - along * 2.1D) * amp + Math.sin(time * 0.37D + phase * 2.0D + along * 1.3D) * amp * 0.5D;
                double oy = Math.cos(time * 0.8D + phase * 1.3D - along * 1.7D) * amp * 0.8D + t * t * 0.3D;
                points[i] = flame.from().add(look.scale(along)).add(right.scale(ox)).add(up.scale(oy));
                widths[i] = (0.05F + t * 0.4F) * (0.85F + 0.15F * (float) Math.sin(time * 2.3D + phase + i));
                colours[i] = flameColour(t);
            }
            tongue(consumer, pose, points, widths, colours, 1.0F, 1.0F);
            tongue(consumer, pose, points, widths, colours, 0.4F, 1.6F);
        }
    }

    /** White-yellow at the hand, orange, then dark red fading to nothing. */
    private static int[] flameColour(float t) {
        int r;
        int g;
        int b;
        if (t < 0.25F) {
            float k = t / 0.25F;
            r = 255;
            g = (int) (245 - 55 * k);
            b = (int) (190 - 120 * k);
        } else if (t < 0.6F) {
            float k = (t - 0.25F) / 0.35F;
            r = 255;
            g = (int) (190 - 80 * k);
            b = (int) (70 - 40 * k);
        } else {
            float k = (t - 0.6F) / 0.4F;
            r = (int) (255 - 70 * k);
            g = (int) (110 - 70 * k);
            b = (int) (30 - 20 * k);
        }
        int a = (int) (200 * (1.0F - t * t));
        return new int[]{r, g, b, a};
    }

    /** A ribbon along the points (two crossed planes, both faces), the width and colour varying along it. */
    private static void tongue(VertexConsumer consumer, Matrix4f pose, Vec3[] points, float[] widths, int[][] colours, float widthScale,
                               float brighten) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 dir = points[i + 1].subtract(points[i]);
            if (dir.lengthSqr() < 1.0E-6D) {
                continue;
            }
            dir = dir.normalize();
            Vec3 u = dir.cross(Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D)).normalize();
            Vec3 v = dir.cross(u).normalize();
            float w0 = widths[i] * widthScale;
            float w1 = widths[i + 1] * widthScale;
            int[] c0 = bright(colours[i], brighten);
            int[] c1 = bright(colours[i + 1], brighten);
            for (Vec3 side : new Vec3[]{u, v}) {
                Vec3 a0 = points[i].add(side.scale(w0));
                Vec3 a1 = points[i].subtract(side.scale(w0));
                Vec3 b1 = points[i + 1].subtract(side.scale(w1));
                Vec3 b0 = points[i + 1].add(side.scale(w1));
                vertex(consumer, pose, a0, c0);
                vertex(consumer, pose, a1, c0);
                vertex(consumer, pose, b1, c1);
                vertex(consumer, pose, b0, c1);
                vertex(consumer, pose, b0, c1);
                vertex(consumer, pose, b1, c1);
                vertex(consumer, pose, a1, c0);
                vertex(consumer, pose, a0, c0);
            }
        }
    }

    private static int[] bright(int[] c, float k) {
        return new int[]{Math.min(255, (int) (c[0] * k)), Math.min(255, (int) (c[1] * k)), Math.min(255, (int) (c[2] * k)), c[3]};
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 p, int[] c) {
        consumer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    /** Two crossed quads along the beam, each drawn both ways round. */
    private static void ribbon(VertexConsumer consumer, Matrix4f pose, Beam beam, float width, int r, int g, int b, int a) {
        Vec3 dir = beam.to().subtract(beam.from());
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        dir = dir.normalize();
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D)).normalize().scale(width);
        Vec3 v = dir.cross(u).normalize().scale(width);
        for (Vec3 side : new Vec3[]{u, v}) {
            Vec3 a0 = beam.from().add(side);
            Vec3 a1 = beam.from().subtract(side);
            Vec3 b1 = beam.to().subtract(side);
            Vec3 b0 = beam.to().add(side);
            quad(consumer, pose, a0, a1, b1, b0, r, g, b, a);
            quad(consumer, pose, b0, b1, a1, a0, r, g, b, a);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int r, int g, int b, int a) {
        for (Vec3 p : new Vec3[]{p0, p1, p2, p3}) {
            consumer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
        }
    }
}
