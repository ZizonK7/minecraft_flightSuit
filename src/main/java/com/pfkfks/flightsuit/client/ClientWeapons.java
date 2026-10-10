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

    private ClientWeapons() {
    }

    public static void handleBeam(BeamStateS2CPacket packet) {
        if (packet.kind == SuitWeapons.FIRE_NONE) {
            FIRING.remove(packet.entityId);
        } else {
            FIRING.put(packet.entityId, packet.kind);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(packet.entityId) instanceof net.minecraft.client.player.AbstractClientPlayer player) {
            SuitAnimator.setAiming(player, SuitWeapons.aimsArm(packet.kind));
            if (packet.kind == SuitWeapons.FIRE_NONE && FIRING_BEFORE.getOrDefault(packet.entityId, SuitWeapons.FIRE_NONE) == SuitWeapons.FIRE_SLASH) {
                // Trunks: the sword goes back on his back a moment after the last cut.
                HeroRenderer.showSword(packet.entityId, com.pfkfks.flightsuit.suit.SuitTuning.SWORD_SHEATHE_TICKS);
            }
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
    }

    /** What a player's primary is firing right now (FIRE_NONE if nothing). */
    public static byte firing(int entityId) {
        return FIRING.getOrDefault(entityId, SuitWeapons.FIRE_NONE);
    }

    /** Seconds until a skill is ready again (0 = ready). */
    public static float secondsLeft(long readyTick) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : Math.max(0.0F, (readyTick - level.getGameTime()) / 20.0F);
    }

    public static void reset() {
        FIRING.clear();
        skill1Ready = 0L;
        skill2Ready = 0L;
        gauge = 0;
        spadeUntil = 0L;
        ultReady = 0L;
        superSaiyan = false;
        ssjLockedUntil = 0L;
        stolen = "";
        stolenReady = 0L;
        FIRING_BEFORE.clear();
        com.pfkfks.flightsuit.suit.SwordArts.GOLDEN.clear();
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
        Vec3 palm = eye.add(look.scale(0.55D)).add(right.scale(0.33D)).add(0.0D, -0.28D, 0.0D);
        Vec3 far = eye.add(look.scale(SuitTuning.BEAM_RANGE));
        BlockHitResult block = player.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity && !entity.isSpectator() && entity.isPickable()
                        && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity));
        return new Beam(palm, hit == null ? end : hit.getLocation());
    }

    /** Impact sparks / frost where each beam lands. */
    public static void tick(ClientLevel level) {
        for (Map.Entry<Integer, Byte> entry : FIRING.entrySet()) {
            if (!SuitWeapons.isBeam(entry.getValue()) || !(level.getEntity(entry.getKey()) instanceof Player player)) {
                continue;
            }
            Beam beam = beamOf(player, 1.0F);
            Vec3 end = beam.to();
            if (entry.getValue() == SuitWeapons.FIRE_CRYO) {
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
            if (!SuitWeapons.isBeam(kind) || !(entity instanceof Player player)) {
                continue;
            }
            Beam beam = beamOf(player, partialTick);
            float flicker = 1.0F + 0.15F * (float) Math.sin(time * 1.7F + entry.getKey());
            boolean cryo = kind == SuitWeapons.FIRE_CRYO;
            int r = cryo ? 170 : 95;
            int g = cryo ? 230 : 227;
            int b = 255;
            // Soft glow, colored body, white-hot core.
            ribbon(consumer, pose, beam, 0.17F * flicker, r, g, b, 70);
            ribbon(consumer, pose, beam, 0.08F * flicker, r, g, b, 160);
            ribbon(consumer, pose, beam, 0.03F, 255, 255, 255, 230);
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lightning());
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
