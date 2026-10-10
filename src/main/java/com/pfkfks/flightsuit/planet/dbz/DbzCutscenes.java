package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscene.End;
import com.pfkfks.flightsuit.cutscene.Cutscenes;

import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_CANDY;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_DEATH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_KAME;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_SBC;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BEAM_WHITE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_BIG_EXPLOSION;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_CRACK;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_EXPLOSION;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_FLASH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_GOLD_PILLAR;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_LAVA;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_LIGHTNING;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_MOON;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_SMOKE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.BURST_SPIRIT_BOMB;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_BOOM;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_CHARGE;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_CROWD;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_POWER;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_PUNCH;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_ROAR;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_THUNDER;
import static com.pfkfks.flightsuit.cutscene.Cutscene.SOUND_WHOOSH;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.CARRY;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.CHARGE;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.DOWN;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.FIRE;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.GUARD;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.HANDS_UP;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.HEADBUTT;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.HURT;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.IDLE;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.KICK;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.POSE_GINYU;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.PUNCH_R;
import static com.pfkfks.flightsuit.planet.dbz.DbzAction.TRANSFORM;
import static com.pfkfks.flightsuit.planet.dbz.DbzCharacter.*;

/**
 * The Dragon Ball story's cutscenes (M17), as scripts for the cutscene engine (cutscene.Cutscene), in story order. Ids
 * are "dbz.<name>"; the anchor is the scene's arena centre at standing height (yaw 0: x east, z south - foes come from
 * +z, our side stands at -z); a y of 0 is "on the ground there" (the engine finds it). Cast ids are DbzSaga.castId's
 * ("goku" for every Goku, "gohan", "vegeta" for the Great Ape too, "cell" for every Cell, "super_buu"...): bind() picks
 * up the fight's own fighter. Lines are dbz.flightsuit.<voice>.<key>, narration cutscene.flightsuit.<id>.<key>.
 */
public final class DbzCutscenes {
    private DbzCutscenes() {
    }

    public static void registerAll() {
        // Chapter 1: the Saiyans.
        Cutscenes.register(raditzArrives());
        Cutscenes.register(gohanHeadbutt());
        Cutscenes.register(specialBeamCannon());
        Cutscenes.register(saiyansLand());
        Cutscenes.register(yamchaDies());
        Cutscenes.register(piccoloDies());
        Cutscenes.register(kaioken());
        Cutscenes.register(oozaru());
        Cutscenes.register(tailCut());
        Cutscenes.register(vegetaSpared());
        // Chapter 2: Namek.
        Cutscenes.register(ginyuPose());
        Cutscenes.register(krillinDiesSsj());
        Cutscenes.register(namekDying());
        Cutscenes.register(namekEscape());
        // Chapter 3: the androids and Cell.
        Cutscenes.register(trunksArrives());
        Cutscenes.register(cellAbsorbs17());
        Cutscenes.register(cellAbsorbs18());
        Cutscenes.register(gohanAwakens());
        Cutscenes.register(cellSelfDestruct());
        Cutscenes.register(fatherSonKamehameha());
        // Chapter 4: Majin Buu.
        Cutscenes.register(buuBefriends());
        Cutscenes.register(superBuu());
        Cutscenes.register(buuAbsorbs());
        Cutscenes.register(kidBuu());
        Cutscenes.register(spiritBombEnd());
    }

    // ================================================================ chapter 1

    /** Raditz's pod comes down in the crater; Goku and Piccolo, enemies until now, stand together. */
    static Cutscene raditzArrives() {
        return Cutscene.of("dbz.raditz_arrives")
                .cast("raditz", RADITZ, 0, 0, 4, 180).hidden().ends(End.FIGHT)
                .cast("goku", GOKU, -3, 0, -6, 0).ends(End.FIGHT)
                .cast("piccolo", PICCOLO, 3, 0, -6, 0).ends(End.FIGHT)
                .shot(45).cam(0, 12, -14).camTo(0, 7, -10).look(0, 14, 8).lookTo(0, 1, 4).narrate("pod")
                .at(30).burst(BURST_BIG_EXPLOSION, 0, 0, 4).shake(15).sound(SOUND_BOOM).appear("raditz", 0, 0, 4)
                .shot(55).cam(0, 2, 0.5).camTo(0, 2.2, 1.5).look(0, 1.7, 4).say(RADITZ, "arrive")
                .at(10).move("raditz", 0, 0, 2.5, 30)
                .shot(45).cam(-5, 2, -3).look(-2.5, 1.6, -6).say(GOKU, "brother")
                .at(5).face("goku", "raditz").face("piccolo", "raditz")
                .shot(45).cam(5, 2, -3).look(2.5, 1.6, -6).say(PICCOLO, "team_up")
                .shot(45).cam(0, 4, -12).camTo(0, 5, -14).look(0, 1.5, 0).narrate("fight")
                .at(10).pose("raditz", CHARGE).pose("goku", GUARD).pose("piccolo", GUARD)
                .build();
    }

    /** Raditz has Goku down - and Gohan bursts out of the pod, head first. */
    static Cutscene gohanHeadbutt() {
        return Cutscene.of("dbz.gohan_headbutt")
                .cast("raditz", RADITZ, 0, 0, 2, 180).bind()
                .cast("goku", GOKU, 0, 0, -0.5, 0).bind()
                .cast("piccolo", PICCOLO, -5, 0, -3, 30).bind()
                .cast("gohan", GOHAN_KID, 3, 0, 0, 270).hidden()
                .shot(45).cam(-3, 2.5, -4).look(0, 0.8, 1).say(RADITZ, "stomp")
                .at(0).knock("goku", 0, 0, -0.5, 8).at(8).fall("goku").at(10).move("raditz", 0, 0, 0.8, 20).at(32).pose("raditz", KICK)
                .shot(35).cam(5, 2, 1).look(3, 1, 0).say(GOHAN_KID, "daddy")
                .at(5).burst(BURST_SMOKE, 3, 1, 0).appear("gohan", 3, 0, 0).sound(SOUND_BOOM).shake(6)
                .shot(22).linear().cam(3, 1.5, -2).camTo(1.5, 1.5, -1).look(1.5, 1, 0.8)
                .at(0).pose("gohan", HEADBUTT).fly("gohan", 0.4, 0.8, 1.0, 8, 0).at(8).knock("raditz", -1, 0, 6, 14)
                .burst(BURST_FLASH, 0.4, 1.4, 1).sound(SOUND_PUNCH).shake(8)
                .shot(45).cam(0, 3, -3).look(-1, 1, 5).say(RADITZ, "headbutt")
                .at(0).pose("raditz", HURT).at(10).fall("gohan").at(35).vanish("gohan")
                .shot(40).cam(-4, 2, -4).look(0, 1, 0).say(GOKU, "get_up")
                .at(0).pose("goku", IDLE).move("goku", 0, 0, -1, 12)
                .shot(40).cam(-6, 2, -1).look(-5, 1.6, -3).say(PICCOLO, "impressed")
                .at(10).pose("raditz", IDLE).move("raditz", 0, 0, 4, 20)
                .build();
    }

    /** Goku holds Raditz from behind; Piccolo's Special Beam Cannon goes through them both. */
    static Cutscene specialBeamCannon() {
        return Cutscene.of("dbz.special_beam_cannon")
                .cast("raditz", RADITZ, 0, 0, 1, 180).bind().ends(End.REMOVE)
                .cast("goku", GOKU, 2, 0, 3, 270).bind().ends(End.REMOVE)
                .cast("piccolo", PICCOLO, 0, 0, -6, 0).bind().ends(End.REMOVE)
                .shot(45).cam(4, 2, 0).look(0, 1.5, 1.3).say(GOKU, "hold")
                .at(0).move("goku", 0, 0, 1.7, 10).at(11).face("goku", "piccolo").pose("goku", CARRY).pose("raditz", HURT)
                .shot(60).cam(-2, 1.8, -8.5).look(0, 1.8, -6).say(PICCOLO, "sbc_charge")
                .at(0).pose("piccolo", CHARGE).sound(SOUND_CHARGE).at(30).burst(BURST_FLASH, 0, 2.2, -5.6)
                .shot(45).cam(-5, 3, -2).look(0, 1.5, -1).say(PICCOLO, "sbc_fire")
                .at(0).beam("piccolo", "raditz", BEAM_SBC, 25).at(25).fall("raditz").fall("goku").shake(10)
                .shot(65).cam(1.5, 1, -0.5).look(0, 0.3, 1.5).say(RADITZ, "dying")
                .shot(50).cam(-3, 1.5, 1).look(0, 0.3, 1.7).say(GOKU, "dying")
                .at(38).vanishInLight("goku").vanishInLight("raditz")
                .shot(35).cam(-1, 2, -9).look(0, 1.6, -6).say(PICCOLO, "alone")
                .at(15).fly("piccolo", 0, 20, -14, 18, 0)
                .build();
    }

    /** A day later: two pods - Nappa and Vegeta - and the Saibamen sprouting from the ground. */
    static Cutscene saiyansLand() {
        return Cutscene.of("dbz.saiyans_land")
                .cast("nappa", NAPPA, -3, 0, 6, 180).hidden()
                .cast("vegeta", VEGETA, 3, 0, 6, 180).hidden()
                .cast("piccolo", PICCOLO, -4, 0, -6, 0).bind()
                .cast("krillin", KRILLIN, -2, 0, -6, 0).bind()
                .cast("yamcha", YAMCHA, 0, 0, -6, 0).bind()
                .cast("tien", TIEN, 2, 0, -6, 0).bind()
                .cast("gohan", GOHAN_KID, 4, 0, -6, 0).bind()
                .shot(45).cam(0, 8, -12).camTo(0, 5, -9).look(0, 12, 6).lookTo(0, 1, 5)
                .at(20).burst(BURST_BIG_EXPLOSION, -3, 0, 6).burst(BURST_BIG_EXPLOSION, 3, 0, 6).shake(15).sound(SOUND_BOOM)
                .at(25).appear("nappa", -3, 0, 6).appear("vegeta", 3, 0, 6)
                .shot(50).cam(-2, 2.2, 2).look(-3, 2, 6).say(NAPPA, "landed")
                .shot(50).cam(2, 2, 2).look(3, 1.7, 6).say(VEGETA, "landed")
                .shot(45).cam(-3, 3, -10).look(0, 1.3, -6).say(PICCOLO, "saiyans")
                .shot(45).cam(0, 4, 0).look(0, 0.5, 4).say(NAPPA, "saibamen")
                .at(10).pose("nappa", FIRE).at(20).burst(BURST_SMOKE, 0, 0, 3).sound(SOUND_BOOM)
                .at(40).vanish("nappa").vanish("vegeta")
                .build();
    }

    /** The last Saibaman lies still; Yamcha goes to look - and it leaps on him and blows. */
    static Cutscene yamchaDies() {
        return Cutscene.of("dbz.yamcha_dies")
                .cast("saibaman", SAIBAMAN, 0, 0, 4, 180).bind().ends(End.REMOVE)
                .cast("yamcha", YAMCHA, -2, 0, -2, 0).bind().ends(End.STAY)
                .cast("krillin", KRILLIN, -4, 0, -4, 0).bind()
                .shot(45).cam(-5, 2, 0).look(-1, 1, 1.5).say(YAMCHA, "last_one")
                .at(0).pose("saibaman", DOWN).move("yamcha", -0.5, 0, 2.6, 30)
                .shot(22).cam(0, 2, 1).look(-0.5, 1, 3).narrate("grab")
                .at(0).pose("saibaman", IDLE).fly("saibaman", -0.5, 0.8, 3.0, 8, 1).at(8).pose("yamcha", HURT)
                .shot(45).cam(-3, 4, -3).look(-0.5, 1, 3)
                .at(5).burst(BURST_BIG_EXPLOSION, -0.5, 1, 3).shake(12).vanish("saibaman").knock("yamcha", -0.8, 0, 2.0, 10).at(15).fall("yamcha")
                .shot(45).cam(-1, 1.2, 0.5).look(-0.8, 0.2, 2.0).narrate("yamcha_down")
                .shot(50).cam(-5, 1.7, -2.5).look(-4, 1.6, -4).say(KRILLIN, "yamcha")
                .build();
    }

    /** Nappa's blast goes for Gohan; Piccolo throws himself in front of it. */
    static Cutscene piccoloDies() {
        return Cutscene.of("dbz.piccolo_dies")
                .cast("nappa", NAPPA, 0, 0, 6, 180).bind()
                .cast("gohan", GOHAN_KID, 0, 0, -2, 0).bind()
                .cast("piccolo", PICCOLO, -3, 0, -2, 0).bind().ends(End.STAY)
                .shot(45).cam(3, 2.2, 3).look(0, 2, 6).say(NAPPA, "beam")
                .at(0).pose("nappa", CHARGE).sound(SOUND_CHARGE)
                .shot(35).cam(-2, 1.4, -5).look(0, 1, -2).say(GOHAN_KID, "frozen")
                .shot(45).cam(-4, 2, 0).look(-1, 1, -1)
                .at(0).beamAt("nappa", 0, 1, -1.2, BEAM_WHITE, 30).at(5).fly("piccolo", 0, 0, -1.2, 6, 0.3).at(11).pose("piccolo", CARRY)
                .at(30).burst(BURST_EXPLOSION, 0, 1, -1.2).shake(10)
                .shot(65).cam(1.5, 1.2, -3).look(0, 0.5, -1.5).say(PICCOLO, "last_words")
                .at(0).fall("piccolo")
                .shot(45).cam(0, 1.5, -4.5).look(0, 1.4, -2).say(GOHAN_KID, "rage")
                .at(10).pose("gohan", TRANSFORM).burst(BURST_FLASH, 0, 1, -2).shake(10).sound(SOUND_POWER)
                .build();
    }

    /** Goku is back from the afterlife's training: the Kaioken, and Vegeta thrown across the crater. */
    static Cutscene kaioken() {
        return Cutscene.of("dbz.kaioken")
                .cast("vegeta", VEGETA, 0, 0, 4, 180).bind()
                .cast("goku", GOKU, 0, 18, -4, 0).hidden().ends(End.FIGHT)
                .cast("krillin", KRILLIN, -4, 0, -4, 0).bind()
                .cast("gohan", GOHAN_KID, -2, 0, -5, 0).bind()
                .shot(45).cam(0, 3, -9).look(0, 12, -4).lookTo(0, 3, -4).narrate("cloud")
                .at(5).appear("goku", 0, 16, -4).fly("goku", 0, 0, -3, 30, 0)
                .shot(45).cam(-2, 1.8, -6).look(0, 1.6, -3).say(GOKU, "sorry_late")
                .shot(45).cam(2, 2, 1).look(0, 1.6, 4).say(VEGETA, "kakarot")
                .shot(45).cam(-3, 2, -1).look(0, 1.5, -3).say(GOKU, "kaioken")
                .at(5).pose("goku", TRANSFORM).burst(BURST_FLASH, 0, 1, -3).sound(SOUND_POWER).shake(8)
                .shot(30).linear().cam(-4, 2, 0).camTo(-4, 2, 3).look(0, 1.5, -3).lookTo(0, 1.5, 3.5)
                .at(0).fly("goku", 0, 0, 3, 6, 0.2).at(6).pose("goku", PUNCH_R).knock("vegeta", 0, 0, 12, 16)
                .burst(BURST_FLASH, 0, 1.5, 3.5).sound(SOUND_PUNCH).shake(10)
                .shot(40).cam(0, 3, 1).look(0, 1, 12).say(VEGETA, "impossible")
                .at(18).pose("vegeta", IDLE).move("vegeta", 0, 0, 8, 20)
                .build();
    }

    /** Vegeta makes a false moon - and becomes a Great Ape. */
    static Cutscene oozaru() {
        return Cutscene.of("dbz.oozaru")
                .cast("vegeta", VEGETA, 0, 0, 8, 180).bind()
                .cast("goku", GOKU, 0, 0, -3, 0).bind()
                .cast("gohan", GOHAN_KID, -3, 0, -4, 0).bind()
                .shot(55).cam(2, 2, 5).look(0, 1.6, 8).say(VEGETA, "moon")
                .at(10).pose("vegeta", HANDS_UP).at(35).burst(BURST_MOON, 0, 22, 8).sound(SOUND_POWER)
                .shot(45).cam(0, 1, 4).look(0, 18, 8).say(VEGETA, "look")
                .shot(65).cam(0, 4, -6).camTo(0, 7, -11).look(0, 3, 8).lookTo(0, 9, 8)
                .at(0).pose("vegeta", TRANSFORM).at(20).transform("vegeta", OOZARU_VEGETA).sound(SOUND_ROAR).shake(20)
                .shot(45).cam(-3, 2, -5).look(0, 1.6, -3).say(GOKU, "oozaru")
                .shot(35).cam(0, 2, -7).look(0, 8, 8).say(VEGETA, "roar")
                .at(0).sound(SOUND_ROAR).pose("vegeta", TRANSFORM)
                .build();
    }

    /** Yajirobe creeps up behind the Great Ape and cuts off its tail. */
    static Cutscene tailCut() {
        return Cutscene.of("dbz.tail_cut")
                .cast("vegeta", OOZARU_VEGETA, 0, 0, 8, 180).bind()
                .cast("yajirobe", YAJIROBE, 2, 0, 14, 180).hidden()
                .cast("goku", GOKU, 0, 0, -3, 0).bind()
                .shot(45).cam(5, 3, 2).look(0, 6, 8).say(YAJIROBE, "sneak")
                .at(0).appear("yajirobe", 2, 0, 14).move("yajirobe", 0.5, 0, 11.5, 30)
                .shot(24).cam(3, 2, 13).look(0, 2, 10.5)
                .at(5).fly("yajirobe", 0, 2, 10.5, 6, 0.5).at(11).pose("yajirobe", PUNCH_R).burst(BURST_FLASH, 0, 2.5, 10.5).sound(SOUND_PUNCH)
                .shot(65).cam(0, 5, -6).look(0, 6, 8).say(VEGETA, "tail")
                .at(0).pose("vegeta", HURT).at(20).transform("vegeta", VEGETA).shake(10).at(30).fall("vegeta")
                .shot(45).cam(2, 1.5, 10).look(0, 1, 11.5).say(YAJIROBE, "done")
                .at(5).move("yajirobe", 0.5, 0, 11.5, 2).at(35).vanish("yajirobe")
                .shot(45).cam(-2, 2, 5).look(0, 1.4, 8).say(VEGETA, "no_tail")
                .at(10).pose("vegeta", IDLE)
                .build();
    }

    /** Vegeta crawls for his pod; Krillin raises the sword; Goku asks him to let Vegeta go. */
    static Cutscene vegetaSpared() {
        return Cutscene.of("dbz.vegeta_spared")
                .cast("vegeta", VEGETA, 0, 0, 6, 180).bind().ends(End.REMOVE)
                .cast("goku", GOKU, 0, 0, -2, 0).bind().ends(End.REMOVE)
                .cast("krillin", KRILLIN, -2, 0, 2, 0).bind()
                .shot(55).cam(2, 1, 4).look(0, 0.3, 6).say(VEGETA, "crawl")
                .at(0).move("vegeta", 0, 0, 8, 50).at(1).pose("vegeta", DOWN)
                .shot(45).cam(-3, 2, 1).look(-1, 1.5, 3).say(KRILLIN, "finish_him")
                .at(0).move("krillin", -0.5, 0, 6, 20).at(21).pose("krillin", HANDS_UP)
                .shot(55).cam(-2, 1.8, -4).look(0, 1.6, -2).say(GOKU, "let_him_go")
                .shot(45).cam(2, 1.5, 4).look(-0.5, 1.6, 6).say(KRILLIN, "fine")
                .at(10).pose("krillin", IDLE)
                .shot(65).cam(0, 6, -4).look(0, 4, 8).lookTo(0, 20, 9).say(VEGETA, "next_time")
                .at(0).pose("vegeta", IDLE).at(10).fly("vegeta", 0, 24, 10, 40, 0).at(52).vanishInLight("vegeta")
                .build();
    }

    // ================================================================ chapter 2

    /** The Ginyu Force lands and strikes its poses, one by one, then all together - and Goku's ship comes down. */
    static Cutscene ginyuPose() {
        return Cutscene.of("dbz.ginyu_pose")
                .cast("ginyu", GINYU, 0, 14, 6, 180)
                .cast("recoome", RECOOME, -3, 15, 6, 180)
                .cast("burter", BURTER, 3, 15, 6, 180)
                .cast("jeice", JEICE, -5.5, 16, 5, 180)
                .cast("guldo", GULDO, 5.5, 16, 5, 180)
                .cast("goku", GOKU, 0, 22, -6, 0).hidden().ends(End.FIGHT)
                .shot(30).cam(0, 8, -8).camTo(0, 4, -4).look(0, 8, 6).lookTo(0, 1.5, 6)
                .at(0).fly("ginyu", 0, 0, 6, 22, 0).fly("recoome", -3, 0, 6, 22, 0).fly("burter", 3, 0, 6, 22, 0)
                .fly("jeice", -5.5, 0, 5, 24, 0).fly("guldo", 5.5, 0, 5, 24, 0)
                .at(24).burst(BURST_SMOKE, 0, 0, 6).sound(SOUND_BOOM).pose("ginyu", IDLE)
                .shot(24).cam(-6, 2, 1).look(-5.5, 1.5, 5).say(JEICE, "pose")
                .at(4).pose("jeice", POSE_GINYU)
                .shot(24).cam(6, 2, 1).look(5.5, 1, 5).say(GULDO, "pose")
                .at(4).pose("guldo", POSE_GINYU)
                .shot(24).cam(3, 2.5, 2).look(3, 1.8, 6).say(BURTER, "pose")
                .at(4).pose("burter", POSE_GINYU)
                .shot(24).cam(-3, 2.5, 2).look(-3, 2, 6).say(RECOOME, "pose")
                .at(4).pose("recoome", POSE_GINYU)
                .shot(30).cam(0, 2, 2.5).camTo(0, 2.5, 1.5).look(0, 2, 6).say(GINYU, "pose")
                .at(4).pose("ginyu", POSE_GINYU)
                .shot(40).cam(0, 3, -6).camTo(0, 3.5, -9).look(0, 1.8, 6).narrate("all")
                .burst(BURST_FLASH, 0, 2, 6).shake(10).sound(SOUND_POWER)
                .shot(50).cam(-3, 2, -11).look(0, 12, -6).lookTo(0, 1.6, -6).say(GOKU, "namek")
                .at(0).appear("goku", 0, 20, -6).fly("goku", 0, 0, -6, 30, 0).at(30).burst(BURST_SMOKE, 0, 0, -6).sound(SOUND_BOOM)
                .build();
    }

    /** Frieza blows Krillin up before Goku's eyes - and Goku's anger turns him Super Saiyan. The longest scene. */
    static Cutscene krillinDiesSsj() {
        return Cutscene.of("dbz.krillin_dies_ssj")
                .cast("frieza", FRIEZA_FINAL, 0, 0, 6, 180).bind()
                .cast("krillin", KRILLIN, -3, 0, -2, 0).bind().ends(End.REMOVE)
                .cast("goku", GOKU, 0, 0, -3, 0).bind()
                .cast("gohan", GOHAN_KID, -5, 0, -4, 0).bind()
                .cast("piccolo", PICCOLO, 4, 0, -4, 0).bind()
                .shot(45).cam(2, 2, 3).look(0, 1.6, 6).say(FRIEZA, "krillin_1")
                .at(20).pose("frieza", FIRE)
                .shot(45).cam(-5, 2, -4).look(-3, 1.5, -2).lookTo(-3, 8, -2).say(KRILLIN, "goku_help")
                .at(0).pose("krillin", HURT).fly("krillin", -3, 8, -2, 30, 0)
                .shot(35).cam(-3, 4, -9).look(-3, 8, -2)
                .at(12).burst(BURST_BIG_EXPLOSION, -3, 8, -2).vanishInLight("krillin").shake(15).sound(SOUND_BOOM)
                .shot(55).cam(0, 1.8, -5.2).look(0, 1.6, -3).say(GOKU, "krillin")
                .at(0).pose("goku", HURT)
                .shot(65).cam(1.2, 1.6, -1.6).camTo(0.8, 1.7, -2.2).look(0, 1.7, -3).say(GOKU, "ssj_rage")
                .at(0).pose("goku", TRANSFORM).at(20).burst(BURST_LIGHTNING, 2, 0, -3).at(30).shake(30).sound(SOUND_THUNDER)
                .at(45).burst(BURST_LIGHTNING, -2, 0, -4)
                .shot(75).cam(0, 3, -9).camTo(0, 5, -12).look(0, 2, -3).narrate("ssj")
                .at(5).transform("goku", GOKU_SSJ).burst(BURST_GOLD_PILLAR, 0, 0, -3).burst(BURST_CRACK, 0, 0, -3).shake(20).sound(SOUND_POWER)
                .at(40).burst(BURST_GOLD_PILLAR, 0, 0, -3)
                .shot(55).cam(0, 1.8, -1).look(0, 1.8, -3).say(GOKU, "ssj_awake")
                .at(0).pose("goku", IDLE).face("goku", "frieza")
                .shot(45).cam(2, 2, 3).look(0, 1.6, 6).say(FRIEZA, "ssj_shock")
                .at(0).pose("frieza", GUARD)
                .shot(35).cam(0, 4, -7).look(0, 1.6, 1).say(GOKU, "gohan_go")
                .build();
    }

    /** Frieza strikes the planet's core; Namek starts to come apart. */
    static Cutscene namekDying() {
        return Cutscene.of("dbz.namek_dying")
                .cast("frieza", FRIEZA_FINAL, 0, 0, 6, 180).bind()
                .cast("goku", GOKU_SSJ, 0, 0, -3, 0).bind()
                .shot(45).cam(3, 2, 3).look(0, 1.6, 6).say(FRIEZA, "core")
                .at(10).pose("frieza", CHARGE)
                .shot(45).cam(0, 6, 0).look(0, 0, 3)
                .at(0).beamAt("frieza", 0, -2, 3, BEAM_DEATH, 20).at(20).burst(BURST_LAVA, 0, 0, 3).burst(BURST_CRACK, 0, 0, 3).shake(30).sound(SOUND_BOOM)
                .shot(55).cam(-4, 3, -6).look(0, 1, 0).narrate("minutes")
                .at(0).burst(BURST_LIGHTNING, 6, 0, 6).at(20).burst(BURST_LIGHTNING, -6, 0, 8).sound(SOUND_THUNDER)
                .shot(45).cam(-1.5, 1.8, -5).look(0, 1.6, -3).say(GOKU, "finish_it")
                .at(0).pose("frieza", IDLE)
                .build();
    }

    /** The last blow, Frieza down - and Goku runs for a Ginyu pod as Namek blows. */
    static Cutscene namekEscape() {
        return Cutscene.of("dbz.namek_escape")
                .cast("frieza", FRIEZA_FINAL, 0, 0, 4, 180).bind().ends(End.REMOVE)
                .cast("goku", GOKU_SSJ, 0, 0, -1, 0).bind().ends(End.REMOVE)
                .shot(30).linear().cam(-3, 2, -3).camTo(-3, 2, 2).look(0, 1.5, -1).lookTo(0, 1.5, 4)
                .at(0).fly("goku", 0, 0, 3, 6, 0.2).at(6).pose("goku", PUNCH_R).knock("frieza", 0, 0, 14, 16)
                .burst(BURST_FLASH, 0, 1.5, 3.5).sound(SOUND_PUNCH).shake(10)
                .shot(45).cam(0, 3, 6).look(0, 0.5, 14).say(FRIEZA, "defeated")
                .at(0).fall("frieza")
                .shot(55).cam(-2, 2, -1).look(0, 1.6, 3).lookTo(0, 9, 0).say(GOKU, "pod")
                .at(20).fly("goku", 0, 25, -10, 30, 0).at(50).vanishInLight("goku")
                .shot(55).cam(0, 10, -15).look(0, 0, 0).narrate("escape")
                .at(0).burst(BURST_LAVA, 5, 0, 5).burst(BURST_LAVA, -6, 0, -2).shake(20).at(25).burst(BURST_BIG_EXPLOSION, 0, 0, 10).sound(SOUND_BOOM)
                .build();
    }

    // ================================================================ chapter 3

    /** A time machine at Capsule Corp: Trunks, from twenty years ahead. */
    static Cutscene trunksArrives() {
        return Cutscene.of("dbz.trunks_arrives")
                .cast("trunks", TRUNKS, 0, 0, 4, 180).hidden()
                .shot(45).cam(0, 6, -6).look(0, 10, 4).lookTo(0, 1, 4).narrate("time_machine")
                .at(20).burst(BURST_FLASH, 0, 1.5, 4).burst(BURST_SMOKE, 0, 0, 4).sound(SOUND_WHOOSH).shake(5).at(25).appear("trunks", 0, 0, 4)
                .shot(55).cam(0, 1.8, 1).look(0, 1.7, 4).say(TRUNKS, "arrive_1")
                .shot(45).cam(2, 1.8, 2).look(0, 1.6, 4).say(TRUNKS, "arrive_2")
                .at(10).pose("trunks", PUNCH_R)
                .shot(45).cam(-2, 2, 0).look(0, 1.5, 4).say(TRUNKS, "arrive_3")
                .build();
    }

    /** Cell - the imperfect one - comes for the fallen 17 and takes him in with his tail. */
    static Cutscene cellAbsorbs17() {
        return Cutscene.of("dbz.cell_absorbs_17")
                .cast("a17", ANDROID_17, 2, 0, 3, 180).bind().ends(End.REMOVE)
                .cast("a18", ANDROID_18, -2, 0, 3, 180).bind().ends(End.STAY)
                .cast("cell", CELL_IMPERFECT, 0, 0, 12, 180).hidden().ends(End.STAY)
                .cast("trunks", TRUNKS, -1, 0, -4, 0).bind()
                .shot(45).cam(0, 4, -6).look(0, 1, 6).lookTo(0, 1, 11).narrate("presence")
                .at(0).pose("a17", DOWN).pose("a18", DOWN).at(15).appear("cell", 0, 0, 12)
                .shot(45).cam(1, 2, 8).look(0, 1.6, 12).say(CELL, "absorb_17")
                .shot(45).cam(4, 2, 2).look(2, 1, 4)
                .at(0).move("cell", 2, 0, 5, 25)
                .shot(45).cam(3, 1.5, 1).look(2, 1, 3.5)
                .at(0).pose("cell", CARRY).at(12).vanish("a17").at(18).transform("cell", CELL_SEMI).shake(10)
                .shot(45).cam(-2, 2, 0).look(2, 1.8, 5).say(CELL, "semi")
                .at(0).pose("cell", IDLE)
                .build();
    }

    /** Android 16 can't stop him: Cell takes 18 too - perfect at last - and calls the Cell Games. */
    static Cutscene cellAbsorbs18() {
        return Cutscene.of("dbz.cell_absorbs_18")
                .cast("cell", CELL_SEMI, 2, 0, 5, 180).bind().ends(End.REMOVE)
                .cast("a18", ANDROID_18, -2, 0, 3, 180).bind().ends(End.REMOVE)
                .cast("a16", ANDROID_16, -4, 0, -3, 0).bind()
                .cast("trunks", TRUNKS, -1, 0, -4, 0).bind()
                .shot(45).cam(-5, 2, 1).look(-2, 1, 3).say(ANDROID_16, "protect")
                .at(0).fly("a16", -3, 0, 2, 10, 0.5).at(11).pose("a16", GUARD)
                .shot(32).cam(0, 3, 0).look(-2, 1, 3)
                .at(0).pose("cell", KICK).knock("a16", -8, 0, -4, 12).burst(BURST_FLASH, -3, 1.5, 2).sound(SOUND_PUNCH)
                .shot(55).cam(-1, 1.5, 1.5).look(-2, 1, 3)
                .at(0).move("cell", -1.2, 0, 3.6, 15).at(16).pose("cell", CARRY).at(26).vanish("a18")
                .at(32).transform("cell", CELL).burst(BURST_FLASH, -1.2, 1.5, 3.6).shake(20).sound(SOUND_POWER)
                .shot(65).cam(0, 1.8, 0.5).camTo(0, 2, -1).look(-1.2, 2, 3.6).say(CELL, "perfect")
                .at(0).pose("cell", IDLE)
                .shot(55).cam(0, 3, -5).look(-1, 2, 3).lookTo(-1, 20, 10).say(CELL, "games_announce")
                .at(30).fly("cell", -1, 25, 10, 20, 0).at(52).vanishInLight("cell")
                .build();
    }

    /** At the Cell Games: Cell crushes 16 - and Gohan snaps. Super Saiyan 2. */
    static Cutscene gohanAwakens() {
        return Cutscene.of("dbz.gohan_awakens")
                .cast("cell", CELL, 0, 0, 6, 180).hidden().ends(End.FIGHT)
                .cast("a16", ANDROID_16, 3, 0, 3, 200).bind().ends(End.REMOVE)
                .cast("gohan", GOHAN_TEEN, 0, 0, -2, 0).bind()
                .cast("goku", GOKU_SSJ, -3, 0, -5, 0).bind()
                .shot(45).cam(0, 4, -4).look(0, 1, 6).narrate("cell_enters")
                .at(5).appear("cell", 0, 0, 6)
                .shot(45).cam(4, 2, 1).look(3, 0.8, 3).say(ANDROID_16, "gohan_free")
                .at(0).pose("a16", DOWN)
                .shot(32).cam(2, 2, 5).look(3, 0.5, 3)
                .at(0).move("cell", 2.2, 0, 3.8, 15).at(15).pose("cell", KICK).at(18).vanishInLight("a16").burst(BURST_EXPLOSION, 3, 0.5, 3).shake(8)
                .shot(65).cam(0, 1.7, 0.5).camTo(0, 1.8, -0.5).look(0, 1.6, -2).say(GOHAN_TEEN, "snap")
                .at(10).pose("gohan", TRANSFORM).at(30).burst(BURST_LIGHTNING, 1, 0, -2).sound(SOUND_THUNDER).shake(20)
                .shot(65).cam(0, 3, -8).camTo(0, 4, -11).look(0, 2, -2).narrate("ssj2")
                .at(5).transform("gohan", GOHAN_TEEN_SSJ2).burst(BURST_GOLD_PILLAR, 0, 0, -2).burst(BURST_CRACK, 0, 0, -2)
                .shot(45).cam(2, 2, 4).look(0, 1.6, 6).say(CELL, "finally")
                .at(0).pose("gohan", IDLE).move("cell", 0, 0, 4, 20)
                .build();
    }

    /** Cell swells to take the Earth with him; Goku takes him away with Instant Transmission. */
    static Cutscene cellSelfDestruct() {
        return Cutscene.of("dbz.cell_self_destruct")
                .cast("cell", CELL, 0, 0, 4, 180).bind().ends(End.REMOVE)
                .cast("goku", GOKU_SSJ, 0, 0, -3, 0).bind().ends(End.REMOVE)
                .cast("gohan", GOHAN_TEEN_SSJ2, -3, 0, -3, 0).bind()
                .shot(45).cam(2, 2, 1).look(0, 1.6, 4).say(CELL, "explode")
                .at(0).pose("cell", TRANSFORM).at(10).glow("cell", true)
                .shot(45).cam(0, 1.8, -5).look(0, 1.6, -3).say(GOKU, "bye")
                .at(10).pose("goku", IDLE)
                .shot(22).linear().cam(-3, 2, -2).camTo(-2, 2, 2).look(0, 1.5, -3).lookTo(0, 1.5, 3)
                .at(0).fly("goku", 0, 0, 3, 6, 0).at(8).vanishInLight("goku").vanishInLight("cell").burst(BURST_FLASH, 0, 1.5, 3.5).sound(SOUND_WHOOSH)
                .shot(55).cam(-2, 2, -6).look(-3, 1.6, -3).say(GOHAN_TEEN, "dad")
                .shot(45).cam(0, 10, -10).look(0, 30, 10).narrate("far_away")
                .at(20).burst(BURST_BIG_EXPLOSION, 0, 40, 30).shake(15)
                .build();
    }

    /** Cell's back - and Gohan's one-handed Kamehameha, with his father's voice in his ear, ends him. */
    static Cutscene fatherSonKamehameha() {
        return Cutscene.of("dbz.father_son_kamehameha")
                .cast("cell", CELL, 0, 0, 6, 180).bind().ends(End.REMOVE)
                .cast("gohan", GOHAN_TEEN_SSJ2, 0, 0, -3, 0).bind()
                .shot(45).cam(2, 2, 3).look(0, 1.6, 6).say(CELL, "kamehameha")
                .at(0).pose("cell", CHARGE).sound(SOUND_CHARGE)
                .shot(45).cam(-1.5, 1.6, -4.5).look(0, 1.6, -3).say(GOHAN_TEEN, "one_arm")
                .at(10).pose("gohan", CHARGE)
                .shot(45).cam(-3, 6, -8).look(0, 8, 0).say(GOKU, "from_heaven")
                .shot(65).cam(-5, 3, 1).look(0, 1.6, 1.5)
                .at(0).beam("gohan", "cell", BEAM_KAME, 50).beam("cell", "gohan", BEAM_KAME, 30).at(30).burst(BURST_FLASH, 0, 1.6, 1.5).shake(20).sound(SOUND_BOOM)
                .shot(45).cam(1, 3, 2).look(0, 1.6, 6).say(CELL, "impossible")
                .at(20).vanishInLight("cell").burst(BURST_BIG_EXPLOSION, 0, 1.5, 6).shake(20)
                .shot(55).cam(0, 1.7, -1).look(0, 1.6, -3).say(GOHAN_TEEN, "its_over")
                .at(10).transform("gohan", GOHAN_TEEN).pose("gohan", IDLE)
                .build();
    }

    // ================================================================ chapter 4

    /** Mr. Satan walks up to Majin Buu with a present - and they become friends. */
    static Cutscene buuBefriends() {
        return Cutscene.of("dbz.buu_befriends")
                .cast("buu", MAJIN_BUU, 0, 0, 5, 180).bind().ends(End.STAY)
                .cast("satan", MR_SATAN, 8, 0, 2, 270)
                .cast("goku", GOKU_SSJ, -3, 0, -4, 0).bind()
                .shot(45).cam(6, 2, -1).look(8, 1.6, 2).say(MR_SATAN, "buu_hi")
                .at(5).move("satan", 2, 0, 4, 30)
                .shot(45).cam(2, 1.8, 2.5).look(1, 1.6, 4.5).say(MR_SATAN, "present")
                .at(0).face("satan", "buu").at(10).pose("satan", FIRE)
                .shot(45).cam(-1, 2, 2).look(0, 1.8, 5).say(MAJIN_BUU, "friend")
                .at(10).pose("buu", HANDS_UP)
                .shot(45).cam(0, 3, -3).look(1, 1.5, 4.5).narrate("friends")
                .at(0).pose("satan", HANDS_UP).at(30).pose("buu", IDLE)
                .build();
    }

    /** The evil steams out of Buu as a Buu of its own - and it eats the good one. */
    static Cutscene superBuu() {
        return Cutscene.of("dbz.super_buu")
                .cast("buu", MAJIN_BUU, 0, 0, 5, 180).bind().ends(End.REMOVE)
                .cast("super_buu", SUPER_BUU, -1.5, 0, 7, 160).hidden().ends(End.FIGHT)
                .cast("goku", GOKU_SSJ, -3, 0, -4, 0).bind()
                .shot(45).cam(2, 2, 2).look(0, 1.8, 5).narrate("steam")
                .at(0).burst(BURST_SMOKE, 0, 2.5, 5).pose("buu", TRANSFORM).shake(10)
                .shot(45).cam(-2, 2, 1).look(-1.5, 1.5, 7).say(MAJIN_BUU, "evil")
                .at(5).appear("super_buu", -1.5, 0, 7)
                .shot(45).cam(0, 2.5, 1.5).look(0, 1.5, 6)
                .at(5).move("super_buu", -0.6, 0, 6.2, 15).at(20).beam("super_buu", "buu", BEAM_CANDY, 15).at(36).vanish("buu")
                .shot(55).cam(0, 1.8, 3).look(-0.6, 1.8, 6.2).say(MAJIN_BUU, "ate_him")
                .at(0).pose("super_buu", IDLE)
                .shot(45).cam(0, 4, -6).look(0, 1.5, 5).say(GOKU, "evil_buu")
                .build();
    }

    /** Super Buu's goo takes Gohan and Piccolo into him - and he grows stronger. */
    static Cutscene buuAbsorbs() {
        return Cutscene.of("dbz.buu_absorbs")
                .cast("super_buu", SUPER_BUU, 0, 0, 5, 180).bind()
                .cast("gohan", GOHAN_TEEN, -2, 0, -2, 0).bind().ends(End.REMOVE)
                .cast("piccolo", PICCOLO, 2, 0, -2, 0).bind().ends(End.REMOVE)
                .shot(45).cam(1.5, 2, 3).look(0, 1.6, 5).say(MAJIN_BUU, "absorb")
                .at(10).burst(BURST_SMOKE, -1, 1.5, 2).burst(BURST_SMOKE, 1, 1.5, 2)
                .shot(45).cam(0, 2, -5).look(0, 1, -2).say(PICCOLO, "what")
                .at(10).pose("gohan", HURT).pose("piccolo", HURT).at(25).fly("gohan", -0.3, 1, 4.5, 12, 0.5).fly("piccolo", 0.3, 1, 4.5, 12, 0.5)
                .at(38).vanish("gohan").vanish("piccolo")
                .shot(65).cam(0, 3, 1).camTo(0, 4, 0).look(0, 2, 5).narrate("absorbed")
                .at(10).transform("super_buu", SUPER_BUU_ABSORBED).shake(15)
                .shot(45).cam(-2, 2, 2).look(0, 1.8, 5).say(MAJIN_BUU, "stronger")
                .at(0).pose("super_buu", IDLE)
                .build();
    }

    /** Beaten, Buu lets go of the ones he took - and shrinks into Kid Buu, who flies off to the wastes. */
    static Cutscene kidBuu() {
        return Cutscene.of("dbz.kid_buu")
                .cast("super_buu", SUPER_BUU_ABSORBED, 0, 0, 5, 180).bind().ends(End.REMOVE)
                .cast("gohan", GOHAN_TEEN, -1.5, 0, 4, 0).hidden().ends(End.FIGHT)
                .cast("piccolo", PICCOLO, 1.5, 0, 4, 0).hidden().ends(End.FIGHT)
                .cast("goku", GOKU_SSJ, 0, 0, -3, 0).bind()
                .shot(45).cam(2, 2, 1).look(0, 1.6, 5).narrate("collapse")
                .at(0).pose("super_buu", HURT).shake(10)
                .shot(45).cam(-2, 1.5, 1.5).look(0, 1.2, 4).say(GOHAN_TEEN, "free")
                .at(5).appear("gohan", -1.5, 0, 3).appear("piccolo", 1.5, 0, 3)
                .shot(65).cam(0, 3, -2).camTo(0, 4, -4).look(0, 1.5, 5).narrate("kid")
                .at(10).transform("super_buu", KID_BUU).burst(BURST_SMOKE, 0, 1, 5).shake(20).sound(SOUND_ROAR)
                .shot(45).cam(1, 1.2, 3).look(0, 1, 5).say(KID_BUU, "laugh")
                .at(22).fly("super_buu", 0, 30, 30, 20, 0).at(42).vanishInLight("super_buu")
                .shot(45).cam(-3, 2, -5).look(0, 1.6, -3).say(GOKU, "wasteland")
                .build();
    }

    /** Everyone raises their hands; the Spirit Bomb wipes Kid Buu away. The end of the story. */
    static Cutscene spiritBombEnd() {
        return Cutscene.of("dbz.spirit_bomb_end")
                .cast("kid_buu", KID_BUU, 0, 0, 6, 180).bind().ends(End.REMOVE)
                .cast("goku", GOKU_SSJ, 0, 0, -3, 0).bind()
                .cast("bulma", BULMA, -6, 0, -7, 0)
                .cast("krillin", KRILLIN, -3, 0, -8, 0)
                .cast("satan", MR_SATAN, 3, 0, -8, 0)
                .cast("dende", DENDE, 6, 0, -7, 0)
                .shot(55).cam(0, 2, -1).look(0, 6, -3).say(GOKU, "hands_up")
                .at(0).pose("goku", HANDS_UP)
                .shot(65).cam(-4, 3, -12).camTo(4, 3, -12).look(0, 1.5, -7).narrate("everyone")
                .at(5).pose("bulma", HANDS_UP).pose("krillin", HANDS_UP).at(15).pose("satan", HANDS_UP).pose("dende", HANDS_UP).sound(SOUND_CROWD)
                .shot(55).cam(0, 4, -7).look(0, 12, -3).narrate("villages")
                .at(10).burst(BURST_SPIRIT_BOMB, 0, 12, -3).at(30).burst(BURST_SPIRIT_BOMB, 0, 13, -3).at(50).burst(BURST_SPIRIT_BOMB, 0, 14, -3)
                .shot(45).cam(2, 2, 3).look(0, 1.6, 6).say(KID_BUU, "scream")
                .shot(55).cam(-6, 5, 0).look(0, 6, 3)
                .at(0).beam("goku", "kid_buu", BEAM_WHITE, 30).at(30).burst(BURST_BIG_EXPLOSION, 0, 1.5, 6).vanishInLight("kid_buu").shake(25).sound(SOUND_BOOM)
                .shot(65).cam(0, 2, -1).look(0, 1.6, -3).say(GOKU, "good_bye")
                .at(0).pose("goku", IDLE).pose("bulma", IDLE).pose("krillin", IDLE).pose("satan", IDLE).pose("dende", IDLE)
                .shot(65).cam(0, 12, -14).look(0, 0, 0).narrate("ending_1")
                .shot(70).cam(0, 20, -20).camTo(0, 30, -28).look(0, 0, 0).narrate("ending_2")
                .build();
    }
}
