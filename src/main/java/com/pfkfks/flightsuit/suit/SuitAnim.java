package com.pfkfks.flightsuit.suit;

/** One-shot body animations, by PlayerAnimator name (assets/flightsuit/player_animation/*.json). */
public enum SuitAnim {
    SUIT_UP_GROUND("suit_up_ground"),
    SUIT_UP_FALL("suit_up_fall"),
    SUIT_EJECT("suit_eject"),
    SUIT_UP_BOARD("suit_up_station"),
    PARRY("parry"),
    COUNTER("counter"),
    STATION_RIG("station_rig"),
    STATION_UNRIG("station_unrig"),
    MISSILE_LAUNCH("missile_launch"),
    SPIN_ATTACK("spin_attack"),
    // M17 (sent by ordinal: add at the end).
    HULK_PUNCH_LEFT("hulk_punch_left"),
    HULK_PUNCH_RIGHT("hulk_punch_right"),
    HULK_SLAM("hulk_slam"),
    SWORD_H("sword_h"),
    SWORD_V("sword_v"),
    SWORD_THRUST("sword_thrust"),
    BURNING_ATTACK("burning_attack"),
    SWORD_SHEATHE("sword_sheathe"),
    SWORD_PARRY("sword_parry"),
    TEMPEST_THROW("tempest_throw");

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
