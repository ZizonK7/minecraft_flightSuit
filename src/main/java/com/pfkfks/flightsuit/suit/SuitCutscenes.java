package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscenes;

/**
 * The suits' own camera scenes (M18 feedback): short, free scenes - the player keeps moving and is drawn, nobody
 * is held - that only steer the camera while a big move plays out. The player stands at the anchor facing +Z.
 */
public final class SuitCutscenes {
    public static final String DRAGON_FIST = "suit.dragon_fist";
    public static final String HULK_FINISHER = "suit.hulk_finisher";
    public static final String HERO_SONG = "suit.hero_song";

    private SuitCutscenes() {
    }

    public static void registerAll() {
        Cutscenes.register(dragonFist());
        Cutscenes.register(hulkFinisher());
        Cutscenes.register(heroSong());
    }

    /**
     * Trunks' Dragon Fist (SwordArts.dragonFist): from behind his shoulder as he turns Super Saiyan 3 - the mane
     * falls down his back at the fifth tick - circling round (26), then low in front looking up at him (24); a wide
     * shot from the side following the golden dragon down its 28-block path (28), then close on him where he lands
     * (22). 100 ticks, the same clock as the move. (M18 3rd feedback: the front-only view never showed the mane.)
     */
    static Cutscene dragonFist() {
        return Cutscene.of(DRAGON_FIST).free()
                .shot(26).cam(-1.9, 1.4, -2.9).camTo(-3.1, 1.6, -0.6).look(0, 1.6, 0).lookTo(0, 1.7, 0)
                .at(5).shake(12)
                .shot(24).cam(-2.3, 0.6, 3.0).camTo(-1.7, 1.2, 2.3).look(0, 1.1, 0).lookTo(0, 1.7, 0)
                .at(18).shake(6)
                .shot(28).linear().cam(11, 4.5, 6).camTo(13, 5.5, 20).look(0, 1.5, 0).lookTo(0, 1.5, 28)
                .shot(22).cam(-5, 2.6, 34).camTo(-3, 2.0, 31.5).look(0, 1.5, 28).lookTo(0, 1.2, 28)
                .build();
    }

    /**
     * The Hulkbuster's finisher (HulkbusterArts.finisher, 2026-10-12): low beside him as he grabs the foe (16), round
     * the front of the two through the flurry (40), behind his shoulder for the last punch and the foe flying off (20).
     * 76 ticks, the same clock as the move; distances allow for the suit's 1.5x size.
     */
    static Cutscene hulkFinisher() {
        return Cutscene.of(HULK_FINISHER).free()
                .shot(16).cam(-4.5, 1.2, 1.0).camTo(-4.0, 1.8, 2.5).look(0, 2.0, 1.5).lookTo(0, 2.2, 2.0)
                .shot(40).linear().cam(4.5, 2.2, 5.5).camTo(-4.5, 2.6, 5.0).look(0, 2.0, 1.8).lookTo(0, 2.0, 1.8)
                .at(0).shake(40)
                .shot(20).cam(-2.4, 3.2, -3.4).camTo(-3.0, 3.6, -5.0).look(0, 2.0, 3.0).lookTo(0, 2.6, 9.0)
                .at(10).shake(10)
                .build();
    }

    /**
     * Link's song (HeroArts.song, 2026-10-12, user: "컷씬은 짧게 피리 부는 느낌"): close in front of him playing, the
     * camera drifting round to his side (32), then over his shoulder down the line to the one put to sleep (12).
     */
    static Cutscene heroSong() {
        return Cutscene.of(HERO_SONG).free()
                .shot(32).cam(0.9, 1.55, 2.2).camTo(-1.8, 1.7, 1.5).look(0, 1.55, 0).lookTo(0, 1.6, 0.2)
                .shot(12).cam(-1.1, 2.0, -2.0).camTo(-1.3, 2.2, -2.6).look(0, 1.2, 8).lookTo(0, 1.0, 9)
                .build();
    }
}
