package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The phantom's Tempest (M17 ultimate), for the wearer and the companion alike: after a moment's wind-up the sky
 * over {@code at} turns purple and for TEMPEST_TICKS cards rain down over TEMPEST_RADIUS - every TEMPEST_HIT_INTERVAL
 * ticks everything {@code hits} allows under it takes TEMPEST_DAMAGE, is slowed, and is pressed down out of the air.
 * At the end the cards gather into one and fly back to the caster's hand.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class TempestRain {
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.8F), 2.5F);
    private static final DustParticleOptions PURPLE_SMALL = new DustParticleOptions(new Vector3f(0.75F, 0.45F, 1.0F), 1.0F);
    private static final double SKY = 12.0D;

    private static final class Rain {
        final ServerLevel level;
        final LivingEntity caster;
        final Vec3 at;
        final Predicate<LivingEntity> hits;
        int age;

        Rain(ServerLevel level, LivingEntity caster, Vec3 at, Predicate<LivingEntity> hits) {
            this.level = level;
            this.caster = caster;
            this.at = at;
            this.hits = hits;
        }
    }

    private static final List<Rain> RAINS = new ArrayList<>();

    private TempestRain() {
    }

    public static void start(ServerLevel level, LivingEntity caster, Vec3 at, Predicate<LivingEntity> hits) {
        RAINS.add(new Rain(level, caster, at, hits));
    }

    /** True while one of {@code caster}'s rains is falling. */
    public static boolean isRaining(LivingEntity caster) {
        for (Rain rain : RAINS) {
            if (rain.caster == caster) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RAINS.isEmpty()) {
            return;
        }
        Iterator<Rain> it = RAINS.iterator();
        while (it.hasNext()) {
            Rain rain = it.next();
            if (rain.caster.level() != rain.level || rain.caster.isRemoved() || step(rain)) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        RAINS.clear();
    }

    private static boolean step(Rain rain) {
        ServerLevel level = rain.level;
        Vec3 at = rain.at;
        int age = rain.age++;
        double radius = SuitTuning.TEMPEST_RADIUS;
        if (age < SuitTuning.TEMPEST_WINDUP_TICKS) {
            // The sky over it turning purple.
            for (int i = 0; i < 2 + age; i++) {
                Vec3 p = spot(level, at, radius);
                level.sendParticles(PURPLE, p.x, at.y + SKY + level.random.nextDouble(), p.z, 1, 0.4D, 0.1D, 0.4D, 0.0D);
            }
            return false;
        }
        int raining = age - SuitTuning.TEMPEST_WINDUP_TICKS;
        if (raining >= SuitTuning.TEMPEST_TICKS) {
            gatherHome(rain);
            return true;
        }
        for (int i = 0; i < 5; i++) {
            Vec3 p = spot(level, at, radius);
            level.sendParticles(PURPLE, p.x, at.y + SKY, p.z, 1, 0.5D, 0.1D, 0.5D, 0.0D);
        }
        double fall = -SKY / ModParticles.CARD_FLIGHT_LIFE;
        for (int i = 0; i < 7; i++) {
            Vec3 p = spot(level, at, radius);
            level.sendParticles(ModParticles.CARD_FLIGHT.get(), p.x, at.y + SKY, p.z, 0, 0.0D, fall, 0.0D, 1.0D);
            level.sendParticles(PURPLE_SMALL, p.x, at.y + 0.1D, p.z, 1, 0.1D, 0.0D, 0.1D, 0.0D);
            if (i < 2) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, at.y + 0.2D, p.z, 2, 0.2D, 0.05D, 0.2D, 0.1D);
            }
        }
        if (raining % 3 == 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.3F + level.random.nextFloat() * 0.5F);
        }
        if (raining % SuitTuning.TEMPEST_HIT_INTERVAL == 0) {
            AABB area = new AABB(at.x - radius, at.y - 3.0D, at.z - radius, at.x + radius, at.y + SKY + 2.0D, at.z + radius);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != rain.caster && entity.isAlive()
                    && rain.hits.test(entity) && Math.hypot(entity.getX() - at.x, entity.getZ() - at.z) <= radius)) {
                victim.invulnerableTime = 0;
                victim.hurt(rain.caster instanceof ServerPlayer player ? player.damageSources().playerAttack(player)
                        : rain.caster.damageSources().mobAttack(rain.caster), SuitTuning.TEMPEST_DAMAGE);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SuitTuning.TEMPEST_HIT_INTERVAL + 10, 2, false, true));
                if (!victim.onGround()) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, -0.5D, 0.0D));
                    victim.hurtMarked = true;
                }
                Vec3 c = victim.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 3, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
        return false;
    }

    private static Vec3 spot(ServerLevel level, Vec3 at, double radius) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double r = Math.sqrt(level.random.nextDouble()) * radius;
        return new Vec3(at.x + Math.cos(angle) * r, at.y, at.z + Math.sin(angle) * r);
    }

    /** Every card gathers back into one and flies home. */
    private static void gatherHome(Rain rain) {
        ServerLevel level = rain.level;
        Vec3 centre = rain.at.add(0.0D, 1.0D, 0.0D);
        SuitSkills.cardSwirl(level, rain.at, 2.5F, 14);
        Vec3 hand = SwordArts.hands(rain.caster);
        Vec3 velocity = hand.subtract(centre).scale(1.0D / ModParticles.CARD_FLIGHT_LIFE);
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ModParticles.CARD_FLIGHT.get(), centre.x, centre.y + i * 0.1D, centre.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        level.playSound(null, rain.caster.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
