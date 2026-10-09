package com.pfkfks.flightsuit.suit;

/** Balance numbers in one place so playtest tuning doesn't mean hunting through handlers. Energy is in FE. */
public final class SuitTuning {
    private SuitTuning() {
    }

    // Storage: the chestplate's arc reactor is the main battery, the other pieces only hold a small buffer.
    public static final int CHEST_CAPACITY = 20_000;
    public static final int PIECE_CAPACITY = 2_000;
    public static final int ENERGY_CELL_CHARGE = 10_000;

    // Per-tick drains.
    public static final int NIGHT_VISION_COST = 1;
    public static final int HOVER_COST = 8;
    public static final int BOOST_COST = 20;
    public static final int THRUST_COST = 12;

    // Repulsor.
    public static final int REPULSOR_COST = 300;
    public static final int REPULSOR_COOLDOWN_TICKS = 8;
    public static final double REPULSOR_RANGE = 48.0D;
    public static final float REPULSOR_DAMAGE = 7.0F;
    /** Stealth cryo beam: weaker hit, but the target freezes solid. */
    public static final float FREEZE_BEAM_DAMAGE = 4.0F;
    public static final int FREEZE_TICKS = 100;

    // Stealth active camouflage (full set, while sneaking).
    public static final int CLOAK_COST = 6;

    // Boots-only thrust: limited climb per airtime, then the boots can only slow the fall.
    public static final int BOOTS_THRUST_TICKS = 30;
    public static final double BOOTS_THRUST_ACCEL = 0.16D;
    public static final double BOOTS_THRUST_MAX_RISE = 0.5D;
    public static final double BOOTS_GLIDE_MAX_FALL = -0.15D;

    // Full-set boost flight (added on top of vanilla creative-style flying).
    public static final double BOOST_ACCEL_HORIZONTAL = 0.12D;
    public static final double BOOST_ACCEL_VERTICAL = 0.45D;
    public static final double BOOST_MAX_SPEED = 1.8D;
    public static final float SUIT_FLYING_SPEED = 0.08F;
    public static final float VANILLA_FLYING_SPEED = 0.05F;

    // Counter key: tap = parry window, hold = nano shield.
    public static final int PARRY_WINDOW_TICKS = 6;
    public static final int PARRY_COOLDOWN_TICKS = 16;
    public static final int PARRY_COST = 100;
    /** Fraction of the hit that still lands on a successful parry. */
    public static final float PARRY_DAMAGE_TAKEN = 0.2F;
    public static final float COUNTER_DAMAGE = 6.0F;
    public static final int COUNTER_STUN_TICKS = 40;
    public static final int SHIELD_DRAIN = 15;
    public static final int SHIELD_HIT_COST = 60;
    /** Client: how long the key must be held before the parry turns into the shield. */
    public static final int SHIELD_HOLD_TICKS = 6;

    // Leggings power assist.
    public static final double LEGS_SPEED_BONUS = 0.2D;
    public static final double LEGS_STEP_BONUS = 0.5D;
    public static final float LEGS_FALL_MULTIPLIER = 0.5F;
}
