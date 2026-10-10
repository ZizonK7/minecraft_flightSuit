package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.suit.StolenSkill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Each Dragon Ball character's moves at range, besides the melee combo every fighter has (DbzFighterEntity.ComboGoal):
 * ki blasts, dashes, charged beams, blast waves, and their specials. Split out of DbzFighterEntity in M17; the
 * charged beams are the same moves the phantom can steal (StolenSkill), and every fighter records the stealable ones
 * it uses (DbzFighterEntity.usedSkill) - each fight has at least one to steal.
 */
final class DbzMoves {
    private DbzMoves() {
    }

    /** The stealable beam a character charges (null: a beam of its own, or none). */
    static @Nullable StolenSkill beamOf(DbzCharacter who) {
        return switch (who) {
            case GOKU, GOKU_SSJ, CELL, CELL_IMPERFECT, CELL_SEMI, GOHAN_TEEN, GOHAN_TEEN_SSJ2, GOHAN_KID -> StolenSkill.KAMEHAMEHA;
            case VEGETA -> StolenSkill.GALICK_GUN;
            case PICCOLO -> StolenSkill.SPECIAL_BEAM_CANNON;
            default -> null;
        };
    }

    static int chargeTicks(DbzCharacter who) {
        return switch (who) {
            case PICCOLO -> 40;
            case VEGETA, CELL, FRIEZA, FRIEZA_FINAL -> 40;
            default -> 30;
        };
    }

    static int beamCooldown(DbzCharacter who) {
        return switch (who) {
            case VEGETA, RECOOME -> 240;
            case CELL, FRIEZA, FRIEZA_FINAL, PICCOLO -> 200;
            default -> 160;
        };
    }

    static float beamDamage(DbzCharacter who) {
        return switch (who) {
            case VEGETA -> 18.0F;
            case RECOOME, TRUNKS -> 15.0F;
            case CELL, CELL_SEMI -> 22.0F;
            case FRIEZA, FRIEZA_FINAL -> 24.0F;
            case PICCOLO -> 26.0F;
            case GOKU_SSJ, GOHAN_TEEN_SSJ2 -> 28.0F;
            default -> 14.0F;
        };
    }

    /** What a character always "has" for the phantom to steal, even before using it (its kind's move). */
    static @Nullable StolenSkill signature(DbzCharacter who) {
        return switch (who) {
            case SAIBAMAN, CELL_JR -> StolenSkill.SELF_DESTRUCT;
            default -> null;
        };
    }

    /** At zero health a boss with a story moment left holds on at a sliver (the moment plays instead). */
    static boolean holdsOn(DbzFighterEntity boss, ServerLevel server) {
        return boss.runMilestones(0.0F);
    }

    /** The moves at range, by character (the melee combo runs by itself). */
    static void use(DbzFighterEntity me, ServerLevel server, LivingEntity target, double distance, boolean sees) {
        DbzCharacter who = me.getCharacter();
        switch (who) {
            case RADITZ -> {
                if (me.specialReady() && distance > 10.0D) {
                    me.dash(server, target);
                    me.specialCooldown(100);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 24.0D) {
                    me.kiBlast(server, target, 6.0F, me.getHealth() < me.getMaxHealth() / 2 ? 2 : 1);
                    me.blastCooldown(60);
                }
            }
            case NAPPA, DODORIA -> {
                if (me.specialReady() && distance < 7.0D) {
                    me.blastWave(server, 6.0D, 11.0F);
                    me.specialCooldown(120);
                } else if (who == DbzCharacter.DODORIA && me.specialReady() && distance > 10.0D) {
                    me.dash(server, target);
                    me.specialCooldown(80);
                } else if (who == DbzCharacter.NAPPA && me.blastReady() && sees && distance < 24.0D) {
                    me.kiBlast(server, target, 8.0F, 1);
                    me.blastCooldown(80);
                }
            }
            case VEGETA -> {
                if (me.specialReady() && sees && distance < 30.0D) {
                    me.startCharge(chargeTicks(who));
                    me.say(server, who.line("galick"));
                } else if (me.blastReady() && sees && distance < 26.0D) {
                    me.kiBlast(server, target, 7.0F, me.getHealth() < me.getMaxHealth() / 2 ? 3 : 2);
                    me.blastCooldown(50);
                }
                if (me.specialReady() && distance > 12.0D && !sees) {
                    me.dash(server, target);
                    me.specialCooldown(60);
                }
            }
            case GOKU, GOKU_SSJ, GOHAN_TEEN, GOHAN_TEEN_SSJ2 -> {
                if (me.specialReady() && sees && distance < 28.0D) {
                    me.startCharge(chargeTicks(who));
                    me.say(server, who.line("kamehameha"));
                } else if (me.blastReady() && sees && distance > 4.0D && distance < 22.0D) {
                    me.kiBlast(server, target, who == DbzCharacter.GOKU ? 6.0F : 9.0F, 1);
                    me.blastCooldown(70);
                }
                if (who == DbzCharacter.GOKU_SSJ || who == DbzCharacter.GOHAN_TEEN_SSJ2) {
                    if (me.tickCount % 2 == 0) {
                        me.goldAura(server);
                    }
                }
            }
            case GOHAN_KID -> {
                if (me.blastReady() && sees && distance > 3.0D && distance < 20.0D) {
                    me.kiBlast(server, target, 5.0F, 1);
                    me.blastCooldown(80);
                }
            }
            case PICCOLO -> {
                if (me.specialReady() && sees && distance < 30.0D) {
                    me.startCharge(chargeTicks(who));
                    me.say(server, who.line("beam"));
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 24.0D) {
                    me.kiBlast(server, target, 7.0F, 2);
                    me.blastCooldown(60);
                }
            }
            case KRILLIN -> {
                if (me.specialReady() && sees && distance < 26.0D) {
                    me.say(server, who.line("disc"));
                    me.setAction(DbzAction.FIRE);
                    StolenSkill.DESTRUCTO_DISC.cast(server, me, me.getEyePosition().add(0.0D, 0.6D, 0.0D),
                            target.getBoundingBox().getCenter().subtract(me.getEyePosition()), target, 16.0F, me::isEnemy);
                    me.usedSkill(StolenSkill.DESTRUCTO_DISC);
                    me.specialCooldown(180);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 22.0D) {
                    me.kiBlast(server, target, 5.0F, 1);
                    me.blastCooldown(60);
                }
            }
            case TIEN -> {
                if (me.specialReady() && distance < 10.0D) {
                    me.say(server, who.line("flare"));
                    StolenSkill.SOLAR_FLARE.cast(server, me, me.getEyePosition(), me.getLookAngle(), target, 0.0F, me::isEnemy);
                    me.usedSkill(StolenSkill.SOLAR_FLARE);
                    me.specialCooldown(300);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 22.0D) {
                    me.kiBlast(server, target, 6.0F, 2);
                    me.blastCooldown(70);
                }
            }
            case YAMCHA, ANDROID_16 -> {
                if (me.blastReady() && sees && distance > 3.0D && distance < 20.0D) {
                    me.kiBlast(server, target, who == DbzCharacter.ANDROID_16 ? 8.0F : 5.0F, 1);
                    me.blastCooldown(70);
                }
            }
            case SAIBAMAN -> {
                if (me.getHealth() < me.getMaxHealth() * 0.35F && distance < 2.5D) {
                    me.selfDestruct(server);
                }
            }
            case FRIEZA_SOLDIER, CELL_JR -> {
                if (who == DbzCharacter.CELL_JR && me.getHealth() < me.getMaxHealth() * 0.25F && distance < 3.0D) {
                    me.selfDestruct(server);
                } else if (me.blastReady() && sees && distance > 3.0D && distance < 20.0D) {
                    me.kiBlast(server, target, who == DbzCharacter.CELL_JR ? 6.0F : 5.0F, 1);
                    me.blastCooldown(who == DbzCharacter.CELL_JR ? 50 : 70);
                }
            }
            case ZARBON, JEICE, GINYU -> {
                if (me.specialReady() && distance > 10.0D) {
                    me.dash(server, target);
                    me.specialCooldown(90);
                } else if (me.blastReady() && sees && distance < 24.0D) {
                    me.kiBlast(server, target, who == DbzCharacter.GINYU ? 9.0F : 7.0F, who == DbzCharacter.JEICE ? 2 : 1);
                    me.blastCooldown(55);
                }
            }
            case GULDO -> {
                // Time freeze: a moment where you can't move at all.
                if (me.specialReady() && sees && distance < 16.0D) {
                    com.pfkfks.flightsuit.suit.Stasis.hold(server, target, 40, false);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6));
                    me.say(server, who.line("freeze"));
                    me.specialCooldown(200);
                } else if (me.blastReady() && sees && distance < 20.0D) {
                    me.kiBlast(server, target, 5.0F, 1);
                    me.blastCooldown(60);
                }
            }
            case RECOOME, TRUNKS -> {
                if (me.specialReady() && sees && distance < 28.0D) {
                    me.startCharge(30);
                    me.say(server, who.line("charge"));
                } else if (me.blastReady() && sees && distance > 4.0D && distance < 22.0D) {
                    me.kiBlast(server, target, 7.0F, 1);
                    me.blastCooldown(60);
                }
            }
            case CELL, CELL_IMPERFECT, CELL_SEMI -> {
                if (me.specialReady() && sees && distance < 28.0D) {
                    me.startCharge(chargeTicks(who));
                    me.say(server, DbzCharacter.CELL.line("charge"));
                } else if (me.blastReady() && sees && distance > 4.0D && distance < 22.0D) {
                    me.kiBlast(server, target, 9.0F, 2);
                    me.blastCooldown(60);
                }
                if (me.tickCount % 400 == 0) {
                    // Solar Flare: everyone around can't see for a moment.
                    StolenSkill.SOLAR_FLARE.cast(server, me, me.getEyePosition(), me.getLookAngle(), target, 0.0F, me::isEnemy);
                    me.usedSkill(StolenSkill.SOLAR_FLARE);
                    me.say(server, DbzCharacter.CELL.line("flare"));
                }
            }
            case BURTER -> {
                // The fastest in the universe: never far from you.
                if (me.specialReady() && distance > 5.0D) {
                    me.dash(server, target);
                    me.specialCooldown(40);
                }
            }
            case FRIEZA, FRIEZA_FINAL -> {
                boolean finalForm = who == DbzCharacter.FRIEZA_FINAL || me.isTransformedNow();
                if (me.specialReady() && sees && distance < 30.0D && finalForm) {
                    me.startCharge(50);
                    me.say(server, DbzCharacter.FRIEZA.line("charge"));
                } else if (me.blastReady() && sees && distance < 32.0D) {
                    // The Death Beam: quick and thin - the stealable one.
                    me.setAction(DbzAction.FIRE);
                    StolenSkill.DEATH_BEAM.cast(server, me, me.getEyePosition(), target.getBoundingBox().getCenter().subtract(me.getEyePosition()),
                            target, finalForm ? 12.0F : 9.0F, me::isEnemy);
                    me.usedSkill(StolenSkill.DEATH_BEAM);
                    me.blastCooldown(45);
                }
            }
            case ANDROID_17, ANDROID_18 -> {
                if (who == DbzCharacter.ANDROID_17 && me.specialReady() && me.getHealth() < me.getMaxHealth() * 0.8F) {
                    me.raiseBarrier(60);
                    me.setAction(DbzAction.GUARD);
                    me.say(server, who.line("barrier"));
                    me.specialCooldown(240);
                } else if (who == DbzCharacter.ANDROID_18 && me.specialReady() && distance > 8.0D) {
                    me.dash(server, target);
                    me.specialCooldown(100);
                }
                if (me.blastReady() && sees && distance < 24.0D) {
                    me.kiBlast(server, target, 7.0F, who == DbzCharacter.ANDROID_18 ? 3 : 2);
                    me.blastCooldown(55);
                }
            }
            case MAJIN_BUU, SUPER_BUU, SUPER_BUU_ABSORBED -> {
                if (me.specialReady() && sees && distance < 18.0D) {
                    // The candy beam (the phantom can steal it).
                    me.setAction(DbzAction.FIRE);
                    StolenSkill.CANDY_BEAM.cast(server, me, me.getEyePosition(), target.getBoundingBox().getCenter().subtract(me.getEyePosition()),
                            target, 4.0F, me::isEnemy);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    me.usedSkill(StolenSkill.CANDY_BEAM);
                    me.say(server, DbzCharacter.MAJIN_BUU.line("candy"));
                    me.specialCooldown(300);
                } else if (me.blastReady() && distance < 6.0D) {
                    me.blastWave(server, 5.0D, who == DbzCharacter.MAJIN_BUU ? 10.0F : 14.0F);
                    me.blastCooldown(100);
                } else if (who != DbzCharacter.MAJIN_BUU && me.blastReady() && sees && distance < 26.0D) {
                    me.kiBlast(server, target, 9.0F, 2);
                    me.blastCooldown(50);
                }
            }
            case KID_BUU -> {
                if (me.specialReady() && distance < 9.0D) {
                    me.blastWave(server, 8.0D, 14.0F);
                    me.specialCooldown(160);
                } else if (me.blastReady() && sees && distance < 26.0D) {
                    me.kiBlast(server, target, 8.0F, 3);
                    me.blastCooldown(50);
                } else if (me.specialReady() && distance > 12.0D) {
                    me.dash(server, target);
                    me.specialCooldown(60);
                }
            }
            case OOZARU_VEGETA -> {
                // The Great Ape: a beam from the mouth, and stamping shockwaves.
                if (me.specialReady() && sees && distance < 34.0D) {
                    me.setAction(DbzAction.FIRE);
                    com.pfkfks.flightsuit.suit.SuitSkills.beam(server, me, me.getEyePosition(),
                            target.getBoundingBox().getCenter().subtract(me.getEyePosition()), 34.0D, 2.2D, 18.0F, 1.6D,
                            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.6F, 0.85F), 3.0F),
                            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 1.0F, 1.0F), 1.6F), me::isEnemy);
                    me.specialCooldown(140);
                } else if (me.blastReady() && distance < 10.0D) {
                    me.setAction(DbzAction.TRANSFORM);
                    com.pfkfks.flightsuit.suit.HulkbusterArts.slam(server, me, 8.0D, 14.0F, me::isEnemy);
                    me.blastCooldown(90);
                }
            }
            default -> {
            }
        }
        if (me.tickCount % 40 == 0 && who.isFoe() && who.role() == DbzCharacter.Role.BOSS) {
            server.sendParticles(ParticleTypes.ENCHANT, me.getX(), me.getY() + 1.0D, me.getZ(), 1, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }
}
