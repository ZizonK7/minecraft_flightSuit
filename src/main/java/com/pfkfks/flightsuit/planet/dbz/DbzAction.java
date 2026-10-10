package com.pfkfks.flightsuit.planet.dbz;

/**
 * What a Dragon Ball fighter's body is doing (M17): the server sets it (DbzFighterEntity.setAction - a fight move,
 * a cutscene's pose) and syncs it with the tick it started; the client's DbzFighterModel poses the limbs from it
 * and the time since. Sent by ordinal: add at the end.
 */
public enum DbzAction {
    IDLE(0),
    /** Flying: leaning forward, arms back. */
    FLY(0),
    PUNCH_L(6),
    PUNCH_R(6),
    KICK(8),
    /** Arms crossed in front. */
    GUARD(12),
    /** Both hands at one side, crouched, gathering a beam. */
    CHARGE(0),
    /** Both hands thrust forward, the beam leaving them. */
    FIRE(14),
    /** Thrown back by a hit. */
    HURT(8),
    /** Lying on the ground. */
    DOWN(0),
    /** Shaking, arms spread, power rising. */
    TRANSFORM(30),
    /** The Ginyu Force poses (each member his own, DbzFighterModel). */
    POSE_GINYU(0),
    /** Gohan's headbutt: the whole body a missile, head first. */
    HEADBUTT(10),
    /** Piccolo shielding someone: arms wrapped round, back turned. */
    CARRY(0),
    /** Arms raised high (the Spirit Bomb, a gathering crowd). */
    HANDS_UP(0);

    /** How long it plays before going back to IDLE (0 = held until changed). */
    private final int ticks;

    DbzAction(int ticks) {
        this.ticks = ticks;
    }

    public int ticks() {
        return ticks;
    }

    public static DbzAction byId(int id) {
        DbzAction[] values = values();
        return id >= 0 && id < values.length ? values[id] : IDLE;
    }
}
