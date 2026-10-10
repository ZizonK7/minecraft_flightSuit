package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Energy shots that fly (M17), simulated here as moving points drawn with particles rather than spawned as
 * entities (like the hero's sword beams): Trunks' Burning Attack, Krillin's Destructo Disc, the phantom's stolen
 * fireballs, and the Dragon Ball fighters' ki blasts. Shared by suit wearers, companions and fighters alike;
 * whoever fires one says who it may hit. Nothing breaks blocks.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class KiShots {
    public enum Style {
        /** Trunks' Burning Attack: a big yellow ball that bursts on the first thing it meets. */
        BURNING,
        /** Destructo Disc: a spinning yellow disc that cuts through everything in its way, stopped only by blocks. */
        DISC,
        /** A blaze's fireball: sets what it hits alight. */
        FIRE,
        /** A plain ki blast: a small burst on what it hits. */
        KI,
        /** A purple ki blast (Frieza's side). */
        KI_DARK
    }

    private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.25F), 2.0F);
    private static final DustParticleOptions YELLOW_CORE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.8F), 1.2F);
    private static final DustParticleOptions KI_BLUE = new DustParticleOptions(new Vector3f(0.6F, 0.85F, 1.0F), 1.3F);
    private static final DustParticleOptions KI_PURPLE = new DustParticleOptions(new Vector3f(0.75F, 0.35F, 1.0F), 1.3F);

    private static final class Shot {
        final ServerLevel level;
        final LivingEntity owner;
        final Style style;
        final Vec3 dir;
        final double speed;
        final double range;
        final float damage;
        final double radius;
        final Predicate<LivingEntity> hits;
        final Set<LivingEntity> cut = new HashSet<>();
        Vec3 pos;
        double travelled;
        int age;

        Shot(ServerLevel level, LivingEntity owner, Style style, Vec3 pos, Vec3 dir, double speed, double range, float damage,
             double radius, Predicate<LivingEntity> hits) {
            this.level = level;
            this.owner = owner;
            this.style = style;
            this.pos = pos;
            this.dir = dir.normalize();
            this.speed = speed;
            this.range = range;
            this.damage = damage;
            this.radius = radius;
            this.hits = hits;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();

    private KiShots() {
    }

    /**
     * Fires a shot from {@code from} along {@code dir}.
     * @param radius the burst's reach (BURNING), or how close it must pass to cut (DISC); unused otherwise
     */
    public static void fire(ServerLevel level, LivingEntity owner, Style style, Vec3 from, Vec3 dir, double speed, double range,
                            float damage, double radius, Predicate<LivingEntity> hits) {
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        SHOTS.add(new Shot(level, owner, style, from, dir, speed, range, damage, radius, hits));
        switch (style) {
            case BURNING -> level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
            case DISC -> level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0F, 1.6F);
            case FIRE -> level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
            default -> level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8F, 1.6F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHOTS.isEmpty()) {
            return;
        }
        Iterator<Shot> it = SHOTS.iterator();
        while (it.hasNext()) {
            Shot shot = it.next();
            if (!shot.owner.isAlive() && shot.style != Style.DISC || shot.owner.level() != shot.level || step(shot)) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SHOTS.clear();
    }

    /** One tick of flight; true when the shot is spent. */
    private static boolean step(Shot shot) {
        ServerLevel level = shot.level;
        shot.age++;
        Vec3 from = shot.pos;
        Vec3 to = from.add(shot.dir.scale(shot.speed));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot.owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        draw(shot, from, end);
        // What it meets on the way.
        double reach = shot.style == Style.DISC ? shot.radius : 0.6D;
        LivingEntity first = null;
        double firstAlong = Double.MAX_VALUE;
        Vec3 seg = end.subtract(from);
        double len = seg.length();
        Vec3 unit = len < 1.0E-6D ? shot.dir : seg.scale(1.0D / len);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(reach + 1.0D),
                entity -> entity != shot.owner && entity.isAlive() && !entity.isSpectator() && shot.hits.test(entity))) {
            AABB box = victim.getBoundingBox().inflate(reach * 0.5D);
            Vec3 centre = box.getCenter();
            double along = Math.max(0.0D, Math.min(len, centre.subtract(from).dot(unit)));
            Vec3 closest = from.add(unit.scale(along));
            double grow = reach * 0.5D + 0.3D;
            if (!box.inflate(grow).contains(closest)) {
                continue;
            }
            if (shot.style == Style.DISC) {
                if (shot.cut.add(victim)) {
                    hurt(shot, victim, shot.damage);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, closest.x, closest.y, closest.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.4F);
                }
            } else if (along < firstAlong) {
                first = victim;
                firstAlong = along;
            }
        }
        if (first != null) {
            Vec3 at = from.add(unit.scale(firstAlong));
            impact(shot, at, first);
            return true;
        }
        shot.travelled += len;
        shot.pos = end;
        if (block.getType() != HitResult.Type.MISS) {
            impact(shot, end, null);
            return true;
        }
        if (shot.travelled >= shot.range) {
            if (shot.style == Style.BURNING) {
                impact(shot, end, null);
            }
            return true;
        }
        return false;
    }

    private static void hurt(Shot shot, LivingEntity victim, float damage) {
        victim.invulnerableTime = 0;
        DamageSource source = shot.owner instanceof ServerPlayer player ? shot.owner.damageSources().playerAttack(player)
                : shot.owner.damageSources().mobAttack(shot.owner);
        victim.hurt(source, damage);
    }

    private static void impact(Shot shot, Vec3 at, LivingEntity direct) {
        ServerLevel level = shot.level;
        switch (shot.style) {
            case BURNING -> {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(shot.radius),
                        entity -> entity != shot.owner && entity.isAlive() && shot.hits.test(entity)
                                && entity.getBoundingBox().getCenter().distanceTo(at) <= shot.radius + entity.getBbWidth() / 2.0D)) {
                    hurt(shot, victim, shot.damage);
                    Vec3 push = victim.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
                    if (push.lengthSqr() > 1.0E-4D) {
                        victim.knockback(1.2D, -push.x, -push.z);
                    }
                }
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(YELLOW, at.x, at.y, at.z, 40, shot.radius / 2.0D, shot.radius / 2.0D, shot.radius / 2.0D, 0.0D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.9F);
            }
            case DISC -> {
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3D, 0.3D, 0.3D, 0.3D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
            }
            case FIRE -> {
                if (direct != null) {
                    hurt(shot, direct, shot.damage);
                    direct.setSecondsOnFire(3);
                }
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 12, 0.2D, 0.2D, 0.2D, 0.05D);
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            default -> {
                // A ki blast: Trunks may cut it, a boss may dodge it (KiGuard).
                if (direct != null && !KiGuard.blocks(direct, shot.owner, at.subtract(shot.dir.scale(2.0D)))) {
                    hurt(shot, direct, shot.damage);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(shot.style == Style.KI_DARK ? KI_PURPLE : KI_BLUE, at.x, at.y, at.z, 10, 0.3D, 0.3D, 0.3D, 0.0D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5F, 1.6F);
            }
        }
    }

    /** The shot along this tick's stretch, with its tail. */
    private static void draw(Shot shot, Vec3 from, Vec3 to) {
        ServerLevel level = shot.level;
        int points = Math.max(2, (int) (to.distanceTo(from) * 3.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.lerp(to, i / (double) points);
            switch (shot.style) {
                case BURNING -> {
                    level.sendParticles(YELLOW, at.x, at.y, at.z, 3, 0.25D, 0.25D, 0.25D, 0.0D);
                    level.sendParticles(YELLOW_CORE, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
                }
                case DISC -> {
                    if (i == points) {
                        // A flat ring, turning.
                        Vec3 side = shot.dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
                        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
                        Vec3 fwd = shot.dir.multiply(1.0D, 0.0D, 1.0D);
                        fwd = fwd.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : fwd.normalize();
                        for (int k = 0; k < 12; k++) {
                            double angle = k * Math.PI / 6.0D + shot.age * 0.8D;
                            Vec3 rim = at.add(side.scale(Math.cos(angle) * shot.radius * 0.6D)).add(fwd.scale(Math.sin(angle) * shot.radius * 0.6D));
                            level.sendParticles(YELLOW, rim.x, rim.y, rim.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
                case FIRE -> level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
                case KI_DARK -> level.sendParticles(KI_PURPLE, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
                default -> level.sendParticles(KI_BLUE, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
        if (shot.style == Style.BURNING || shot.style == Style.KI || shot.style == Style.KI_DARK) {
            level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }
}
