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
    HERO(36.0D),
    /**
     * Hulkbuster (DESIGN.md 4-2 헐크버스터형): heavy armour for close fighting that makes its wearer half again as
     * big (SuitSize). No repulsor (M17): all fists - flurry, piston punch, ground slam (HulkbusterArts).
     */
    HULKBUSTER(32.0D),
    /**
     * Swordsman (Trunks, 검사형, M17): cuts on the wing - three-step combo, Burning Attack, flash slash, sword parry,
     * and Super Saiyan on the ultimate key (SwordArts).
     */
    SWORDSMAN(36.0D);

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

    /**
     * The hero has no thrusters at all: no flight, no boots thrust - the clawshot is how he gets up and across.
     * The phantom neither (after the M13 test - flying with its cards was too strong): it blinks (Z, shadow step).
     */
    public boolean canFly() {
        return this != HERO && this != PHANTOM;
    }

    /** Z blinks (shadow step) instead of parrying and shielding. */
    public boolean blinks() {
        return this == PHANTOM;
    }

    public boolean hasFreezeBeam() {
        return this == STEALTH;
    }

    /** Fights with its fists instead of a palm repulsor (Hulkbuster). */
    public boolean punches() {
        return this == HULKBUSTER;
    }

    /** Draws a sword for the primary (Trunks); the hero's Master Sword is HERO's own. */
    public boolean hasSword() {
        return this == SWORDSMAN;
    }

    /** Has something on the ultimate key (V): the phantom's Tempest, Trunks' Super Saiyan. */
    public boolean hasUltimate() {
        return this == PHANTOM || this == SWORDSMAN;
    }
}
