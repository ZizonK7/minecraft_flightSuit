package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.cutscene.Cutscene;
import com.pfkfks.flightsuit.cutscene.Cutscenes;

/**
 * The suits' own camera scenes (M18 feedback): short, free scenes - the player keeps moving and is drawn, nobody
 * is held - that only steer the camera while a big move plays out. The player stands at the anchor facing +Z.
 */
public final class SuitCutscenes {
    public static final String DRAGON_FIST = "suit.dragon_fist";

    private SuitCutscenes() {
    }

    public static void registerAll() {
        Cutscenes.register(dragonFist());
    }

    /**
     * Trunks' Dragon Fist (SwordArts.dragonFist): low in front while he powers up to Super Saiyan 3 (50 ticks), a
     * wide shot from the side following the golden dragon down its 28-block path (28), then close on him where he
     * lands (22). 100 ticks, the same clock as the move.
     */
    static Cutscene dragonFist() {
        return Cutscene.of(DRAGON_FIST).free()
                .shot(50).cam(-2.6, 0.5, 3.4).camTo(-1.8, 1.3, 2.3).look(0, 0.9, 0).lookTo(0, 1.7, 0)
                .at(5).shake(12).at(44).shake(6)
                .shot(28).linear().cam(11, 4.5, 6).camTo(13, 5.5, 20).look(0, 1.5, 0).lookTo(0, 1.5, 28)
                .shot(22).cam(-5, 2.6, 34).camTo(-3, 2.0, 31.5).look(0, 1.5, 28).lookTo(0, 1.2, 28)
                .build();
    }
}
