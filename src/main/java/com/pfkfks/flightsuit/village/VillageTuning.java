package com.pfkfks.flightsuit.village;

/** Village numbers in one place (DESIGN.md 4-12). Like SuitTuning, intentionally untuned for now. */
public final class VillageTuning {
    /** How far the village reaches around its hall, sideways (a square, so it matches the board's "area"). */
    public static final int RADIUS = 48;
    /** And up / down. */
    public static final int HEIGHT = 24;

    /** How long a downed resident lasts before dying for good (DESIGN: "쓰러지면 일정 시간 안에 치료"). */
    public static final int DOWNED_TICKS = 20 * 60;
    public static final float REVIVE_HEALTH = 6.0F;

    /** The alarm stays up this long after the last enemy was seen or the last resident was hit. */
    public static final int ALARM_TICKS = 20 * 30;
    /** A guard rings a bell this close to them when the alarm goes up. */
    public static final int BELL_REACH = 24;

    /** A wanderer who isn't taken in moves on after a day. */
    public static final int WANDERER_STAY_TICKS = 24000;
    /** The first wanderer of a new village (with a free bed) shows up after this, not the next morning. */
    public static final int FIRST_WANDERER_DELAY = 20 * 30;
    /** Morning roll for a wanderer, before happiness: base + happiness / 200. */
    public static final float WANDERER_BASE_CHANCE = 0.35F;

    /** Architects on one building at most; each takes a quarter of it (user's call: "4명 정도, 1/4씩"). */
    public static final int CREW_SIZE = 4;

    /** Ticks between two blocks a builder puts back. */
    public static final int REPAIR_INTERVAL = 15;
    public static final int LEDGER_MAX = 4096;

    public static final int NEWS_LINES = 3;
    /** A death weighs on everyone's mood (and the village's safety) for this many days. */
    public static final int DEATH_MEMORY_DAYS = 3;
    /** One guard keeps this many residents "safe" on the board. */
    public static final int RESIDENTS_PER_GUARD = 4;

    /** Residents a village needs before the architects suggest expanding the hall to {@code stage}. */
    public static int stagePopulation(int stage) {
        return stage == 2 ? 6 : 15;
    }

    /** Work speed for a talent of 1..5 stars: 60% .. 140% (★3 = normal). */
    public static float talentSpeed(int talent) {
        return 0.6F + 0.2F * (Math.max(1, talent) - 1);
    }

    /** A guard's talent: extra health (hearts x2) and sword damage per star above one. */
    public static final double GUARD_HEALTH_PER_STAR = 4.0D;
    public static final double GUARD_DAMAGE_PER_STAR = 1.0D;

    private VillageTuning() {
    }
}
