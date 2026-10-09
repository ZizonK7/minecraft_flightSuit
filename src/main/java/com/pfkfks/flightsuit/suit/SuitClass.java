package com.pfkfks.flightsuit.suit;

/**
 * What a suit is built for (DESIGN.md 4-2 "클래스"). The class decides which special abilities the suit
 * gets on top of the shared basics - e.g. only stealth suits can cloak and fire freeze beams.
 */
public enum SuitClass {
    /** Mark-style all-rounder: palm repulsor, standard sensors. */
    STANDARD(32.0D),
    /** Stealth: active camouflage, freeze beam instead of a repulsor blast, longer-range helmet sensors. */
    STEALTH(48.0D);

    private final double sensorRange;

    SuitClass(double sensorRange) {
        this.sensorRange = sensorRange;
    }

    public double sensorRange() {
        return sensorRange;
    }

    public boolean canCloak() {
        return this == STEALTH;
    }

    public boolean hasFreezeBeam() {
        return this == STEALTH;
    }
}
