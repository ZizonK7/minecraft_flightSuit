package com.pfkfks.flightsuit.energy;

/** Power block numbers (FE and FE/tick). */
public final class PowerTuning {
    private PowerTuning() {
    }

    public static final int SOLAR_OUTPUT = 30;
    public static final float SOLAR_RAIN_FACTOR = 0.3F;
    public static final int SOLAR_BUFFER = 4_000;

    public static final int GENERATOR_OUTPUT = 60;
    public static final int GENERATOR_BUFFER = 8_000;

    public static final int BATTERY_CAPACITY = 1_000_000;
    public static final int BATTERY_TRANSFER = 1_000;

    /** Max FE/tick any node pushes into the grid. */
    public static final int GRID_PUSH = 1_000;

    public static final int STATION_CAPACITY = 200_000;
    public static final int STATION_INPUT = 2_000;
    /** FE/tick the station pours into a docked suit. */
    public static final int STATION_CHARGE_RATE = 400;
    /** Every 10 ticks: durability points restored per damaged piece, and the FE that costs. */
    public static final int STATION_REPAIR_POINTS = 2;
    public static final int STATION_REPAIR_COST = 100;

    // M18 (DESIGN.md 5차 피드백 "공장식 전력"): cables and the power plant.
    /** How far a cable run is followed from a block (cables counted, not distance). */
    public static final int CABLE_MAX_RUN = 256;
    /** Power pushed down a cable per tick, per source. */
    public static final int CABLE_TRANSFER = 2_000;
    /** The power plant: coal in its hopper burns for this share of its furnace time at POWER_PLANT_OUTPUT a tick. */
    public static final int POWER_PLANT_OUTPUT = 200;
    public static final float POWER_PLANT_BURN_FACTOR = 0.5F;
    public static final int POWER_PLANT_BUFFER = 40_000;
    public static final int POWER_PLANT_SLOTS = 9;

    /** Station storage: a small buffer that keeps the vault locked; upkeep is FE/tick. */
    public static final int STORAGE_CAPACITY = 20_000;
    public static final int STORAGE_INPUT = 200;
    public static final int STORAGE_UPKEEP = 2;
}
