package com.pfkfks.flightsuit.war;

/** Raid numbers in one place (DESIGN.md 4-11, M10). Untuned first pass. */
public final class WarTuning {
    /** A new village is left alone this many days before the first raid. */
    public static final int FIRST_RAID_DAYS = 3;
    /** Days between raids: this plus up to RAID_INTERVAL_SPREAD more. */
    public static final int RAID_INTERVAL = 4;
    public static final int RAID_INTERVAL_SPREAD = 4;
    /** Not worth an army below this many residents (the raid is put off two days). */
    public static final int MIN_POPULATION = 2;
    /** When the scouts report the army (time of day), and when it arrives (dusk). */
    public static final long WARNING_TIME = 9000L;
    public static final long RAID_TIME = 12500L;

    /** Where the army forms up: this far from the hall, just outside the village. */
    public static final int SPAWN_DISTANCE = 56;
    /** Soldiers per wave: base + population / 2, capped (DESIGN 전투 규모: 수십 명 수준, 웨이브로). */
    public static final int WAVE_BASE = 4;
    public static final int WAVE_MAX = 10;
    /** The next wave comes when this share of the current one is left, or after WAVE_TIMEOUT ticks. */
    public static final float WAVE_REMAINING = 0.34F;
    public static final int WAVE_TIMEOUT = 20 * 60;
    /** A raid that hasn't broken by then pulls back (and loots on the way). */
    public static final int RAID_MAX_TICKS = 12000;
    /** No answer to a surrender: the prisoners are let go. */
    public static final int SURRENDER_DECISION_TICKS = 20 * 180;

    /** Chance a burning arrow sets what it hits alight (inside a village). */
    public static final float FIRE_CHANCE = 0.35F;
    /** Chance a raid is a fire attack (화공): its archers shoot burning arrows. */
    public static final float FIRE_RAID_CHANCE = 0.5F;

    public static final double SOLDIER_HEALTH = 24.0D;
    public static final double SPEARMAN_HEALTH = 20.0D;
    public static final double ARCHER_HEALTH = 18.0D;

    private WarTuning() {
    }
}
