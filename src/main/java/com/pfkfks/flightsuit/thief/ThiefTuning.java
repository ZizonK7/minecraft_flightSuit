package com.pfkfks.flightsuit.thief;

/** Batman's crew numbers (DESIGN.md 4-14). */
public final class ThiefTuning {
    /** No visits in the first days of a world (사용자 결정: 처음 5일은 오지 않음). */
    public static final int FIRST_DAY = 5;
    /** After a visit, at least this many days of quiet, then up to EXTRA_GAP more (평균 1~2주에 한 번). */
    public static final int MIN_GAP = 5;
    public static final int EXTRA_GAP = 10;
    /** They come between these times of day, and are gone by dawn. */
    public static final long ARRIVE_FROM = 13500L;
    public static final long ARRIVE_UNTIL = 20000L;
    public static final long DAWN = 23000L;
    /** Spotting: guards (and players) see much further when the thief stands in light (block light >= LIT). */
    public static final int LIT = 8;
    public static final double GUARD_SIGHT_DARK = 6.0D;
    public static final double GUARD_SIGHT_LIT = 14.0D;
    public static final double PLAYER_SIGHT_DARK = 3.0D;
    public static final double PLAYER_SIGHT_LIT = 8.0D;
    /** An armed security sensor catches anyone hidden within this range. */
    public static final double SENSOR_RANGE = 8.0D;
    /** Batman's EMP: power blocks and worn suits around him. */
    public static final double EMP_RANGE = 12.0D;
    public static final float EMP_SUIT_DRAIN = 0.3F;
    /** Breaking into a station storage quietly still makes noise: guards this close hear it. */
    public static final double EMP_HEARD = 16.0D;
    /** Ticks spent at each chest. */
    public static final int LOOT_TICKS = 40;
    /** Robin fights this long before he runs too. */
    public static final int ROBIN_FIGHT_TICKS = 200;
    /** Computed nights (nobody watching): chance the village's security stops them, per guard and per armed sensor. */
    public static final double CATCH_PER_GUARD = 0.12D;
    public static final double CATCH_PER_SENSOR = 0.25D;
    public static final double CATCH_MAX = 0.85D;
    /** Security sensor block. */
    public static final int SENSOR_CAPACITY = 4_000;
    public static final int SENSOR_INPUT = 100;
    public static final int SENSOR_UPKEEP = 1;

    private ThiefTuning() {
    }
}
