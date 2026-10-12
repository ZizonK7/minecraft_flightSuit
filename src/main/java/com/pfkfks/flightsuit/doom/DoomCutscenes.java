package com.pfkfks.flightsuit.doom;

import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscene.End;
import com.pfkfks.flightsuit.cutscene.Cutscenes;

import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_DOOM;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_FIRE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_CRACK;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_EARTH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_EXPLOSION;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_FLASH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_GREEN_PILLAR;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_LIGHTNING;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_RIFT;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_SMOKE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_BOOM;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_CHARGE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_POWER;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_PUNCH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_ROAR;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_THUNDER;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_WHOOSH;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.GUARD;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.HANDS_UP;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.HURT;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.PUNCH_R;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.TRANSFORM;
import static com.pfkfks.flightsuit.planet.dbz.DbzCharacter.*;

/**
 * The Doom saga's cutscenes (DESIGN.md 4-17), in story order, for the cutscene engine (cutscene.Cutscene). Ids are
 * "doom.<name>". In the fights the anchor is the sight's centre, yaw 0 (the Deadlands: 180) - the foes come from -z
 * (Castle Doom's keep is to the north), our side stands at +z; a y of 0 is the ground there. Cast ids are
 * DoomSaga.castId's ("doom" for every Doom, "reed", "sue", "johnny", "ben", otherwise the character's id).
 * Lines are dbz.flightsuit.<voice>.<key>, narration cutscene.flightsuit.<id>.<key>.
 * The famous beats: another Earth in the sky (the incursion); Sue's "the smartest man in every room"; the Doomsday
 * trailer's caught blow and "Hell answers to me... I am Doom"; the Doombot that wasn't him; God Emperor Doom; Secret
 * Wars' "could I have done better?" - "...Yes."; the mask coming off; and the last scene, the mask in his hands.
 */
public final class DoomCutscenes {
    private DoomCutscenes() {
    }

    public static void registerAll() {
        // Prologue.
        Cutscenes.register(ffArrive());
        // Chapter 1-2: Battleworld and the Barons.
        Cutscenes.register(battleworld());
        Cutscenes.register(genosha());
        Cutscenes.register(sentinels());
        Cutscenes.register(magnetoYields());
        Cutscenes.register(talokan());
        Cutscenes.register(namorYields());
        Cutscenes.register(deadlands());
        Cutscenes.register(summons());
        // Chapter 3: Castle Doom.
        Cutscenes.register(castleGates());
        Cutscenes.register(throne());
        Cutscenes.register(doombotReveal());
        Cutscenes.register(godEmperor());
        Cutscenes.register(couldYou());
        Cutscenes.register(unmasked());
        // The last scene.
        Cutscenes.register(postCredits());
    }

    // ================================================================ prologue

    /**
     * The incursion night: the other Earth fills the sky (the anchor is the rift, +z towards the player, the Earth far
     * out at -z and high); the rift tears open and the Fantastic Four step out of a dead world.
     */
    static Cutscene ffArrive() {
        return Cutscene.of("doom.ff_arrive")
                .cast("reed", MISTER_FANTASTIC, 0, 0, 0, 0).hidden()
                .cast("sue", INVISIBLE_WOMAN, -1.6, 0, -0.6, 10).hidden()
                .cast("johnny", HUMAN_TORCH, 1.6, 0, -0.6, 350).hidden()
                .cast("ben", THE_THING, 0, 0, -1.8, 0).hidden()
                .shot(70).cam(0, 2.2, 13).camTo(0, 3.5, 11).look(0, 30, -50).lookTo(0, 55, -56).narrate("sky")
                .at(0).burst(BURST_EARTH, 0, 75, -56).shake(20).sound(SOUND_THUNDER)
                .shot(50).cam(3, 2, 7).look(0, 2, 0).narrate("rift")
                .at(5).burst(BURST_RIFT, 0, 0, 0).sound(SOUND_BOOM).shake(10)
                .at(20).appear("reed", 0, 0, 0).at(26).appear("sue", -1.6, 0, -0.6).at(32).appear("johnny", 1.6, 0, -0.6)
                .at(38).appear("ben", 0, 0, -1.8)
                .shot(60).cam(0, 1.8, 4.5).look(0, 1.7, 1).say(MISTER_FANTASTIC, "arrive")
                .at(0).move("reed", 0, 0, 2, 30).move("sue", -1.6, 0, 1.2, 30).move("johnny", 1.6, 0, 1.2, 30).move("ben", 0, 0, -0.3, 30)
                .shot(60).cam(-3.5, 1.8, 3.8).look(-1.6, 1.6, 1.2).say(INVISIBLE_WOMAN, "smartest")
                .shot(50).cam(3.5, 1.6, 3.5).look(1.6, 1.6, 1.2).say(HUMAN_TORCH, "arrive")
                .at(10).facePos("johnny", 0, 40, -56)
                .shot(55).cam(0, 2.8, 4).look(0, 1.8, -0.3).say(THE_THING, "arrive")
                .shot(75).cam(0.8, 1.8, 4.2).camTo(0.4, 1.8, 3.6).look(0, 1.7, 2).say(MISTER_FANTASTIC, "doom")
                .at(60).burst(BURST_RIFT, 0, 0, -1)
                .build();
    }

    // ================================================================ chapter 1-2

    /** First time through the gate: over the refuge, the patchwork of dead worlds, the castle on the skyline, Reed. */
    static Cutscene battleworld() {
        return Cutscene.of("doom.battleworld")
                .cast("reed", MISTER_FANTASTIC, 4, 0, -2, 160)
                .shot(90).cam(0, 22, 12).camTo(0, 40, 30).look(0, 8, -60).lookTo(0, 10, -140).narrate("patchwork")
                .shot(70).cam(0, 40, 30).camTo(0, 34, 10).look(0, 12, -140).lookTo(0, 16, -360).narrate("castle")
                .shot(60).cam(1.5, 1.9, -5).look(4, 1.6, -2).say(MISTER_FANTASTIC, "welcome")
                .at(0).move("reed", 2.5, 0, -4, 30)
                .build();
    }

    /** Genosha: Magneto in the air over the X, iron rising out of the ground as Doombots. */
    static Cutscene genosha() {
        return Cutscene.of("doom.genosha")
                .cast("magneto", MAGNETO, 0, 8, -10, 0).hidden()
                .cast("sue", INVISIBLE_WOMAN, -3, 0, 9, 180).bind()
                .cast("bot1", DOOMBOT, -5, 0, -7, 0).hidden()
                .cast("bot2", DOOMBOT, 0, 0, -8, 0).hidden()
                .cast("bot3", DOOMBOT, 5, 0, -7, 0).hidden()
                .shot(60).cam(0, 4, 15).camTo(0, 6, 11).look(0, 6, -6).lookTo(0, 8, -10).narrate("genosha")
                .at(20).appear("magneto", 0, 8, -10).burst(BURST_FLASH, 0, 9, -10).sound(SOUND_POWER)
                .shot(70).cam(0, 8.5, -5.5).look(0, 9.5, -10).say(MAGNETO, "intro")
                .shot(55).cam(-4.5, 2, 12).look(-3, 1.6, 9).say(INVISIBLE_WOMAN, "genosha")
                .shot(50).cam(0, 5, 8).look(0, 3, -7).say(MAGNETO, "rise")
                .at(0).pose("magneto", HANDS_UP).shake(25).sound(SOUND_ROAR)
                .at(10).burst(BURST_CRACK, -5, 0, -7).appear("bot1", -5, 0, -7)
                .at(16).burst(BURST_CRACK, 0, 0, -8).appear("bot2", 0, 0, -8)
                .at(22).burst(BURST_CRACK, 5, 0, -7).appear("bot3", 5, 0, -7)
                .at(40).vanish("magneto")
                .build();
    }

    /** The ground splits and two Sentinels stand up out of it, eyes burning. */
    static Cutscene sentinels() {
        return Cutscene.of("doom.sentinels")
                .cast("s1", SENTINEL, -7, 0, -4, 20).hidden()
                .cast("s2", SENTINEL, 7, 0, -4, 340).hidden()
                .shot(45).cam(0, 3, 10).look(0, 1, -4).narrate("tremor")
                .at(5).shake(40).sound(SOUND_ROAR).burst(BURST_CRACK, -7, 0, -4).burst(BURST_CRACK, 7, 0, -4)
                .shot(60).cam(0, 1.5, 6).camTo(0, 1, 4).look(0, 3, -4).lookTo(0, 7, -4).narrate("rise")
                .at(5).appear("s1", -7, 0, -4).appear("s2", 7, 0, -4).burst(BURST_SMOKE, -7, 0, -4).burst(BURST_SMOKE, 7, 0, -4)
                .shot(55).cam(-5, 5, 3).look(-7, 5.5, -4).say(SENTINEL, "scan")
                .at(10).burst(BURST_FLASH, -7, 6, -3.5).sound(SOUND_CHARGE)
                .build();
    }

    /** Magneto beaten: he lets the world core go - and lets Doom know whose Baron he isn't. */
    static Cutscene magnetoYields() {
        return Cutscene.of("doom.magneto_yields")
                .cast("magneto", MAGNETO, 0, 0, -3, 0).bind().ends(End.REMOVE)
                .cast("sue", INVISIBLE_WOMAN, -3, 0, 3, 200).bind()
                .shot(50).cam(2.5, 1.8, 1).look(0, 1.5, -3).say(MAGNETO, "yields")
                .at(0).pose("magneto", HURT)
                .shot(60).cam(-1.5, 1.8, 0).look(0, 1.6, -3).say(MAGNETO, "core")
                .shot(55).cam(0, 2.5, 4).look(0, 3, -3).say(MAGNETO, "leave")
                .at(25).fly("magneto", 0, 14, -8, 25, 0).at(50).vanish("magneto")
                .build();
    }

    /** Talokan: Namor up on the stepped temple; Johnny flames on. */
    static Cutscene talokan() {
        return Cutscene.of("doom.talokan")
                .cast("namor", NAMOR, 0, 11, -20, 0).hidden()
                .cast("johnny", HUMAN_TORCH, 3, 0, 9, 180).bind()
                .shot(65).cam(0, 5, 14).camTo(0, 7, 8).look(0, 4, -12).lookTo(0, 10, -20).narrate("talokan")
                .at(25).appear("namor", 0, 11, -20).burst(BURST_SMOKE, 0, 11, -20)
                .shot(65).cam(0, 11.8, -16).look(0, 12.4, -20).say(NAMOR, "intro")
                .shot(45).cam(5, 2, 12).look(3, 1.6, 9).say(HUMAN_TORCH, "flame_on")
                .at(10).pose("johnny", TRANSFORM)
                .shot(40).cam(0, 6, 6).look(0, 8, -20).say(NAMOR, "warriors")
                .at(20).vanish("namor")
                .build();
    }

    /** Namor beaten: "Imperius Rex" - the core is yours; Talokan kneels to no one. */
    static Cutscene namorYields() {
        return Cutscene.of("doom.namor_yields")
                .cast("namor", NAMOR, 0, 0, -3, 0).bind().ends(End.REMOVE)
                .cast("johnny", HUMAN_TORCH, 3, 0, 3, 200).bind()
                .shot(50).cam(2.5, 1.8, 1).look(0, 1.5, -3).say(NAMOR, "yields")
                .at(0).pose("namor", HURT)
                .shot(60).cam(-1.5, 1.8, 0).look(0, 1.6, -3).say(NAMOR, "core")
                .shot(45).cam(3, 2, 5).look(3, 1.6, 3).say(HUMAN_TORCH, "namor")
                .at(30).fly("namor", 0, 6, -14, 15, 0).at(45).vanish("namor")
                .build();
    }

    /** The Shield and the breach in it: the dead pour through; Ben cracks his knuckles. */
    static Cutscene deadlands() {
        return Cutscene.of("doom.deadlands")
                .cast("ben", THE_THING, 0, 0, 8, 180).bind()
                .cast("z1", MARVEL_ZOMBIE, -2, 0, -19, 0).hidden()
                .cast("z2", MARVEL_ZOMBIE, 1, 0, -20, 0).hidden()
                .cast("z3", MARVEL_ZOMBIE, 3, 0, -19, 0).hidden()
                .shot(70).cam(0, 10, 6).camTo(0, 12, 2).look(0, 9, -21).narrate("shield")
                .shot(55).cam(0, 2, 2).look(0, 1.5, -16).narrate("breach")
                .at(5).appear("z1", -2, 0, -19).appear("z2", 1, 0, -20).appear("z3", 3, 0, -19).sound(SOUND_ROAR)
                .at(10).move("z1", -3, 0, -12, 40).move("z2", 0, 0, -11, 40).move("z3", 3, 0, -12, 40)
                .shot(50).cam(-2.5, 2, 11).look(0, 1.7, 8).say(THE_THING, "clobber")
                .at(25).pose("ben", PUNCH_R).sound(SOUND_PUNCH)
                .build();
    }

    /** The third core: Doom shows himself - a glowing figure over the field - and calls them to his castle. */
    static Cutscene summons() {
        return Cutscene.of("doom.summons")
                .cast("doom", DOCTOR_DOOM, 0, 3, -6, 0).hidden()
                .shot(45).cam(0, 2, 6).look(0, 4, -6).narrate("sky")
                .at(5).burst(BURST_GREEN_PILLAR, 0, 0, -6).shake(15).sound(SOUND_THUNDER)
                .at(20).appear("doom", 0, 3, -6).glow("doom", true)
                .shot(70).cam(0, 3.2, 1.5).look(0, 4.4, -6).say(DOCTOR_DOOM, "summons")
                .shot(65).cam(0, 4.6, -3).look(0, 4.7, -6).say(DOCTOR_DOOM, "summons_2")
                .shot(30).cam(0, 3, 3).look(0, 4, -6)
                .at(10).vanishInLight("doom")
                .build();
    }

    // ================================================================ chapter 3: Castle Doom

    /** Before the gates of Castle Doom: the Four with you, Ben knocking. */
    static Cutscene castleGates() {
        return Cutscene.of("doom.castle_gates")
                .cast("reed", MISTER_FANTASTIC, -4, 0, 8, 180).bind()
                .cast("sue", INVISIBLE_WOMAN, -1, 0, 8, 180).bind()
                .cast("johnny", HUMAN_TORCH, 2, 0, 8, 180).bind()
                .cast("ben", THE_THING, 5, 0, 8, 180).bind()
                .cast("bot1", DOOMBOT, -6, 0, -8, 0).hidden()
                .cast("bot2", DOOMBOT, 0, 0, -10, 0).hidden()
                .cast("bot3", DOOMBOT, 6, 0, -8, 0).hidden()
                .shot(80).cam(0, 7, 40).camTo(0, 9, 30).look(0, 12, 0).lookTo(0, 10, -16).narrate("castle")
                .shot(55).cam(-6, 2, 12).look(-4, 1.6, 8).say(MISTER_FANTASTIC, "plan")
                .shot(55).cam(7, 2, 11).look(5, 1.8, 8).say(THE_THING, "knock")
                .at(10).move("ben", 3, 0, 2, 20).at(32).pose("ben", PUNCH_R).burst(BURST_EXPLOSION, 3, 1, 1).shake(10).sound(SOUND_BOOM)
                .shot(40).cam(4, 1.8, 11).look(2, 1.6, 8).say(HUMAN_TORCH, "flame_on")
                .at(5).pose("johnny", TRANSFORM)
                .shot(50).cam(0, 3, 4).look(0, 1.6, -8).narrate("bots")
                .at(5).burst(BURST_FLASH, -6, 1, -8).appear("bot1", -6, 0, -8)
                .at(10).burst(BURST_FLASH, 0, 1, -10).appear("bot2", 0, 0, -10)
                .at(15).burst(BURST_FLASH, 6, 1, -8).appear("bot3", 6, 0, -8)
                .build();
    }

    /**
     * Doom comes out of his keep. Ben goes for him and Doom catches the blow (the Doomsday trailer's Stormbreaker):
     * "Hell answers to me... I am Doom."
     */
    static Cutscene throne() {
        return Cutscene.of("doom.throne")
                .cast("doom", DOCTOR_DOOM, 0, 0, -11, 0).hidden().ends(End.FIGHT)
                .cast("reed", MISTER_FANTASTIC, -3, 0, 5, 180).bind()
                .cast("sue", INVISIBLE_WOMAN, -1, 0, 6, 180).bind()
                .cast("johnny", HUMAN_TORCH, 2, 0, 6, 180).bind()
                .cast("ben", THE_THING, 4, 0, 4, 180).bind()
                .shot(70).cam(0, 2.5, 3).camTo(0, 2, 0).look(0, 2, -11).lookTo(0, 1.8, -7).narrate("door")
                .at(10).appear("doom", 0, 0, -11).at(15).move("doom", 0, 0, -6, 50)
                .shot(55).cam(0, 2, -2.5).look(0, 1.8, -6).say(DOCTOR_DOOM, "welcome")
                .shot(55).cam(-4.5, 2, 7.5).look(-3, 1.6, 5).say(MISTER_FANTASTIC, "victor")
                .at(0).face("reed", "doom")
                .shot(30).linear().cam(3, 1.8, -1).look(1, 1.6, -5)
                .at(0).move("ben", 0.4, 0, -4.6, 18).at(18).pose("ben", PUNCH_R).pose("doom", GUARD).sound(SOUND_PUNCH).shake(8)
                .shot(60).cam(2, 1.8, -3.2).look(0, 1.8, -5.5).say(DOCTOR_DOOM, "hell")
                .at(30).beam("doom", "ben", BEAM_DOOM, 10).at(40).knock("ben", 3, 0, 4, 14)
                .shot(35).cam(0, 3, 5).look(0, 1.8, -6)
                .at(0).pose("doom", TRANSFORM).burst(BURST_GREEN_PILLAR, 0, 0, -6).sound(SOUND_POWER)
                .build();
    }

    /** The Torch's flame hits home and "Doom" bursts apart in sparks - a Doombot. The real one is overhead. */
    static Cutscene doombotReveal() {
        return Cutscene.of("doom.doombot_reveal")
                .cast("doom", DOCTOR_DOOM, 0, 0, -4, 0).bind()
                .cast("johnny", HUMAN_TORCH, 4, 0, 2, 220).bind()
                .shot(35).cam(5, 2, 0).look(1, 1.6, -3).say(HUMAN_TORCH, "take_this")
                .at(10).beam("johnny", "doom", BEAM_FIRE, 12)
                .shot(45).cam(0, 2, 0).look(0, 1.6, -4).narrate("sparks")
                .at(0).burst(BURST_EXPLOSION, 0, 1.4, -4).vanishInLight("doom").shake(10).sound(SOUND_BOOM)
                .shot(50).cam(3, 1.8, 4.5).look(4, 1.6, 2).say(HUMAN_TORCH, "got_him")
                .shot(60).cam(0, 2, 2).look(0, 2.5, -4).lookTo(0, 9, -9).say(DOCTOR_DOOM, "doombot")
                .at(10).appear("doom", 0, 9, -9).glow("doom", true)
                .shot(35).cam(0, 3, 2).look(0, 5, -6)
                .at(0).fly("doom", 0, 0, -4, 22, 1.0).at(30).glow("doom", false)
                .build();
    }

    /** At half health: the cores' power was never gone - it comes to him. God Emperor Doom. */
    static Cutscene godEmperor() {
        return Cutscene.of("doom.god_emperor")
                .cast("doom", DOCTOR_DOOM, 0, 0, -4, 0).bind()
                .cast("reed", MISTER_FANTASTIC, -3, 0, 4, 180).bind()
                .cast("sue", INVISIBLE_WOMAN, 3, 0, 4, 180).bind()
                .shot(55).cam(0, 2, 1.5).look(0, 1.7, -4).say(DOCTOR_DOOM, "power")
                .at(0).pose("doom", HANDS_UP)
                .shot(75).cam(0, 1, 3).camTo(0, 3, 6).look(0, 2, -4).lookTo(0, 4, -4).narrate("god")
                .at(10).transform("doom", DOOM_GOD_EMPEROR).burst(BURST_GREEN_PILLAR, 0, 0, -4).shake(30).sound(SOUND_POWER)
                .shot(55).cam(1.5, 1.8, 6.5).look(3, 1.6, 4).say(INVISIBLE_WOMAN, "not_a_man")
                .shot(45).cam(0, 2.5, 0).look(0, 2, -4).say(DOCTOR_DOOM, "kneel")
                .at(5).pose("doom", HANDS_UP).burst(BURST_LIGHTNING, -4, 0, 2).at(12).burst(BURST_LIGHTNING, 4, 0, 2).sound(SOUND_THUNDER)
                .build();
    }

    /**
     * Secret Wars' end: Reed asks the one question - had I the power, could I have done better? - and after a long
     * silence Doom says yes. The doubt is enough: the power leaves him.
     */
    static Cutscene couldYou() {
        return Cutscene.of("doom.could_you")
                .cast("doom", DOOM_GOD_EMPEROR, 0, 0, -3, 0).bind()
                .cast("reed", MISTER_FANTASTIC, 0, 0, 3, 180).bind()
                .shot(50).cam(3.5, 1.8, 0).look(0, 1.6, -0.5).say(MISTER_FANTASTIC, "stop")
                .at(0).move("reed", 0, 0, 0, 30)
                .shot(70).cam(0, 1.8, 1.6).look(0, 1.9, -3).say(DOCTOR_DOOM, "saved")
                .shot(70).cam(0, 1.7, -1.8).look(0, 1.7, 0).say(MISTER_FANTASTIC, "question")
                .shot(90).linear().cam(0, 1.9, -1.4).camTo(0, 1.9, -1.9).look(0, 1.9, -3).narrate("silence")
                .at(60).line("doom", "yes")
                .shot(55).cam(0, 4, 4).look(0, 1.2, -3).narrate("fade")
                .at(5).transform("doom", DOCTOR_DOOM).pose("doom", HURT).burst(BURST_FLASH, 0, 2, -3)
                .build();
    }

    /** The end of the fight: the mask comes off (we see Reed's face, not his); Johnny almost says it; Doom is gone. */
    static Cutscene unmasked() {
        return Cutscene.of("doom.unmasked")
                .cast("doom", DOCTOR_DOOM, 0, 0, -3, 0).bind().ends(End.REMOVE)
                .cast("reed", MISTER_FANTASTIC, 0, 0, 0.5, 180).bind()
                .cast("sue", INVISIBLE_WOMAN, -2.2, 0, 1.5, 200).bind()
                .cast("johnny", HUMAN_TORCH, 2.2, 0, 1.5, 160).bind()
                .cast("ben", THE_THING, 0, 0, 3, 180).bind()
                .shot(50).cam(3.5, 2, -1).look(0, 1, -3).narrate("falls")
                .at(0).pose("doom", HURT)
                .shot(75).cam(0, 1.9, -5.5).look(0, 1.7, 0).say(DOCTOR_DOOM, "mask")
                .at(45).burst(BURST_FLASH, 0, 1.9, -2.9).sound(SOUND_WHOOSH)
                .shot(60).cam(-1.4, 1.75, -1.6).look(0, 1.65, 0.5).say(MISTER_FANTASTIC, "face")
                .shot(50).cam(3.2, 1.7, -0.5).look(2.2, 1.6, 1.5).say(HUMAN_TORCH, "wait")
                .shot(40).cam(0.5, 2.4, 5.2).look(0, 1.8, 3).say(THE_THING, "dont")
                .shot(80).cam(0, 1.9, -5.5).look(0, 1.6, -3).say(DOCTOR_DOOM, "every_world")
                .shot(45).cam(0, 3, 3).look(0, 2, -3).narrate("gone")
                .at(5).vanishInLight("doom").burst(BURST_GREEN_PILLAR, 0, 0, -3).sound(SOUND_THUNDER)
                .shot(65).cam(-1, 2, 3.6).look(0, 1.6, 0.5).say(MISTER_FANTASTIC, "thanks")
                .at(0).facePos("reed", 0, 0, 5)
                .build();
    }

    // ================================================================ the last scene

    /**
     * The night after, back home (the anchor a few steps ahead of the player, +z towards them): a cloaked figure with
     * his back turned, the mask in his hands; he turns, a few words, gone. "Doctor Doom will return."
     */
    static Cutscene postCredits() {
        return Cutscene.of("doom.post_credits")
                .cast("doom", DOCTOR_DOOM, 0, 0, 0, 180).hidden()
                .shot(55).cam(0, 1.7, 5).look(0, 1.5, 0).narrate("night")
                .at(15).appear("doom", 0, 0, 0)
                .shot(60).cam(1.6, 1.6, -2.4).look(0, 1.2, 0).narrate("mask")
                .shot(60).cam(0, 1.7, 3).look(0, 1.75, 0).say(DOCTOR_DOOM, "post")
                .at(5).facePos("doom", 0, 0, 5)
                .shot(40).cam(0, 1.7, 4).look(0, 1.6, 0).narrate("end")
                .at(10).vanishInLight("doom").sound(SOUND_THUNDER)
                .build();
    }
}
