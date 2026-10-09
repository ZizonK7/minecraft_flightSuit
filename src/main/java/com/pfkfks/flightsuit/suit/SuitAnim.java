package com.pfkfks.flightsuit.suit;

/** One-shot body animations, by PlayerAnimator name (assets/flightsuit/player_animation/*.json). */
public enum SuitAnim {
    SUIT_UP_GROUND("suit_up_ground"),
    REPULSOR_RIGHT("repulsor_right"),
    SUIT_UP_FALL("suit_up_fall"),
    SUIT_UP_STATION("suit_up_station"),
    SUIT_EJECT("suit_eject");

    private final String animationName;

    SuitAnim(String animationName) {
        this.animationName = animationName;
    }

    public String animationName() {
        return animationName;
    }

    public static SuitAnim byId(int id) {
        SuitAnim[] values = values();
        return id >= 0 && id < values.length ? values[id] : SUIT_UP_GROUND;
    }
}
