package com.pfkfks.flightsuit.suit;

/**
 * What a suit is built for (DESIGN.md 4-2 "클래스", the M18 line-up in 5장 "5차 피드백"). The class decides which
 * special abilities the suit gets on top of the shared basics - e.g. only stealth suits can cloak and fire freeze
 * beams - and, since M18, whether the wearer fights with a weapon in hand (SuitWeaponItem) or with bare palms.
 */
public enum SuitClass {
    /**
     * Scrap Mark 0 (M18): the cheap first suit, iron and copper, a coal boiler for a battery - a weak repulsor, a
     * flamethrower, a rocket jump, an overdrive and a flare (ScrapArts). Flies, slowly.
     */
    SCRAP(24.0D),
    /** Mark-style all-rounder: palm repulsor, standard sensors; M18 the full burst (V) and the EDITH scan (B). */
    STANDARD(32.0D),
    /**
     * Stealth: active camouflage, cryo beam instead of the repulsor beam, longer-range helmet sensors; M18 the
     * mining laser (X), the x-ray mode (V) and the light orb (B) - the explorer's suit.
     */
    STEALTH(48.0D),
    /**
     * Phantom thief (MapleStory's Phantom): throws cards instead of firing a beam - Carte Blanche stream,
     * Carte Noir follow-ups, Judgment Draw, Rose Carte Finale (see PhantomCards).
     */
    PHANTOM(40.0D),
    /**
     * Hero of Twilight (Zelda): a swordsman - Master Sword slashes that loose sword beams at full health, spin
     * attack, clawshot, and a Hylian shield that turns projectiles from the front (see HeroArts). M18: the sword
     * is an item in hand, right click raises the shield, the Triforce (V), the gale boomerang (B).
     */
    HERO(36.0D),
    /**
     * Hulkbuster (DESIGN.md 4-2 헐크버스터형): heavy armour for close fighting that makes its wearer half again as
     * big (SuitSize). No repulsor (M17): all fists - flurry, piston punch, ground slam (HulkbusterArts). M18 the
     * boss-fighter: armour mode (Z held), the jackhammer (V), block throwing (B).
     */
    HULKBUSTER(32.0D),
    /**
     * Swordsman (Trunks, 검사형, M17): cuts on the wing - three-step combo, Burning Attack, flash slash, sword parry,
     * and Super Saiyan on the ultimate key (SwordArts). M18: the sword is an item in hand (left click cuts), right
     * click charges a ki slash, B is a ki volley.
     */
    SWORDSMAN(36.0D),
    /**
     * Cannoneer (MapleStory's Cannon Shooter, Mark 6, M18): a cannon in hand and a monkey at heel - the area
     * fighter. Cannonballs, a recoil jump, monkey turrets, a rolling fan, the giant cannonball, the monkey rush
     * (CannonArts).
     */
    CANNONEER(32.0D),
    /**
     * Bishop (MapleStory's Bishop, Mark 7, M18): the support suit - a staff in hand that heals allies and shoots
     * holy arrows, Holy Magic Shell, Angel Ray, chains of light, Genesis, Heaven's Door (BishopArts).
     */
    BISHOP(32.0D);

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

    /** Has something on the ultimate key (V): since M18 every suit does. */
    public boolean hasUltimate() {
        return true;
    }

    /**
     * M18: the weapon that appears in the wearer's hand with a full chestplate on (SuitWeaponItem), or null for the
     * suits that fight bare-handed (palms, fists, cards).
     */
    public SuitWeaponItem.Kind weapon() {
        return switch (this) {
            case HERO -> SuitWeaponItem.Kind.MASTER_SWORD;
            case SWORDSMAN -> SuitWeaponItem.Kind.TRUNKS_SWORD;
            case BISHOP -> SuitWeaponItem.Kind.BISHOP_STAFF;
            case CANNONEER -> SuitWeaponItem.Kind.CANNON;
            default -> null;
        };
    }

    /** The primary fires from an empty hand (true) or from the weapon item (false). */
    public boolean handsFree() {
        return weapon() == null;
    }

    /** The flight speed against a Mark 1 (the Hulkbuster lumbers, the scrap suit's boiler barely lifts it). */
    public float flightFactor() {
        return switch (this) {
            case HULKBUSTER -> SuitTuning.HULKBUSTER_FLIGHT_FACTOR;
            case SCRAP -> SuitTuning.SCRAP_FLIGHT_FACTOR;
            default -> 1.0F;
        };
    }
}
