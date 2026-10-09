package com.pfkfks.flightsuit.suit;

/**
 * What a suit is built for (DESIGN.md 4-2 "클래스"). The class decides which special abilities the suit
 * gets on top of the shared basics - e.g. only stealth suits can cloak and fire freeze beams.
 */
public enum SuitClass {
    /** Mark-style all-rounder: palm repulsor, standard sensors. */
    STANDARD(32.0D),
    /** Stealth: active camouflage, cryo beam instead of the repulsor beam, longer-range helmet sensors. */
    STEALTH(48.0D),
    /**
     * Phantom thief (MapleStory's Phantom): throws cards instead of firing a beam - Carte Blanche stream,
     * Carte Noir follow-ups, Judgment Draw, Rose Carte Finale (see PhantomCards).
     */
    PHANTOM(40.0D),
    /**
     * Hero of Twilight (Zelda): a swordsman - Master Sword slashes that loose sword beams at full health, spin
     * attack, clawshot, and a Hylian shield that turns projectiles from the front (see HeroArts).
     */
    HERO(36.0D);

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

    /** The hero has no thrusters at all: no flight, no boots thrust - the clawshot is how he gets up and across. */
    public boolean canFly() {
        return this != HERO;
    }

    public boolean hasFreezeBeam() {
        return this == STEALTH;
    }
}
