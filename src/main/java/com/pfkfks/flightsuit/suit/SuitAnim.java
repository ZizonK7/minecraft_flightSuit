package com.pfkfks.flightsuit.suit;

/** One-shot body animations, by PlayerAnimator name (assets/flightsuit/player_animation/*.json). */
public enum SuitAnim {
    SUIT_UP_GROUND("suit_up_ground"),
    REPULSOR_RIGHT("repulsor_right"),
    SUIT_UP_FALL("suit_up_fall"),
    SUIT_EJECT("suit_eject"),
    SUIT_UP_BOARD("suit_up_station"),
    PARRY("parry"),
    COUNTER("counter"),
    STATION_RIG("station_rig"),
    STATION_UNRIG("station_unrig");

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
