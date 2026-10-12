package com.pfkfks.flightsuit.doom;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.planet.dbz.DbzAction;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import com.pfkfks.flightsuit.suit.KiShots;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * How Battleworld's people fight (DESIGN.md 4-17), on the Dragon Ball fighters' body (DbzFighterEntity runs the melee
 * combo, charged beams, ki blasts and dashes; DbzMoves hands every Doom-saga character to {@link #use}). The moves
 * come from what fans know Doom by - Marvel vs. Capcom's Plasma Beam, Photon Shot, Molecular Shield, Hidden Missiles,
 * Foot Dive, Photon Array, Sphere Flame and Doom's Time; the comics' electrified armour and endless Doombots; the
 * Doomsday trailer's "Hell answers to me" - and the others' signatures: Magneto pulling a suited player in, Namor's
 * "Imperius Rex!", the Fantastic Four's stretch, force field, flame and clobbering.
 * Every telegraphed move (a ring on the ground, a ball gathering) gives a moment to get out of the way.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DoomMoves {
    /** Doom's green (the magic and the plasma alike). */
    public static final int GREEN = 0x50FF70;
    private static final String MOVE = "flightsuit_doom_move";
    private static final String TALKED = "flightsuit_doom_talked";

    /** Something to happen a few ticks from now (telegraphed moves), dropped if its fighter is gone. */
    private record Later(LivingEntity who, int[] ticks, Runnable action) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    private DoomMoves() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LATER.isEmpty()) {
            return;
        }
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
            Later later = it.next();
            if (!later.who().isAlive()) {
                it.remove();
            } else if (--later.ticks()[0] <= 0) {
                due.add(later.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LATER.clear();
    }

    private static void later(LivingEntity who, int ticks, Runnable action) {
        LATER.add(new Later(who, new int[]{ticks}, action));
    }

    public static boolean isCast(DbzCharacter who) {
        return switch (who) {
            case DOCTOR_DOOM, DOOM_GOD_EMPEROR, DOOMBOT, LATVERIAN_WITCH, SENTINEL, MAGNETO, NAMOR, TALOKAN_WARRIOR, MARVEL_ZOMBIE,
                 ZOMBIE_CAPTAIN, MISTER_FANTASTIC, INVISIBLE_WOMAN, HUMAN_TORCH, THE_THING -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- beams and shots (DbzFighterEntity asks)

    /** The colour a charged beam gathers and fires in. */
    public static int beamColour(DbzCharacter who) {
        return switch (who) {
            case SENTINEL -> 0xFF5030;
            case HUMAN_TORCH -> KiFx.FIRE;
            case DOOM_GOD_EMPEROR -> 0xB8FFC8;
            default -> GREEN;
        };
    }

    /** The colour of a blast wave (DbzFighterEntity.blastWave): Doom's green, Sue's force field, the Thing's dust... */
    public static int waveColour(DbzCharacter who) {
        return switch (who) {
            case INVISIBLE_WOMAN -> 0x9AE6FF;
            case THE_THING -> 0xFFB060;
            case SENTINEL -> 0xFF5030;
            case ZOMBIE_CAPTAIN -> 0x9AB07A;
            default -> GREEN;
        };
    }

    public static Vector3f beamRgb(DbzCharacter who) {
        int c = beamColour(who);
        return new Vector3f((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F, (c & 0xFF) / 255.0F);
    }

    public static float beamDamage(DbzCharacter who) {
        return switch (who) {
            case DOCTOR_DOOM -> 22.0F;
            case DOOM_GOD_EMPEROR -> 30.0F;
            case SENTINEL -> 14.0F;
            case HUMAN_TORCH -> 20.0F;
            case DOOMBOT -> 9.0F;
            default -> 14.0F;
        };
    }

    public static int beamCooldown(DbzCharacter who) {
        return switch (who) {
            case DOCTOR_DOOM, DOOM_GOD_EMPEROR -> 120;
            case SENTINEL -> 200;
            case DOOMBOT -> 260;
            default -> 240;
        };
    }

    public static KiShots.Style shotStyle(DbzCharacter who) {
        return switch (who) {
            case LATVERIAN_WITCH -> KiShots.Style.HEX;
            case MAGNETO -> KiShots.Style.METAL;
            case NAMOR, TALOKAN_WARRIOR -> KiShots.Style.WATER;
            case HUMAN_TORCH -> KiShots.Style.FIRE;
            case MISTER_FANTASTIC, INVISIBLE_WOMAN, THE_THING -> KiShots.Style.KI;
            default -> KiShots.Style.DOOM;
        };
    }

    // ---------------------------------------------------------------- the moves at range

    public static void use(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        DbzCharacter who = me.getCharacter();
        switch (who) {
            case DOCTOR_DOOM, DOOM_GOD_EMPEROR -> doom(me, server, target, distance, sees, who == DbzCharacter.DOOM_GOD_EMPEROR);
            case DOOMBOT -> {
                if (me.specialReady() && sees && distance > 5.0D && distance < 24.0D && me.getRandom().nextInt(3) == 0) {
                    me.startCharge(30);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 22.0D) {
                    me.kiBlast(server, target, 5.0F, 1);
                    me.blastCooldown(50 + me.getRandom().nextInt(30));
                }
            }
            case LATVERIAN_WITCH -> witch(me, server, target, distance, sees);
            case SENTINEL -> {
                if (me.blastReady() && distance < 5.5D) {
                    me.blastWave(server, 5.5D, 12.0F);
                    me.blastCooldown(90);
                } else if (me.specialReady() && sees && distance < 30.0D) {
                    me.startCharge(30);
                    sayOnce(me, server, "scan", 400);
                }
            }
            case MAGNETO -> magneto(me, server, target, distance, sees);
            case NAMOR -> namor(me, server, target, distance, sees);
            case TALOKAN_WARRIOR -> {
                if (me.blastReady() && sees && distance > 4.0D && distance < 18.0D) {
                    me.kiBlast(server, target, 5.0F, 1);
                    me.blastCooldown(70 + me.getRandom().nextInt(30));
                }
            }
            case MARVEL_ZOMBIE -> {
                // The hunger: faster when there's blood near.
                if (me.blastReady() && distance < 10.0D) {
                    me.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
                    me.blastCooldown(120);
                }
            }
            case ZOMBIE_CAPTAIN -> zombieCaptain(me, server, target, distance, sees);
            case MISTER_FANTASTIC -> reed(me, server, target, distance, sees);
            case INVISIBLE_WOMAN -> sue(me, server, target, distance);
            case HUMAN_TORCH -> {
                if (me.specialReady() && sees && distance < 26.0D && me.getRandom().nextInt(2) == 0) {
                    me.startCharge(30);
                    sayOnce(me, server, "nova", 300);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 24.0D) {
                    me.kiBlast(server, target, 6.0F, 2);
                    me.blastCooldown(40);
                    sayOnce(me, server, "flame_on", 1200);
                }
            }
            case THE_THING -> {
                if (me.specialReady() && distance < 4.5D) {
                    me.blastWave(server, 4.5D, 14.0F);
                    me.specialCooldown(140);
                } else if (me.specialReady() && distance > 9.0D) {
                    me.dash(server, target);
                    me.specialCooldown(80);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Victor von Doom: a basic two-bolt volley between his moves, and one special at a time in turn - Plasma Beam,
     * Photon Shot, Molecular Shield, Hidden Missiles, Doombots, Foot Dive; under half health or as God Emperor the
     * Photon Array, Sphere Flame, lightning and Doom's Time join in.
     */
    private static void doom(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees, boolean god) {
        if (me.specialReady() && (sees || distance < 8.0D)) {
            boolean late = god || me.getHealth() < me.getMaxHealth() * 0.5F;
            int moves = late ? 10 : 6;
            int move = me.getPersistentData().getInt(MOVE) % moves;
            me.getPersistentData().putInt(MOVE, move + 1 + (me.getRandom().nextInt(4) == 0 ? 1 : 0));
            switch (move) {
                case 0 -> {
                    me.startCharge(god ? 24 : 32);
                    sayOnce(me, server, "plasma", 500);
                }
                case 1 -> photonShot(me, server, target);
                case 2 -> molecularShield(me, server, target);
                case 3 -> hiddenMissiles(me, server, target);
                case 4 -> {
                    if (!summon(me, server, DbzCharacter.DOOMBOT, 2, 4)) {
                        photonShot(me, server, target);
                    }
                }
                case 5 -> footDive(me, server, target, distance);
                case 6 -> photonArray(me, server, target);
                case 7 -> sphereFlame(me, server, target);
                case 8 -> hellAnswers(me, server, target);
                default -> doomsTime(me, server, target);
            }
            me.specialCooldown(god ? 70 : 100);
            return;
        }
        if (me.blastReady() && sees && distance > 3.0D && distance < 26.0D) {
            me.kiBlast(server, target, god ? 8.0F : 6.0F, god ? 3 : 2);
            me.blastCooldown(god ? 40 : 55);
        }
    }

    /** A ball of light over his head, then bolts every way at once (with a few at you). */
    private static void photonShot(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        me.setAction(DbzAction.HANDS_UP);
        KiFx.charge(server, me, 0xFF4040, 1.6F, 20, KiFx.AT_OVERHEAD);
        server.playSound(null, me.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2F, 1.6F);
        later(me, 20, () -> {
            Vec3 from = me.getEyePosition().add(0.0D, 1.2D, 0.0D);
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2.0D / 12.0D;
                Vec3 dir = new Vec3(Math.cos(angle), -0.12D, Math.sin(angle));
                KiShots.fire(server, me, KiShots.Style.DOOM, from, dir, 1.1D, 26.0D, 7.0F, 0.0D, me::isEnemy);
            }
            if (target.isAlive()) {
                KiShots.fire(server, me, KiShots.Style.DOOM, from, target.getBoundingBox().getCenter().subtract(from), 1.4D, 30.0D, 7.0F, 0.0D,
                        me::isEnemy);
            }
            me.setAction(DbzAction.FIRE);
        });
    }

    /** Stones lifted round him (a shield: he takes half), then hurled one after another. */
    private static void molecularShield(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        me.raiseBarrier(80);
        me.setAction(DbzAction.GUARD);
        sayOnce(me, server, "shield", 500);
        for (int i = 0; i < 4; i++) {
            int n = i;
            later(me, 30 + n * 8, () -> {
                double angle = n * Math.PI / 2.0D + me.tickCount * 0.1D;
                Vec3 from = me.position().add(Math.cos(angle) * 1.8D, 1.6D + (n % 2) * 0.6D, Math.sin(angle) * 1.8D);
                Vec3 aim = target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(3.0D));
                KiShots.fire(server, me, KiShots.Style.METAL, from, aim.subtract(from), 1.3D, 28.0D, 8.0F, 0.0D, me::isEnemy);
                server.sendParticles(ParticleTypes.POOF, from.x, from.y, from.z, 4, 0.2D, 0.2D, 0.2D, 0.02D);
            });
        }
    }

    /** Four spots round you light up red; a moment later the shoulder missiles come down on them. */
    private static void hiddenMissiles(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        me.setAction(DbzAction.FIRE);
        server.playSound(null, me.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.5F, 0.7F);
        for (int i = 0; i < 4; i++) {
            Vec3 spot = i == 0 ? target.position()
                    : target.position().add((me.getRandom().nextDouble() - 0.5D) * 7.0D, 0.0D, (me.getRandom().nextDouble() - 0.5D) * 7.0D);
            KiFx.burst(server, spot.add(0.0D, 0.1D, 0.0D), 0xFF3030, 2.4F, 26, KiFx.RING);
            later(me, 26 + i * 3, () -> {
                Vec3 high = spot.add(0.0D, 18.0D, 0.0D);
                KiShots.fire(server, me, KiShots.Style.DOOM_SPHERE, high, new Vec3(0.0D, -1.0D, 0.0D), 1.8D, 22.0D, 10.0F, 2.5D, me::isEnemy);
            });
        }
    }

    /** Up on the rocket boots and down feet first: a shockwave where he lands. */
    private static void footDive(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance) {
        Vec3 to = target.position().subtract(me.position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        if (flat.lengthSqr() > 1.0E-4D) {
            flat = flat.normalize().scale(Math.min(1.6D, distance * 0.12D + 0.4D));
        }
        me.setDeltaMovement(flat.x, 0.9D, flat.z);
        me.hurtMarked = true;
        me.setAction(DbzAction.FLY);
        server.sendParticles(ParticleTypes.FLAME, me.getX(), me.getY(), me.getZ(), 16, 0.2D, 0.1D, 0.2D, 0.05D);
        server.playSound(null, me.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 1.2F, 0.8F);
        later(me, 22, () -> {
            me.setDeltaMovement(me.getDeltaMovement().x, -1.6D, me.getDeltaMovement().z);
            me.hurtMarked = true;
        });
        later(me, 30, () -> me.blastWave(server, 5.0D, 13.0F));
    }

    /** The big one: three sweeping fans of bolts. */
    private static void photonArray(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        me.setAction(DbzAction.CHARGE);
        sayOnce(me, server, "array", 500);
        KiFx.charge(server, me, GREEN, 1.4F, 24, KiFx.AT_HANDS);
        for (int volley = 0; volley < 3; volley++) {
            int v = volley;
            later(me, 24 + v * 10, () -> {
                if (!target.isAlive()) {
                    return;
                }
                me.setAction(DbzAction.FIRE);
                Vec3 from = me.getEyePosition().subtract(0.0D, 0.3D, 0.0D);
                Vec3 aim = target.getBoundingBox().getCenter().subtract(from);
                double base = Math.atan2(aim.z, aim.x);
                double rise = aim.y / Math.max(1.0D, Math.sqrt(aim.x * aim.x + aim.z * aim.z));
                for (int i = -3; i <= 3; i++) {
                    double angle = base + i * 0.16D + (v - 1) * 0.08D;
                    KiShots.fire(server, me, KiShots.Style.DOOM, from, new Vec3(Math.cos(angle), rise, Math.sin(angle)), 1.5D, 32.0D, 6.0F, 0.0D,
                            me::isEnemy);
                }
            });
        }
    }

    /** A slow green sun thrown at you: get out of its way. */
    private static void sphereFlame(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        me.setAction(DbzAction.HANDS_UP);
        sayOnce(me, server, "sphere", 500);
        KiFx.charge(server, me, GREEN, 3.0F, 30, KiFx.AT_OVERHEAD);
        later(me, 30, () -> {
            me.setAction(DbzAction.FIRE);
            Vec3 from = me.getEyePosition().add(0.0D, 2.0D, 0.0D);
            KiShots.fire(server, me, KiShots.Style.DOOM_SPHERE, from, target.getBoundingBox().getCenter().subtract(from), 0.55D, 34.0D, 20.0F,
                    4.5D, me::isEnemy);
        });
    }

    /** "Hell answers to me": lightning out of a clear sky and a wave of green that throws everyone back. */
    private static void hellAnswers(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        sayOnce(me, server, "hell", 500);
        me.setAction(DbzAction.TRANSFORM);
        for (int i = 0; i < 3; i++) {
            int n = i;
            Vec3 spot = target.position().add(n == 0 ? 0.0D : (me.getRandom().nextDouble() - 0.5D) * 6.0D, 0.0D,
                    n == 0 ? 0.0D : (me.getRandom().nextDouble() - 0.5D) * 6.0D);
            KiFx.burst(server, spot.add(0.0D, 0.1D, 0.0D), 0xB8FFC8, 1.8F, 14 + n * 6, KiFx.RING);
            later(me, 14 + n * 6, () -> {
                LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(server);
                if (bolt != null) {
                    bolt.moveTo(spot.x, spot.y, spot.z);
                    bolt.setVisualOnly(true);
                    server.addFreshEntity(bolt);
                }
                for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(1.8D),
                        me::isEnemy)) {
                    hit.hurt(me.damageSources().lightningBolt(), 9.0F);
                }
            });
        }
        later(me, 30, () -> me.blastWave(server, 7.0D, 10.0F));
    }

    /**
     * Doom's Time: a cage of light marks where you stand; still in it a second later and you're held there, lit up
     * by his lightning, until it blows.
     */
    private static void doomsTime(DbzFighterEntity me, ServerLevel server, LivingEntity target) {
        Vec3 spot = target.position();
        sayOnce(me, server, "time", 500);
        me.setAction(DbzAction.HANDS_UP);
        KiFx.burst(server, spot.add(0.0D, 0.1D, 0.0D), GREEN, 2.5F, 24, KiFx.RING);
        KiFx.pillar(server, null, spot, GREEN, 4.0F, 24);
        server.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.5F, 0.6F);
        later(me, 24, () -> {
            if (!target.isAlive() || target.position().distanceTo(spot) > 2.8D) {
                return;
            }
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 6, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, 200, false, false));
            for (int i = 0; i < 4; i++) {
                later(me, i * 10, () -> {
                    if (target.isAlive()) {
                        target.invulnerableTime = 0;
                        target.hurt(me.damageSources().mobAttack(me), 3.0F);
                        KiFx.burst(server, target.getBoundingBox().getCenter(), 0xB8FFC8, 1.4F, 8, KiFx.SPARK);
                        server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 0.6F, 1.8F);
                    }
                });
            }
            later(me, 44, () -> {
                if (target.isAlive()) {
                    target.invulnerableTime = 0;
                    target.hurt(me.damageSources().mobAttack(me), 12.0F);
                    KiFx.burst(server, target.getBoundingBox().getCenter(), GREEN, 3.0F, 18, KiFx.BLAST);
                    server.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.2F);
                }
            });
        });
    }

    /** The Latverian witches: hexes, a curse that lifts you off your feet, and their own strength poured into Doom. */
    private static void witch(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (me.specialReady() && sees && distance < 18.0D) {
            if (me.getRandom().nextBoolean()) {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                KiFx.burst(server, target.getBoundingBox().getCenter(), 0xE040A0, 2.0F, 16, KiFx.FLASH);
                sayOnce(me, server, "curse", 300);
            } else {
                for (DbzFighterEntity doom : server.getEntitiesOfClass(DbzFighterEntity.class, me.getBoundingBox().inflate(20.0D),
                        f -> f.getCharacter() == DbzCharacter.DOCTOR_DOOM || f.getCharacter() == DbzCharacter.DOOM_GOD_EMPEROR)) {
                    doom.heal(25.0F);
                    KiFx.beam(server, me.getEyePosition(), doom.getBoundingBox().getCenter(), 0xE040A0, 0.4F, 16, KiFx.BEAM_THIN, me,
                            KiFx.AT_HANDS, doom);
                }
            }
            me.specialCooldown(160);
        } else if (me.blastReady() && sees && distance > 3.0D && distance < 22.0D) {
            me.kiBlast(server, target, 5.0F, 1);
            me.blastCooldown(45);
        }
    }

    /** Is there iron on them? A suit, or iron/netherite gear. */
    private static boolean metal(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.getItem() instanceof com.pfkfks.flightsuit.suit.SuitArmorItem) {
                return true;
            }
            String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()) == null ? ""
                    : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
            if (id.startsWith("iron_") || id.startsWith("netherite_") || id.startsWith("chainmail_")) {
                return true;
            }
        }
        return target instanceof com.pfkfks.flightsuit.entity.SuitCompanionEntity;
    }

    /**
     * Magneto: a suit is nothing but metal to him - he drags the one wearing it in; a storm of steel from above; a
     * magnetic shield when he's hurt.
     */
    private static void magneto(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (me.specialReady()) {
            int move = me.getPersistentData().getInt(MOVE);
            me.getPersistentData().putInt(MOVE, move + 1);
            if (move % 3 == 0 && sees && distance > 4.0D && distance < 26.0D && metal(target)) {
                Vec3 pull = me.position().subtract(target.position()).normalize().scale(Math.min(2.4D, distance * 0.16D));
                target.setDeltaMovement(pull.x, 0.55D, pull.z);
                target.hurtMarked = true;
                KiFx.beam(server, me.getEyePosition(), target.getBoundingBox().getCenter(), 0xD040FF, 0.5F, 14, KiFx.BEAM_SPIRAL, me,
                        KiFx.AT_HANDS, target);
                server.playSound(null, target.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
                sayOnce(me, server, target instanceof Player ? "pull" : "pull_suit", 400);
                me.specialCooldown(90);
            } else if (move % 3 == 1 && me.getHealth() < me.getMaxHealth() * 0.75F) {
                me.raiseBarrier(100);
                me.setAction(DbzAction.GUARD);
                KiFx.burst(server, me.getBoundingBox().getCenter(), 0xD040FF, 2.5F, 20, KiFx.ORB);
                sayOnce(me, server, "shield", 400);
                me.specialCooldown(120);
            } else {
                // The storm of steel: rings mark where it comes down.
                me.setAction(DbzAction.HANDS_UP);
                sayOnce(me, server, "storm", 400);
                for (int i = 0; i < 6; i++) {
                    Vec3 spot = target.position().add((me.getRandom().nextDouble() - 0.5D) * 8.0D, 0.0D, (me.getRandom().nextDouble() - 0.5D) * 8.0D);
                    KiFx.burst(server, spot.add(0.0D, 0.1D, 0.0D), 0xC8D2E0, 1.6F, 20, KiFx.RING);
                    int n = i;
                    later(me, 20 + n * 2, () -> KiShots.fire(server, me, KiShots.Style.METAL, spot.add(0.0D, 16.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D),
                            1.6D, 20.0D, 9.0F, 0.0D, me::isEnemy));
                }
                me.specialCooldown(110);
            }
            return;
        }
        if (me.blastReady() && sees && distance > 3.0D && distance < 24.0D) {
            me.kiBlast(server, target, 6.0F, 3);
            me.blastCooldown(50);
        }
    }

    /** Namor: lunges with the trident, "Imperius Rex!" throws you up on a column of water, calls up Talokan. */
    private static void namor(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (me.specialReady()) {
            int move = me.getPersistentData().getInt(MOVE);
            me.getPersistentData().putInt(MOVE, move + 1);
            if (move % 3 == 1 && sees && distance < 20.0D) {
                Vec3 spot = target.position();
                sayOnce(me, server, "rex", 400);
                KiFx.burst(server, spot.add(0.0D, 0.1D, 0.0D), 0x40B8FF, 2.2F, 16, KiFx.RING);
                later(me, 16, () -> {
                    KiFx.pillar(server, null, spot, 0x40B8FF, 8.0F, 20);
                    server.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 1.0D, spot.z, 80, 1.0D, 2.0D, 1.0D, 0.3D);
                    server.playSound(null, blockPos(spot), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.6F);
                    for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(2.2D, 3.0D, 2.2D),
                            me::isEnemy)) {
                        hit.hurt(me.damageSources().mobAttack(me), 10.0F);
                        hit.setDeltaMovement(hit.getDeltaMovement().x, 1.3D, hit.getDeltaMovement().z);
                        hit.hurtMarked = true;
                    }
                });
                me.specialCooldown(110);
            } else if (move % 3 == 2 && me.getHealth() < me.getMaxHealth() * 0.6F && summon(me, server, DbzCharacter.TALOKAN_WARRIOR, 3, 5)) {
                sayOnce(me, server, "talokan", 400);
                me.specialCooldown(200);
            } else if (distance > 5.0D) {
                me.dash(server, target);
                me.specialCooldown(60);
            } else {
                me.specialCooldown(30);
            }
            return;
        }
        if (me.blastReady() && sees && distance > 4.0D && distance < 22.0D) {
            me.kiBlast(server, target, 6.0F, 2);
            me.blastCooldown(60);
        }
    }

    private static net.minecraft.core.BlockPos blockPos(Vec3 at) {
        return net.minecraft.core.BlockPos.containing(at);
    }

    /** The Zombie Captain: the shield thrown (it cuts through a line), a leaping smash, the horde called in. */
    private static void zombieCaptain(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (!me.specialReady()) {
            return;
        }
        int move = me.getPersistentData().getInt(MOVE);
        me.getPersistentData().putInt(MOVE, move + 1);
        if (move % 3 == 0 && sees && distance > 4.0D && distance < 24.0D) {
            me.setAction(DbzAction.FIRE);
            Vec3 from = me.getEyePosition().subtract(0.0D, 0.4D, 0.0D);
            KiShots.fire(server, me, KiShots.Style.DISC, from, target.getBoundingBox().getCenter().subtract(from), 1.3D, 26.0D, 11.0F, 1.2D,
                    me::isEnemy);
            sayOnce(me, server, "shield", 400);
            me.specialCooldown(90);
        } else if (move % 3 == 1 && me.getHealth() < me.getMaxHealth() * 0.65F && summon(me, server, DbzCharacter.MARVEL_ZOMBIE, 3, 6)) {
            sayOnce(me, server, "horde", 400);
            me.specialCooldown(220);
        } else if (distance < 12.0D) {
            footDive(me, server, target, distance);
            me.specialCooldown(110);
        } else {
            me.dash(server, target);
            me.specialCooldown(50);
        }
    }

    /** Reed: an arm stretched out across the field - a blow from ten blocks off, or a foe hauled in. */
    private static void reed(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (me.specialReady() && sees && distance > 3.0D && distance < 11.0D) {
            me.setAction(DbzAction.PUNCH_R);
            KiFx.beam(server, me.getEyePosition().subtract(0.0D, 0.3D, 0.0D), target.getBoundingBox().getCenter(), 0x3A6CFF, 0.35F, 7,
                    KiFx.BEAM_THIN, me, KiFx.AT_HANDS, target);
            target.hurt(me.damageSources().mobAttack(me), 11.0F);
            Vec3 push = target.position().subtract(me.position()).multiply(1.0D, 0.0D, 1.0D);
            if (me.getRandom().nextBoolean() && push.lengthSqr() > 1.0E-4D) {
                // Hauled in.
                push = push.normalize().scale(-1.2D);
            } else if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(1.0D);
            }
            target.push(push.x, 0.3D, push.z);
            target.hurtMarked = true;
            server.playSound(null, target.blockPosition(), SoundEvents.SLIME_ATTACK, SoundSource.NEUTRAL, 1.0F, 0.7F);
            sayOnce(me, server, "stretch", 600);
            me.specialCooldown(50);
        }
    }

    /** Sue: force fields over our side (absorption), a push that throws foes back, and now and then out of sight. */
    private static void sue(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance) {
        if (!me.specialReady()) {
            if (me.isInvisible() && me.tickCount % 60 == 0) {
                me.setInvisible(false);
            }
            return;
        }
        int move = me.getPersistentData().getInt(MOVE);
        me.getPersistentData().putInt(MOVE, move + 1);
        if (move % 3 == 0) {
            for (LivingEntity friend : server.getEntitiesOfClass(LivingEntity.class, me.getBoundingBox().inflate(14.0D),
                    e -> e instanceof Player p && !p.isSpectator() || e instanceof DbzFighterEntity f && !f.getCharacter().isFoe()
                            || e instanceof com.pfkfks.flightsuit.entity.SuitCompanionEntity)) {
                friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
                KiFx.burst(server, friend.getBoundingBox().getCenter(), 0x9AE6FF, friend.getBbHeight() * 0.8F, 30, KiFx.ORB);
            }
            sayOnce(me, server, "field", 400);
            me.specialCooldown(300);
        } else if (move % 3 == 1 && distance < 7.0D) {
            me.blastWave(server, 6.0D, 8.0F);
            me.specialCooldown(160);
        } else {
            me.setInvisible(true);
            me.specialCooldown(120);
        }
    }

    // ---------------------------------------------------------------- reinforcements, hits taken, the fallen

    /**
     * Calls {@code count} of {@code who} in round {@code me} (Doombots, Talokan, the horde) while fewer than
     * {@code cap} of them are about; they join the fight (DoomSaga.reinforce). False if there were enough already.
     */
    private static boolean summon(DbzFighterEntity me, ServerLevel server, DbzCharacter who, int count, int cap) {
        int around = server.getEntitiesOfClass(DbzFighterEntity.class, me.getBoundingBox().inflate(32.0D),
                f -> f.getCharacter() == who && f.isAlive()).size();
        if (around >= cap) {
            return false;
        }
        me.setAction(DbzAction.HANDS_UP);
        for (int i = 0; i < Math.min(count, cap - around); i++) {
            double angle = me.getRandom().nextDouble() * Math.PI * 2.0D;
            Vec3 at = me.position().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
            Vec3 spot = com.pfkfks.flightsuit.suit.SuitSkills.standable(server, me, at);
            if (spot == null) {
                spot = me.position();
            }
            DbzFighterEntity fighter = DoomSaga.reinforce(server, me, who, spot);
            if (fighter != null) {
                KiFx.burst(server, fighter.getBoundingBox().getCenter(), who == DbzCharacter.DOOMBOT ? GREEN : 0x40B8FF, 1.6F, 14, KiFx.FLASH);
            }
        }
        if (who == DbzCharacter.DOOMBOT) {
            sayOnce(me, server, "doombots", 500);
        }
        return true;
    }

    /** Doom's armour is electrified: hit him up close and now and then it hits back. */
    public static void onHurt(ServerLevel server, DbzFighterEntity me, DamageSource source) {
        DbzCharacter who = me.getCharacter();
        if ((who != DbzCharacter.DOCTOR_DOOM && who != DbzCharacter.DOOM_GOD_EMPEROR && who != DbzCharacter.DOOMBOT)
                || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker.distanceToSqr(me) > 16.0D
                || me.getRandom().nextFloat() > (who == DbzCharacter.DOOMBOT ? 0.1F : 0.3F)) {
            return;
        }
        attacker.hurt(me.damageSources().lightningBolt(), who == DbzCharacter.DOOMBOT ? 3.0F : 6.0F);
        Vec3 push = attacker.position().subtract(me.position()).multiply(1.0D, 0.0D, 1.0D);
        if (push.lengthSqr() > 1.0E-4D) {
            push = push.normalize().scale(0.9D);
            attacker.push(push.x, 0.3D, push.z);
            attacker.hurtMarked = true;
        }
        KiFx.burst(server, me.getBoundingBox().getCenter(), 0xB8FFC8, 2.0F, 10, KiFx.SPARK);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, me.getX(), me.getY() + 1.0D, me.getZ(), 20, 0.5D, 0.8D, 0.5D, 0.3D);
        server.playSound(null, me.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 0.6F, 1.6F);
        if (who != DbzCharacter.DOOMBOT) {
            sayOnce(me, server, "touch", 400);
        }
    }

    /** A minion down: Doombots and Sentinels go up in sparks (and leave scrap), the dead fall to dust. */
    public static void minionDown(ServerLevel server, DbzFighterEntity me, DamageSource source) {
        DbzCharacter who = me.getCharacter();
        Vec3 c = me.getBoundingBox().getCenter();
        switch (who) {
            case DOOMBOT, SENTINEL -> {
                KiFx.burst(server, c, 0xFFC870, who == DbzCharacter.SENTINEL ? 5.0F : 1.8F, 16, KiFx.BLAST);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.4D, 0.6D, 0.4D, 0.4D);
                server.playSound(null, me.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, who == DbzCharacter.SENTINEL ? 2.5F : 0.8F,
                        1.4F);
                if (source.getEntity() instanceof Player) {
                    int scrap = who == DbzCharacter.SENTINEL ? 6 : 1 + me.getRandom().nextInt(3);
                    me.spawnAtLocation(new ItemStack(who == DbzCharacter.SENTINEL ? Items.IRON_INGOT : Items.IRON_NUGGET, scrap * (who == DbzCharacter.SENTINEL ? 1 : 3)));
                    if (me.getRandom().nextInt(who == DbzCharacter.SENTINEL ? 1 : 3) == 0) {
                        me.spawnAtLocation(new ItemStack(Items.REDSTONE, 2 + me.getRandom().nextInt(3)));
                    }
                }
            }
            case MARVEL_ZOMBIE -> {
                server.sendParticles(ParticleTypes.ASH, c.x, c.y, c.z, 30, 0.3D, 0.6D, 0.3D, 0.02D);
                if (source.getEntity() instanceof Player && me.getRandom().nextBoolean()) {
                    me.spawnAtLocation(new ItemStack(Items.ROTTEN_FLESH, 1 + me.getRandom().nextInt(2)));
                }
            }
            case TALOKAN_WARRIOR -> {
                server.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 30, 0.3D, 0.6D, 0.3D, 0.1D);
                if (source.getEntity() instanceof Player && me.getRandom().nextBoolean()) {
                    me.spawnAtLocation(new ItemStack(Items.PRISMARINE_SHARD, 1 + me.getRandom().nextInt(3)));
                }
            }
            case LATVERIAN_WITCH -> {
                KiFx.burst(server, c, 0xE040A0, 1.6F, 14, KiFx.FLASH);
                server.sendParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 30, 0.3D, 0.6D, 0.3D, 0.1D);
            }
            default -> {
            }
        }
    }

    /** A line no more often than every {@code gap} ticks (so a fight isn't all chat). */
    private static void sayOnce(DbzFighterEntity me, ServerLevel server, String key, int gap) {
        long now = server.getGameTime();
        String tag = TALKED + "_" + key;
        if (now - me.getPersistentData().getLong(tag) < gap && me.getPersistentData().contains(tag)) {
            return;
        }
        me.getPersistentData().putLong(tag, now);
        me.say(server, me.getCharacter().line(key));
    }

    /** For a cutscene's "the Doombot's head comes off" moment and the like: the Doombot blast without dying. */
    static void sparks(ServerLevel server, Vec3 at) {
        KiFx.burst(server, at, 0xFFC870, 2.4F, 16, KiFx.BLAST);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.5D, 0.8D, 0.5D, 0.4D);
        server.playSound(null, blockPos(at), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.5F);
    }
}
