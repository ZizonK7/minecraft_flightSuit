package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.KiFxS2CPacket;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Draws the ki effects the server asks for (fx.KiFx, after the M17 test): beams, balls of light gathering, flying
 * shots with tails, blasts, pillars of light, the Solar Flare's white-out - and the auras, which it works out itself
 * for whoever should have one (a Super Saiyan, a fighter the story powered up, someone in Kaioken).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class KiFxClient {
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static final Map<Integer, Shot> SHOTS = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Pillar> PILLARS = new ArrayList<>();
    /** Auras sent for a while (Kaioken): entity id to colour and the game time it ends. */
    private static final Map<Integer, long[]> AURAS = new HashMap<>();
    private static final double AURA_RANGE = 64.0D;
    private static long flareUntil;
    private static @Nullable ClientLevel seen;

    private KiFxClient() {
    }

    // ---------------------------------------------------------------- what's being drawn

    private static final class Beam {
        final int source;
        final byte at;
        final int target;
        final Vec3 from;
        final Vec3 to;
        final int colour;
        final float width;
        final byte style;
        final int life;
        int age;

        Beam(KiFxS2CPacket p) {
            source = p.entity;
            at = (byte) p.id;
            target = p.other;
            from = p.a;
            to = p.b;
            colour = p.colour;
            width = p.size;
            style = p.style;
            life = Math.max(2, p.ticks);
        }
    }

    private static final class Charge {
        final int entity;
        final byte at;
        final int colour;
        final float size;
        final int life;
        int age;

        Charge(KiFxS2CPacket p) {
            entity = p.entity;
            at = (byte) p.id;
            colour = p.colour;
            size = p.size;
            life = Math.max(1, p.ticks);
        }
    }

    private static final class Shot {
        final byte style;
        final int colour;
        final float size;
        final Vec3 vel;
        final int life;
        Vec3 pos;
        Vec3 prev;
        double travelled;
        int age;

        Shot(KiFxS2CPacket p) {
            style = p.style;
            colour = p.colour;
            size = p.size;
            vel = p.b;
            life = p.ticks + 1;
            pos = p.a;
            prev = p.a;
        }
    }

    private static final class Burst {
        final Vec3 at;
        final int colour;
        final float size;
        final int life;
        final byte style;
        int age;

        Burst(Vec3 at, int colour, float size, int life, byte style) {
            this.at = at;
            this.colour = colour;
            this.size = size;
            this.life = Math.max(2, life);
            this.style = style;
        }
    }

    private static final class Pillar {
        final int entity;
        final Vec3 at;
        final int colour;
        final float height;
        final int life;
        int age;

        Pillar(KiFxS2CPacket p) {
            entity = p.entity;
            at = p.a;
            colour = p.colour;
            height = p.size;
            life = Math.max(4, p.ticks);
        }
    }

    // ---------------------------------------------------------------- from the server

    public static void handle(KiFxS2CPacket p) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        switch (p.kind) {
            case KiFx.BEAM -> {
                BEAMS.add(new Beam(p));
                if (p.entity >= 0) {
                    CHARGES.remove(p.entity);
                }
            }
            case KiFx.CHARGE -> CHARGES.put(p.entity, new Charge(p));
            case KiFx.CHARGE_STOP -> CHARGES.remove(p.entity);
            case KiFx.SHOT -> SHOTS.put(p.id, new Shot(p));
            case KiFx.SHOT_END -> SHOTS.remove(p.id);
            case KiFx.BURST -> BURSTS.add(new Burst(p.a, p.colour, p.size, p.ticks, p.style));
            case KiFx.PILLAR -> PILLARS.add(new Pillar(p));
            case KiFx.AURA -> {
                if (p.colour == 0) {
                    AURAS.remove(p.entity);
                } else {
                    AURAS.put(p.entity, new long[]{p.colour, p.ticks > 0 ? minecraft.level.getGameTime() + p.ticks : Long.MAX_VALUE});
                }
            }
            case KiFx.FLARE -> {
                BURSTS.add(new Burst(p.a, KiFx.WHITE, 3.5F, 16, KiFx.FLASH));
                BURSTS.add(new Burst(p.a, 0xFFF6C0, 2.0F, 22, KiFx.SPARK));
                Entity self = minecraft.getCameraEntity();
                if (self != null && self.getId() != p.entity && self.position().distanceTo(p.a) <= p.size) {
                    flareUntil = minecraft.level.getGameTime() + p.ticks;
                }
            }
            default -> {
            }
        }
    }

    private static void clear() {
        BEAMS.clear();
        CHARGES.clear();
        SHOTS.clear();
        BURSTS.clear();
        PILLARS.clear();
        AURAS.clear();
        flareUntil = 0L;
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        seen = null;
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (level != seen) {
            clear();
            seen = level;
        }
        if (level == null) {
            return;
        }
        BEAMS.removeIf(b -> ++b.age >= b.life);
        CHARGES.values().removeIf(c -> ++c.age >= c.life + 4 || level.getEntity(c.entity) == null);
        BURSTS.removeIf(b -> ++b.age >= b.life);
        PILLARS.removeIf(p -> ++p.age >= p.life);
        long now = level.getGameTime();
        AURAS.values().removeIf(a -> now >= a[1]);
        for (Iterator<Shot> it = SHOTS.values().iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            if (fly(level, minecraft, shot)) {
                it.remove();
            }
        }
    }

    /** One tick of a shot's flight (the server flies the same one); true when it's done. */
    private static boolean fly(ClientLevel level, Minecraft minecraft, Shot shot) {
        shot.age++;
        shot.prev = shot.pos;
        Vec3 next = shot.pos.add(shot.vel);
        boolean ended = false;
        if (minecraft.player != null) {
            BlockHitResult hit = level.clip(new ClipContext(shot.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() != HitResult.Type.MISS) {
                next = hit.getLocation();
                ended = true;
            }
        }
        shot.travelled += next.distanceTo(shot.pos);
        shot.pos = next;
        if (shot.age >= shot.life) {
            ended = true;
        }
        if (ended && shot.style == KiFx.SHOT_SPIRIT) {
            BURSTS.add(new Burst(shot.pos, KiFx.SPIRIT, shot.size * 2.4F, 40, KiFx.BLAST));
        }
        return ended;
    }

    // ---------------------------------------------------------------- drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        KiDraw.Ctx c = new KiDraw.Ctx(buffers.getBuffer(KiDraw.GLOW), event.getPoseStack().last().pose(),
                new Vec3(camera.getLeftVector()), new Vec3(camera.getUpVector()), cam);
        float time = level.getGameTime() + pt;
        for (Pillar pillar : PILLARS) {
            drawPillar(c, level, pillar, pt, time);
        }
        drawAuras(c, minecraft, level, pt, time);
        for (Charge charge : CHARGES.values()) {
            drawCharge(c, level, charge, pt, time);
        }
        for (Beam beam : BEAMS) {
            drawBeam(c, level, beam, pt, time);
        }
        for (Shot shot : SHOTS.values()) {
            drawShot(c, shot, pt, time);
        }
        for (Burst burst : BURSTS) {
            drawBurst(c, burst, pt, time);
        }
        buffers.endBatch(KiDraw.GLOW);
    }

    /** Where an effect on an entity sits this frame (the same spots as the server's SwordArts.hands and the rest). */
    static Vec3 anchor(Entity e, byte at, float pt) {
        Vec3 base = e.getPosition(pt);
        Vec3 look = Vec3.directionFromRotation(e.getViewXRot(pt), e.getViewYRot(pt));
        double eye = e.getEyeHeight();
        return switch (at) {
            case KiFx.AT_HANDS -> base.add(0.0D, eye - 0.35D, 0.0D).add(look);
            case KiFx.AT_EYES -> base.add(0.0D, eye, 0.0D).add(look.scale(0.3D));
            case KiFx.AT_FOREHEAD -> base.add(0.0D, eye + 0.12D, 0.0D).add(look.scale(0.45D));
            case KiFx.AT_OVERHEAD -> base.add(0.0D, e.getBbHeight() + 1.0D, 0.0D);
            case KiFx.AT_CHEST -> base.add(0.0D, e.getBbHeight() * 0.7D, 0.0D)
                    .add(Vec3.directionFromRotation(0.0F, e.getViewYRot(pt)).scale(0.35D));
            default -> base.add(0.0D, e.getBbHeight() / 2.0D, 0.0D);
        };
    }

    private static int lighter(int rgb) {
        int r = (rgb >> 16 & 0xFF) + 255 >> 1;
        int g = (rgb >> 8 & 0xFF) + 255 >> 1;
        int b = (rgb & 0xFF) + 255 >> 1;
        return r << 16 | g << 8 | b;
    }

    private static float ease(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /** A steady pseudo-random number in [0, 1) for {@code a} and {@code b}. */
    private static double hash(int a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static Vec3 randomDir(int a, int b) {
        double u = hash(a, b) * 2.0D - 1.0D;
        double ang = hash(b, a + 17) * Math.PI * 2.0D;
        double s = Math.sqrt(1.0D - u * u);
        return new Vec3(Math.cos(ang) * s, u, Math.sin(ang) * s);
    }

    // Beams.

    private static void drawBeam(KiDraw.Ctx c, ClientLevel level, Beam beam, float pt, float time) {
        Entity source = beam.source >= 0 ? level.getEntity(beam.source) : null;
        Entity target = beam.target >= 0 ? level.getEntity(beam.target) : null;
        Vec3 from = source != null ? anchor(source, beam.at, pt) : beam.from;
        Vec3 to = target != null ? target.getBoundingBox().getCenter().add(target.getPosition(pt).subtract(target.position())) : beam.to;
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.05D) {
            return;
        }
        Vec3 unit = dir.scale(1.0D / length);
        float age = beam.age + pt;
        float grow = Math.min(1.0F, age / 3.0F);
        float fade = Math.min(1.0F, (beam.life - age) / 5.0F);
        if (fade <= 0.0F) {
            return;
        }
        Vec3 head = from.add(unit.scale(length * grow));
        double pulse = 1.0D + 0.07D * Math.sin(time * 2.3D) + 0.05D * Math.sin(time * 5.7D + 1.0D);
        double r = beam.width * 0.5D * pulse * (0.35D + 0.65D * fade);
        int col = beam.colour;
        if (beam.style == KiFx.BEAM_THIN) {
            KiDraw.tube(c, from, head, r * 1.6D, r * 1.6D, col, (int) (40 * fade), (int) (40 * fade));
            KiDraw.tube(c, from, head, r * 0.75D, r * 0.75D, col, (int) (160 * fade), (int) (160 * fade));
            KiDraw.tube(c, from, head, r * 0.3D, r * 0.3D, 0xFFFFFF, (int) (240 * fade), (int) (240 * fade));
            KiDraw.glow(c, from, r * 4.0D, col, (int) (130 * fade));
            KiDraw.glow(c, head, r * 3.5D, col, (int) (120 * fade));
            return;
        }
        float a = fade;
        // Halo, glow, colour, white-hot core.
        KiDraw.tube(c, from, head, r * 1.9D, r * 1.9D, col, (int) (22 * a), (int) (22 * a));
        KiDraw.tube(c, from, head, r * 1.35D, r * 1.35D, col, (int) (55 * a), (int) (55 * a));
        KiDraw.tube(c, from, head, r * 0.95D, r * 0.95D, col, (int) (110 * a), (int) (110 * a));
        KiDraw.tube(c, from, head, r * 0.45D, r * 0.45D, 0xFFFFFF, (int) (210 * a), (int) (210 * a));
        // Waves running down it.
        for (int k = 0; k < 5; k++) {
            double s = frac(time * 0.09D + k / 5.0D) * grow;
            Vec3 at = from.add(unit.scale(length * s));
            KiDraw.ring(c, at, unit, r * 0.9D, r * 1.55D, time * 0.2D, 0.0D, lighter(col), (int) (120 * a), 0);
        }
        if (beam.style == KiFx.BEAM_SPIRAL) {
            spiral(c, from, head, unit, r * 2.0D, time, a);
        }
        // The muzzle, and the head boring in.
        KiDraw.orb(c, from, r * 1.25D, col, a);
        KiDraw.orb(c, head, r * (1.5D + 0.15D * Math.sin(time * 3.1D)), col, a);
        KiDraw.glow(c, head, r * 5.0D, col, (int) (60 * a));
        KiDraw.star(c, head, r * 4.0D, time * 0.15D, 8, lighter(col), (int) (90 * a));
    }

    /** The Special Beam Cannon's purple spiral, wound round the beam and turning. */
    private static void spiral(KiDraw.Ctx c, Vec3 from, Vec3 to, Vec3 unit, double radius, float time, float a) {
        double length = to.distanceTo(from);
        int n = Math.max(8, (int) (length * 6.0D));
        Vec3[] uv = KiDraw.basis(unit);
        Vec3[] pts = new Vec3[n + 1];
        double[] widths = new double[n + 1];
        int[] alphas = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            double d = length * i / n;
            double ang = d * 2.4D - time * 0.7D;
            pts[i] = from.add(unit.scale(d)).add(uv[0].scale(Math.cos(ang) * radius)).add(uv[1].scale(Math.sin(ang) * radius));
            widths[i] = 0.16D;
            alphas[i] = (int) (230 * a);
        }
        KiDraw.ribbon(c, pts, widths, alphas, KiFx.SBC_SPIRAL);
        for (int i = 0; i <= n; i++) {
            widths[i] = 0.5D;
            alphas[i] = (int) (60 * a);
        }
        KiDraw.ribbon(c, pts, widths, alphas, KiFx.SBC_SPIRAL);
    }

    // Charging.

    private static void drawCharge(KiDraw.Ctx c, ClientLevel level, Charge charge, float pt, float time) {
        Entity e = level.getEntity(charge.entity);
        if (e == null || e.isInvisible()) {
            return;
        }
        float t = Math.min(1.0F, (charge.age + pt) / charge.life);
        double r = (0.1D + (charge.size * 0.5D - 0.1D) * ease(t)) * (1.0D + 0.1D * Math.sin(time * 4.3D));
        Vec3 at = anchor(e, charge.at, pt);
        if (charge.at == KiFx.AT_OVERHEAD) {
            at = at.add(0.0D, r, 0.0D);
        }
        int col = charge.colour;
        KiDraw.orb(c, at, r, col, 1.0F);
        // Light drawn in from all round.
        boolean big = charge.at == KiFx.AT_OVERHEAD;
        int streaks = big ? 18 : 9;
        double reach = big ? 14.0D : 2.6D;
        for (int k = 0; k < streaks; k++) {
            double phase = time * (big ? 0.035D : 0.08D) + k / (double) streaks;
            int cycle = (int) Math.floor(phase);
            double p = frac(phase);
            Vec3 dir = randomDir(charge.entity * 31 + k, cycle);
            double d0 = r + (1.0D - p) * reach;
            double d1 = d0 + (big ? 2.5D : 0.5D);
            KiDraw.streak(c, at.add(dir.scale(d0)), at.add(dir.scale(d1)), big ? 0.18D : 0.05D, 0.0D, lighter(col), (int) (230 * p), 0);
        }
        if (charge.at == KiFx.AT_FOREHEAD || charge.size >= 1.0F && !big) {
            // Crackling round it.
            long seed = (long) (time / 2.0F) * 7919L + charge.entity;
            for (int k = 0; k < 2; k++) {
                Vec3 d = randomDir((int) seed + k, 3);
                KiDraw.bolt(c, at, at.add(d.scale(r * 2.6D + 0.3D)), seed + k, 0.25D, 0.04D, 0xE8F4FF, 220);
            }
        }
    }

    // Shots.

    private static void drawShot(KiDraw.Ctx c, Shot shot, float pt, float time) {
        Vec3 at = shot.prev.lerp(shot.pos, pt);
        Vec3 unit = shot.vel.normalize();
        double r = shot.size * 0.5D;
        int col = shot.colour;
        if (shot.style == 1) {
            disc(c, at, unit, r, time, col);
            return;
        }
        double tail = Math.min(shot.travelled + shot.vel.length() * pt, shot.style == KiFx.SHOT_SPIRIT ? r * 3.0D : r * 7.0D);
        if (tail > 0.05D) {
            Vec3 end = at.subtract(unit.scale(tail));
            Vec3[] pts = {at, at.lerp(end, 0.35D), end};
            KiDraw.ribbon(c, pts, new double[]{r * 1.9D, r * 1.3D, 0.0D}, new int[]{150, 80, 0}, col);
            KiDraw.ribbon(c, pts, new double[]{r * 0.9D, r * 0.5D, 0.0D}, new int[]{220, 120, 0}, 0xFFFFFF);
        }
        KiDraw.orb(c, at, r * (1.0D + 0.08D * Math.sin(time * 5.0D)), col, 1.0F);
        if (shot.style == 2) {
            // A blaze's fireball: a flickering skin of flame.
            KiDraw.sphere(c, at, r * 1.25D, 0xFF5010, 70);
        }
    }

    /** Krillin's Destructo Disc: a flat spinning disc of light with a jagged rim. */
    private static void disc(KiDraw.Ctx c, Vec3 at, Vec3 unit, double r, float time, int col) {
        Vec3 flat = new Vec3(unit.x, 0.0D, unit.z);
        Vec3 side = flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-flat.z, 0.0D, flat.x).normalize();
        // Nearly flat, tipped a little to the side.
        Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D).add(side.scale(0.15D)).normalize();
        double spin = time * 1.1D;
        KiDraw.ring(c, at, normal, 0.0D, r * 0.85D, spin, 0.0D, col, 150, 110);
        KiDraw.ring(c, at, normal, r * 0.7D, r * 1.05D, spin, 0.18D, lighter(col), 255, 200);
        KiDraw.ring(c, at, normal, r * 1.0D, r * 1.35D, -spin, 0.25D, col, 90, 0);
        KiDraw.glow(c, at, r * 2.4D, col, 60);
    }

    // Bursts.

    private static void drawBurst(KiDraw.Ctx c, Burst burst, float pt, float time) {
        float t = Math.min(1.0F, (burst.age + pt) / burst.life);
        float fade = (float) Math.pow(1.0F - t, 1.5D);
        int col = burst.colour;
        double size = burst.size;
        Vec3 at = burst.at;
        switch (burst.style) {
            case KiFx.FLASH -> {
                KiDraw.glow(c, at, size * (1.0D + t), 0xFFFFFF, (int) (255 * fade));
                KiDraw.glow(c, at, size * 2.2D, col, (int) (120 * fade));
                KiDraw.sphere(c, at, size * 0.35D * (1.0D + t), 0xFFFFFF, (int) (160 * fade));
            }
            case KiFx.SPARK -> {
                KiDraw.glow(c, at, size * (0.6D + 0.6D * t), lighter(col), (int) (220 * fade));
                KiDraw.star(c, at, size * (0.8D + 1.4D * ease(t)), time * 0.1D + burst.age, 6, 0xFFFFFF, (int) (220 * fade));
            }
            case KiFx.ORB -> {
                float in = Math.min(1.0F, t * 5.0F);
                float out = Math.min(1.0F, (1.0F - t) * 4.0F);
                double r = size * (0.85D + 0.15D * ease(in)) * (1.0D + 0.04D * Math.sin(time * 2.0D));
                KiDraw.orb(c, at, r, col, in * out);
                KiDraw.glow(c, at, r * 4.0D, col, (int) (40 * in * out));
            }
            case KiFx.RING -> {
                double reach = size * ease(t);
                KiDraw.ring(c, at.add(0.0D, 0.1D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), reach * 0.7D, reach, time * 0.1D, 0.1D, lighter(col),
                        0, (int) (200 * fade));
                KiDraw.ring(c, at.add(0.0D, 0.1D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), reach, reach * 1.08D, 0.0D, 0.0D, 0xFFFFFF,
                        (int) (200 * fade), 0);
            }
            default -> {
                // The blast: a flash, a ball of light swelling and fading, a ring going out along the ground.
                float swell = ease(t);
                double r = size * (0.3D + 0.7D * swell);
                if (t < 0.3F) {
                    float f = 1.0F - t / 0.3F;
                    KiDraw.glow(c, at, size * 2.6D, 0xFFFFFF, (int) (255 * f));
                    KiDraw.star(c, at, size * 3.0D, burst.age * 0.3D, 10, lighter(col), (int) (200 * f));
                }
                KiDraw.sphere(c, at, r * 0.6D, 0xFFFFFF, (int) (230 * fade * (1.0F - t)));
                KiDraw.sphere(c, at, r, col, (int) (120 * fade));
                KiDraw.sphere(c, at, r * 1.25D, col, (int) (45 * fade));
                KiDraw.glow(c, at, r * 2.5D, col, (int) (90 * fade));
                double reach = size * (0.4D + 1.8D * swell);
                Vec3 ground = at.add(0.0D, -Math.min(size * 0.6D, 1.5D), 0.0D);
                KiDraw.ring(c, ground, new Vec3(0.0D, 1.0D, 0.0D), reach * 0.82D, reach, 0.0D, 0.0D, lighter(col), 0, (int) (170 * fade));
                KiDraw.ring(c, ground, new Vec3(0.0D, 1.0D, 0.0D), reach, reach * 1.06D, 0.0D, 0.0D, 0xFFFFFF, (int) (170 * fade), 0);
            }
        }
    }

    // Pillars of light.

    private static void drawPillar(KiDraw.Ctx c, ClientLevel level, Pillar pillar, float pt, float time) {
        Entity e = pillar.entity >= 0 ? level.getEntity(pillar.entity) : null;
        Vec3 base = e != null ? e.getPosition(pt) : pillar.at;
        float age = pillar.age + pt;
        float in = Math.min(1.0F, age / 4.0F);
        float out = Math.min(1.0F, (pillar.life - age) / (pillar.life * 0.4F));
        float a = Math.max(0.0F, Math.min(in, out));
        double width = e != null ? Math.max(0.6D, e.getBbWidth()) : 0.8D;
        double r = width * (0.9D + 0.12D * Math.sin(time * 1.9D));
        Vec3 top = base.add(0.0D, pillar.height * ease(in), 0.0D);
        int col = pillar.colour;
        KiDraw.tube(c, base, top, r * 2.3D, r * 3.0D, col, (int) (30 * a), 0);
        KiDraw.tube(c, base, top, r * 1.5D, r * 1.8D, col, (int) (70 * a), (int) (10 * a));
        KiDraw.tube(c, base, top, r, r * 1.1D, col, (int) (120 * a), (int) (40 * a));
        KiDraw.tube(c, base, top, r * 0.45D, r * 0.5D, 0xFFFFFF, (int) (200 * a), (int) (60 * a));
        for (int k = 0; k < 4; k++) {
            double s = frac(time * 0.06D + k / 4.0D);
            Vec3 at = base.add(0.0D, pillar.height * s, 0.0D);
            KiDraw.ring(c, at, new Vec3(0.0D, 1.0D, 0.0D), r * 1.2D, r * 2.1D, 0.0D, 0.0D, lighter(col), (int) (150 * a * (1.0D - s)), 0);
        }
        KiDraw.ring(c, base.add(0.0D, 0.05D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), 0.0D, r * 3.5D, 0.0D, 0.0D, col, (int) (110 * a), 0);
    }

    // Auras.

    private static void drawAuras(KiDraw.Ctx c, Minecraft minecraft, ClientLevel level, float pt, float time) {
        Entity viewer = minecraft.getCameraEntity();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (Entity e : level.entitiesForRendering()) {
            if (e.isInvisible() || e.distanceToSqr(c.camera()) > AURA_RANGE * AURA_RANGE || e == viewer && firstPerson) {
                continue;
            }
            int colour = auraOf(e);
            if (colour != 0) {
                aura(c, e, colour, pt, time, e instanceof DbzFighterEntity f && f.getCharacter() == DbzCharacter.GOHAN_TEEN_SSJ2);
            }
        }
    }

    /** The aura {@code e} should have now, or 0. */
    private static int auraOf(Entity e) {
        long[] sent = AURAS.get(e.getId());
        if (sent != null) {
            return (int) sent[0];
        }
        if (e instanceof DbzFighterEntity fighter) {
            int synced = fighter.getAuraColour();
            if (synced != 0) {
                return synced;
            }
            DbzCharacter who = fighter.getCharacter();
            return who == DbzCharacter.GOKU_SSJ || who == DbzCharacter.GOHAN_TEEN_SSJ2 ? KiFx.GOLD : 0;
        }
        if (e instanceof SuitCompanionEntity companion) {
            return companion.isUltimate() ? KiFx.GOLD : 0;
        }
        return com.pfkfks.flightsuit.suit.SwordArts.GOLDEN.contains(e.getId()) ? KiFx.GOLD : 0;
    }

    /** Tongues of flame licking up round the body, sparks rising (and for a Super Saiyan 2, lightning). */
    private static void aura(KiDraw.Ctx c, Entity e, int col, float pt, float time, boolean lightning) {
        Vec3 base = e.getPosition(pt);
        double h = e.getBbHeight();
        double r = Math.max(0.45D, e.getBbWidth() * 0.8D);
        int seed = e.getId();
        int light = lighter(col);
        for (int layer = 0; layer < 2; layer++) {
            int tongues = layer == 0 ? 12 : 8;
            double rr = layer == 0 ? r : r * 0.6D;
            int tint = layer == 0 ? col : light;
            for (int i = 0; i < tongues; i++) {
                double ang = Math.PI * 2.0D * (i + layer * 0.5D) / tongues + time * 0.04D;
                double flick = 0.5D + 0.5D * Math.sin(time * 0.9D + i * 2.3D + seed);
                double tip = h * (layer == 0 ? 1.08D + 0.32D * flick : 0.9D + 0.2D * flick);
                int n = 5;
                Vec3[] pts = new Vec3[n + 1];
                double[] widths = new double[n + 1];
                int[] alphas = new int[n + 1];
                for (int k = 0; k <= n; k++) {
                    double s = k / (double) n;
                    double out = rr * (0.65D + 0.45D * Math.sin(Math.PI * Math.min(1.0D, s * 1.1D))) * (k == n ? 0.45D : 1.0D);
                    double sway = Math.sin(time * 0.7D + i + k * 0.8D) * 0.06D * k;
                    pts[k] = base.add(Math.cos(ang) * out + Math.cos(ang + 1.6D) * sway, tip * s, Math.sin(ang) * out + Math.sin(ang + 1.6D) * sway);
                    widths[k] = rr * 0.95D * Math.pow(1.0D - s, 0.7D);
                    alphas[k] = (int) ((layer == 0 ? 105 : 80) * Math.sin(Math.PI * Math.min(1.0D, 0.15D + s * 0.95D)));
                }
                KiDraw.ribbon(c, pts, widths, alphas, tint);
            }
        }
        KiDraw.glow(c, base.add(0.0D, h * 0.5D, 0.0D), h * 0.85D, col, 28);
        for (int k = 0; k < 7; k++) {
            double phase = time * 0.05D + k / 7.0D;
            double p = frac(phase);
            int cycle = (int) Math.floor(phase);
            double ang = hash(seed + k, cycle) * Math.PI * 2.0D;
            Vec3 at = base.add(Math.cos(ang) * r * 1.05D, h * (0.1D + 1.25D * p), Math.sin(ang) * r * 1.05D);
            KiDraw.streak(c, at, at.add(0.0D, 0.35D, 0.0D), 0.06D, 0.0D, light, (int) (230 * (1.0D - p)), 0);
        }
        if (lightning && (int) (time / 3.0F) % 3 == 0) {
            long s = (long) (time / 3.0F) * 31L + seed;
            Vec3 a = base.add((hash((int) s, 1) - 0.5D) * r * 1.6D, h * hash((int) s, 2), (hash((int) s, 3) - 0.5D) * r * 1.6D);
            Vec3 b = base.add((hash((int) s, 4) - 0.5D) * r * 1.6D, h * hash((int) s, 5), (hash((int) s, 6) - 0.5D) * r * 1.6D);
            KiDraw.bolt(c, a, b, s, 0.35D, 0.04D, 0xD8F0FF, 255);
        }
    }

    // ---------------------------------------------------------------- the Solar Flare's white-out

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || flareUntil <= 0L) {
            return;
        }
        float left = flareUntil - (minecraft.level.getGameTime() + event.getPartialTick());
        if (left <= 0.0F) {
            flareUntil = 0L;
            return;
        }
        float a = Mth.clamp(left / 30.0F, 0.0F, 1.0F);
        int alpha = (int) (255 * a * a);
        event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), alpha << 24 | 0xFFFFFF);
    }
}
