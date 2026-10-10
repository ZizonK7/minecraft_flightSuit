package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscene.End;
import com.pfkfks.flightsuit.cutscene.Cutscenes;

import static com.pfkfks.flightsuit.planet.dbz.DbzCharacter.*;

/**
 * The Dragon Ball story's cutscenes (M17), as scripts for the cutscene engine (cutscene.Cutscene; DESIGN.md / the
 * M17 work order list them by chapter). Ids are "dbz.<name>"; the anchor is the scene's arena centre (yaw 0: x east,
 * z south), and every line is dbz.flightsuit.<voice>.<key> (narration: cutscene.flightsuit.<id>.<key>).
 */
public final class DbzCutscenes {
    private DbzCutscenes() {
    }

    public static void registerAll() {
        Cutscenes.register(ginyuPose());
    }

    /** Chapter 2: the Ginyu Force lands and strikes its poses, one by one, then all together. */
    static Cutscene ginyuPose() {
        return Cutscene.of("dbz.ginyu_pose")
                .cast("ginyu", GINYU, 0, 14, 6, 180)
                .cast("recoome", RECOOME, -3, 15, 6, 180)
                .cast("burter", BURTER, 3, 15, 6, 180)
                .cast("jeice", JEICE, -5.5, 16, 5, 180)
                .cast("guldo", GULDO, 5.5, 16, 5, 180)
                .shot(30).cam(0, 8, -8).camTo(0, 4, -4).look(0, 8, 6).lookTo(0, 1.5, 6)
                .at(0).fly("ginyu", 0, 0, 6, 22, 0).fly("recoome", -3, 0, 6, 22, 0).fly("burter", 3, 0, 6, 22, 0)
                .fly("jeice", -5.5, 0, 5, 24, 0).fly("guldo", 5.5, 0, 5, 24, 0)
                .at(24).burst(Cutscene.BURST_SMOKE, 0, 0, 6).sound(Cutscene.SOUND_BOOM).pose("ginyu", DbzAction.IDLE)
                .shot(24).cam(-6, 2, 1).look(-5.5, 1.5, 5).say(JEICE, "pose")
                .at(4).pose("jeice", DbzAction.POSE_GINYU)
                .shot(24).cam(6, 2, 1).look(5.5, 1, 5).say(GULDO, "pose")
                .at(4).pose("guldo", DbzAction.POSE_GINYU)
                .shot(24).cam(3, 2.5, 2).look(3, 1.8, 6).say(BURTER, "pose")
                .at(4).pose("burter", DbzAction.POSE_GINYU)
                .shot(24).cam(-3, 2.5, 2).look(-3, 2, 6).say(RECOOME, "pose")
                .at(4).pose("recoome", DbzAction.POSE_GINYU)
                .shot(30).cam(0, 2, 2.5).camTo(0, 2.5, 1.5).look(0, 2, 6).say(GINYU, "pose")
                .at(4).pose("ginyu", DbzAction.POSE_GINYU)
                .shot(40).cam(0, 3, -6).camTo(0, 3.5, -9).look(0, 1.8, 6).narrate("all")
                .burst(Cutscene.BURST_FLASH, 0, 2, 6).shake(10).sound(Cutscene.SOUND_POWER)
                .build();
    }
}
