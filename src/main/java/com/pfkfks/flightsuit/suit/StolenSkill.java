package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The skills the phantom can steal (M17, Z held) and use (B) - and the very same moves when their owners use them:
 * the Dragon Ball fighters fire their beams through {@link #cast} too, so a stolen Kamehameha is Goku's Kamehameha.
 * Saved by name (not ordinal). Vanilla monsters don't record what they do; each kind has one fixed skill
 * ({@link #ofMob}).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public enum StolenSkill {
    /** Goku, Cell: a second's charge, then a thick blue beam straight through. */
    KAMEHAMEHA(2_500, 160, 20, 20.0F),
    /** Vegeta: a purple beam that throws hard. */
    GALICK_GUN(2_500, 160, 20, 22.0F),
    /** Frieza: a thin beam at once, quick to come again. */
    DEATH_BEAM(800, 40, 0, 12.0F),
    /** Piccolo: two seconds' charge, a beam with a spiral round it, deeper into bosses. */
    SPECIAL_BEAM_CANNON(3_000, 240, 40, 30.0F),
    /** Tien, Cell: blinds everything round and makes it lose its target. */
    SOLAR_FLARE(1_000, 300, 0, 0.0F),
    /** Krillin: a spinning disc that cuts through every foe in its way. */
    DESTRUCTO_DISC(1_500, 120, 0, 18.0F),
    /** Goku: eight seconds of half again the damage and speed, and four health when it ends. */
    KAIOKEN(1_200, 600, 0, 0.0F),
    /** Goku: straight to the side of the aimed foe, however far, if it's in sight. */
    INSTANT_TRANSMISSION(1_000, 200, 0, 0.0F),
    /** Cell Jr., Saibamen: a blast round you - and it costs you six health. */
    SELF_DESTRUCT(1_500, 400, 0, 25.0F),
    /** Majin Buu: the foe it hits (not a boss) turns candy-small for a while (CandyShrink). */
    CANDY_BEAM(1_200, 300, 0, 4.0F),
    /** Creeper: a blast where you aim (no blocks broken). */
    CREEPER_BLAST(800, 160, 0, 15.0F),
    /** Blaze, ghast: three small fireballs. */
    FIREBALL(600, 80, 0, 5.0F),
    /** Enderman: somewhere near where you look, up to 32 blocks off. */
    ENDER_BLINK(500, 60, 0, 0.0F),
    /** Evoker: a row of fangs along the ground. */
    EVOKER_FANGS(700, 120, 0, 6.0F),
    /** Wither skeleton: a cut that withers. */
    WITHER_SLASH(500, 80, 0, 8.0F);

    private static final DustParticleOptions KAME_GLOW = new DustParticleOptions(new Vector3f(0.35F, 0.7F, 1.0F), 2.8F);
    private static final DustParticleOptions GALICK_GLOW = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 2.8F);
    private static final DustParticleOptions DEATH_GLOW = new DustParticleOptions(new Vector3f(0.9F, 0.4F, 0.95F), 0.7F);
    private static final DustParticleOptions SBC_GLOW = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.55F), 1.3F);
    private static final DustParticleOptions SBC_SPIRAL = new DustParticleOptions(new Vector3f(0.55F, 0.25F, 0.85F), 1.0F);
    private static final DustParticleOptions CANDY_GLOW = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.8F), 1.0F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.3F);
    private static final DustParticleOptions RED_AURA = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.15F), 1.4F);
    private static final String KAIOKEN_UNTIL = "flightsuit_kaioken_until";
    private static final UUID KAIOKEN_SPEED_ID = UUID.fromString("9b1e0f6a-3c55-4d1e-8a7b-2e6f40c1d9a3");

    private final int cost;
    private final int cooldownTicks;
    private final int chargeTicks;
    private final float damage;

    StolenSkill(int cost, int cooldownTicks, int chargeTicks, float damage) {
        this.cost = cost;
        this.cooldownTicks = cooldownTicks;
        this.chargeTicks = chargeTicks;
        this.damage = damage;
    }

    public int cost() {
        return cost;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Ticks of charging before it goes off (0 = at once). */
    public int chargeTicks() {
        return chargeTicks;
    }

    /** The damage when the phantom uses it (fighters pass their own). */
    public float damage() {
        return damage;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("skill.flightsuit.stolen." + id()).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public static @Nullable StolenSkill byName(String name) {
        for (StolenSkill skill : values()) {
            if (skill.name().equalsIgnoreCase(name)) {
                return skill;
            }
        }
        return null;
    }

    /** A vanilla monster's one skill, by kind (they don't record when they use it). */
    public static @Nullable StolenSkill ofMob(LivingEntity mob) {
        if (mob instanceof Creeper) {
            return CREEPER_BLAST;
        }
        if (mob instanceof Blaze || mob instanceof Ghast) {
            return FIREBALL;
        }
        if (mob instanceof EnderMan) {
            return ENDER_BLINK;
        }
        if (mob instanceof Evoker) {
            return EVOKER_FANGS;
        }
        if (mob instanceof WitherSkeleton) {
            return WITHER_SLASH;
        }
        return null;
    }

    // ---------------------------------------------------------------- charging

    /** While it charges: light gathering at the hands (a sound at the start). */
    public void chargeEffect(ServerLevel level, LivingEntity caster, int charged) {
        Vec3 hands = SwordArts.hands(caster);
        DustParticleOptions glow = this == GALICK_GUN ? GALICK_GLOW : this == SPECIAL_BEAM_CANNON ? SBC_GLOW : KAME_GLOW;
        if (charged == 0) {
            level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F,
                    this == SPECIAL_BEAM_CANNON ? 0.6F : 1.0F);
        }
        double size = 0.1D + 0.4D * charged / Math.max(1, chargeTicks);
        if (this == SPECIAL_BEAM_CANNON) {
            // Two fingers to the forehead: the light gathers there.
            hands = caster.getEyePosition().add(caster.getLookAngle().scale(0.4D));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hands.x, hands.y, hands.z, 2, 0.2D, 0.2D, 0.2D, 0.05D);
        }
        level.sendParticles(glow, hands.x, hands.y, hands.z, 4, size, size, size, 0.0D);
        level.sendParticles(WHITE, hands.x, hands.y, hands.z, 1, size * 0.3D, size * 0.3D, size * 0.3D, 0.0D);
    }

    // ---------------------------------------------------------------- casting

    /**
     * Uses the skill now (after any charge). {@code from}/{@code dir}: where it leaves from and which way;
     * {@code aimed}: the foe under the crosshair, or null; {@code damage}: what it does (the phantom passes
     * {@link #damage()}, fighters their own); {@code hits}: who it may hurt.
     * @return false if it couldn't go off at all (no foe for Instant Transmission, nowhere to land) - nothing spent then
     */
    public boolean cast(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, @Nullable LivingEntity aimed, float damage,
                        Predicate<LivingEntity> hits) {
        Vec3 look = dir.normalize();
        switch (this) {
            case KAMEHAMEHA -> {
                SuitSkills.beam(level, caster, from, look, 24.0D, 1.8D, damage, 0.9D, KAME_GLOW, WHITE, hits);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.3F);
            }
            case GALICK_GUN -> {
                SuitSkills.beam(level, caster, from, look, 24.0D, 1.8D, damage, 1.8D, GALICK_GLOW, WHITE, hits);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 0.9F);
            }
            case DEATH_BEAM -> {
                SuitSkills.beam(level, caster, from, look, 32.0D, 0.6D, damage, 0.2D, DEATH_GLOW, DEATH_GLOW, hits);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 1.8F);
            }
            case SPECIAL_BEAM_CANNON -> {
                Vec3 end = SuitSkills.beam(level, caster, from, look, 32.0D, 1.0D,
                        victim -> Stasis.isBoss(victim) ? damage * 1.5F : damage, 1.0D, SBC_GLOW, WHITE, hits);
                spiral(level, from, end);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.8F);
            }
            case SOLAR_FLARE -> solarFlare(level, caster, hits);
            case DESTRUCTO_DISC -> KiShots.fire(level, caster, KiShots.Style.DISC, from, look, 1.2D, 32.0D, damage, 1.3D, hits);
            case KAIOKEN -> kaioken(level, caster);
            case INSTANT_TRANSMISSION -> {
                return instantTransmission(level, caster, aimed);
            }
            case SELF_DESTRUCT -> selfDestruct(level, caster, damage, hits);
            case CANDY_BEAM -> candyBeam(level, caster, from, look, damage, hits);
            case CREEPER_BLAST -> creeperBlast(level, caster, from, look, damage, hits);
            case FIREBALL -> {
                for (int i = -1; i <= 1; i++) {
                    Vec3 spread = look.yRot((float) Math.toRadians(i * 7.0D));
                    KiShots.fire(level, caster, KiShots.Style.FIRE, from, spread, 1.1D, 32.0D, damage, 0.0D, hits);
                }
            }
            case ENDER_BLINK -> {
                return enderBlink(level, caster, from, look);
            }
            case EVOKER_FANGS -> fangs(level, caster, look);
            case WITHER_SLASH -> {
                return witherSlash(level, caster, aimed, damage, hits);
            }
        }
        return true;
    }

    /** The Special Beam Cannon's spiral, wound round the beam. */
    private static void spiral(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.1D) {
            return;
        }
        dir = dir.scale(1.0D / length);
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        for (double d = 0.0D; d < length; d += 0.25D) {
            double angle = d * 2.2D;
            Vec3 at = from.add(dir.scale(d)).add(side.scale(Math.cos(angle) * 0.7D)).add(up.scale(Math.sin(angle) * 0.7D));
            level.sendParticles(SBC_SPIRAL, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void solarFlare(ServerLevel level, LivingEntity caster, Predicate<LivingEntity> hits) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(10.0D),
                entity -> entity != caster && entity.isAlive() && entity.distanceTo(caster) <= 10.0D && hits.test(entity))) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            if (victim instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
            }
        }
        Vec3 head = caster.getEyePosition();
        level.sendParticles(ParticleTypes.FLASH, head.x, head.y + 0.3D, head.z, 3, 0.2D, 0.2D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 40, 0.3D, 0.3D, 0.3D, 0.5D);
        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 1.8F);
    }

    private static void kaioken(ServerLevel level, LivingEntity caster) {
        caster.getPersistentData().putLong(KAIOKEN_UNTIL, level.getGameTime() + 160);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(KAIOKEN_SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(KAIOKEN_SPEED_ID, "Kaioken", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        level.sendParticles(RED_AURA, caster.getX(), caster.getY() + 1.0D, caster.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.0D);
        level.playSound(null, caster.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** Kaioken under way: half again the damage. */
    public static boolean inKaioken(LivingEntity entity) {
        return entity.getPersistentData().contains(KAIOKEN_UNTIL);
    }

    private static boolean instantTransmission(ServerLevel level, LivingEntity caster, @Nullable LivingEntity target) {
        if (target == null) {
            return false;
        }
        Vec3 back = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        back = back.lengthSqr() < 1.0E-4D ? caster.getLookAngle().multiply(1.0D, 0.0D, 1.0D) : back;
        Vec3 near = target.position().subtract(back.normalize().scale(target.getBbWidth() / 2.0D + 1.2D));
        Vec3 spot = SuitSkills.standable(level, caster, near);
        if (spot == null) {
            spot = SuitSkills.standable(level, caster, target.position().add(1.5D, 0.0D, 0.0D));
        }
        if (spot == null) {
            return false;
        }
        Vec3 from = caster.position();
        Vec3 face = target.position().subtract(spot);
        float yaw = (float) (Mth.atan2(face.z, face.x) * (180.0D / Math.PI)) - 90.0F;
        teleport(level, caster, spot, yaw);
        level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1.0D, from.z, 12, 0.3D, 0.6D, 0.3D, 0.05D);
        level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 1.0D, spot.z, 12, 0.3D, 0.6D, 0.3D, 0.05D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    private static void teleport(ServerLevel level, LivingEntity who, Vec3 to, float yaw) {
        if (who instanceof ServerPlayer player) {
            player.teleportTo(level, to.x, to.y, to.z, yaw, player.getXRot());
        } else {
            who.teleportTo(to.x, to.y, to.z);
            who.setYRot(yaw);
        }
        who.fallDistance = 0.0F;
    }

    private static void selfDestruct(ServerLevel level, LivingEntity caster, float damage, Predicate<LivingEntity> hits) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(5.0D),
                entity -> entity != caster && entity.isAlive() && entity.distanceTo(caster) <= 5.0D && hits.test(entity))) {
            victim.invulnerableTime = 0;
            victim.hurt(caster instanceof ServerPlayer player ? caster.damageSources().playerAttack(player)
                    : caster.damageSources().mobAttack(caster), damage);
            Vec3 push = victim.position().subtract(caster.position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                victim.knockback(1.4D, -push.x, -push.z);
            }
        }
        caster.setHealth(Math.max(1.0F, caster.getHealth() - 6.0F));
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, caster.getX(), caster.getY() + 0.5D, caster.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, caster.getX(), caster.getY() + 1.0D, caster.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
    }

    private static void candyBeam(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 look, float damage, Predicate<LivingEntity> hits) {
        SuitSkills.beam(level, caster, from, look, 20.0D, 0.6D, damage, 0.0D, CANDY_GLOW, WHITE, entity -> {
            if (!hits.test(entity)) {
                return false;
            }
            if (!Stasis.isBoss(entity) && !(entity instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter
                    && fighter.getCharacter().role() == com.pfkfks.flightsuit.planet.dbz.DbzCharacter.Role.BOSS)) {
                CandyShrink.shrink(level, entity, 60);
            }
            return true;
        });
        level.playSound(null, from.x, from.y, from.z, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    private static void creeperBlast(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 look, float damage, Predicate<LivingEntity> hits) {
        Vec3 far = from.add(look.scale(24.0D));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(from, far,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        Vec3 at = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : block.getLocation();
        if (caster instanceof ServerPlayer player) {
            SuitWeapons.Aim aim = SuitWeapons.aim(player, 24.0D);
            at = aim.end();
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5D),
                entity -> entity != caster && entity.isAlive() && hits.test(entity))) {
            victim.invulnerableTime = 0;
            victim.hurt(caster instanceof ServerPlayer player ? caster.damageSources().playerAttack(player)
                    : caster.damageSources().mobAttack(caster), damage);
            Vec3 push = victim.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                victim.knockback(1.0D, -push.x, -push.z);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 1.0F);
    }

    private static boolean enderBlink(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 look) {
        Vec3 far = from.add(look.scale(32.0D));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(from, far,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        Vec3 end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : block.getLocation().subtract(look.scale(0.6D));
        for (int i = 0; i < 16; i++) {
            Vec3 near = end.add((caster.getRandom().nextDouble() - 0.5D) * 6.0D, (caster.getRandom().nextDouble() - 0.5D) * 4.0D,
                    (caster.getRandom().nextDouble() - 0.5D) * 6.0D);
            Vec3 spot = SuitSkills.standable(level, caster, near);
            if (spot != null) {
                Vec3 was = caster.position();
                teleport(level, caster, spot, caster.getYRot());
                level.sendParticles(ParticleTypes.PORTAL, was.x, was.y + 1.0D, was.z, 30, 0.3D, 0.8D, 0.3D, 0.3D);
                level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1.0D, spot.z, 30, 0.3D, 0.8D, 0.3D, 0.3D);
                level.playSound(null, was.x, was.y, was.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /** The evoker's row of fangs, along the ground the way you look. */
    private static void fangs(ServerLevel level, LivingEntity caster, Vec3 look) {
        Vec3 flat = look.multiply(1.0D, 0.0D, 1.0D);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, caster.getYRot()) : flat.normalize();
        float yaw = (float) Mth.atan2(flat.z, flat.x);
        for (int i = 0; i < 14; i++) {
            double d = 1.5D + i * 1.25D;
            double x = caster.getX() + flat.x * d;
            double z = caster.getZ() + flat.z * d;
            Double y = floor(level, x, caster.getY(), z);
            if (y != null) {
                level.addFreshEntity(new EvokerFangs(level, x, y, z, yaw, i, caster));
            }
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The top of the ground near {@code y} at x/z (a few blocks up or down), or null. */
    private static @Nullable Double floor(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y + 2.0D, z);
        for (int i = 0; i < 7; i++, pos = pos.below()) {
            BlockPos below = pos.below();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                return pos.getY() + (shape.isEmpty() ? 0.0D : shape.max(net.minecraft.core.Direction.Axis.Y));
            }
        }
        return null;
    }

    private static boolean witherSlash(ServerLevel level, LivingEntity caster, @Nullable LivingEntity aimed, float damage,
                                       Predicate<LivingEntity> hits) {
        LivingEntity target = aimed != null && aimed.distanceTo(caster) <= 4.5D ? aimed : null;
        if (target == null) {
            Vec3 look = caster.getLookAngle();
            double best = 4.5D;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(4.5D),
                    entity -> entity != caster && entity.isAlive() && hits.test(entity))) {
                Vec3 to = near.getBoundingBox().getCenter().subtract(caster.getEyePosition());
                if (to.length() < best && to.normalize().dot(look) > 0.5D) {
                    best = to.length();
                    target = near;
                }
            }
        }
        if (target == null) {
            return false;
        }
        target.invulnerableTime = 0;
        target.hurt(caster instanceof ServerPlayer player ? caster.damageSources().playerAttack(player)
                : caster.damageSources().mobAttack(caster), damage);
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, 0.3D, 0.4D, 0.3D, 0.02D);
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    // ---------------------------------------------------------------- Kaioken's effects

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(KAIOKEN_UNTIL)) {
            return;
        }
        if (level.getGameTime() >= data.getLong(KAIOKEN_UNTIL) || !entity.isAlive()) {
            data.remove(KAIOKEN_UNTIL);
            AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(KAIOKEN_SPEED_ID);
            }
            if (entity.isAlive()) {
                // The body pays for it.
                entity.setHealth(Math.max(1.0F, entity.getHealth() - 4.0F));
                level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            return;
        }
        if (entity.tickCount % 2 == 0) {
            level.sendParticles(RED_AURA, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0D, entity.getZ(), 3,
                    entity.getBbWidth() * 0.7D, entity.getBbHeight() * 0.45D, entity.getBbWidth() * 0.7D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && !attacker.level().isClientSide && inKaioken(attacker)) {
            event.setAmount(event.getAmount() * 1.5F);
        }
    }
}
