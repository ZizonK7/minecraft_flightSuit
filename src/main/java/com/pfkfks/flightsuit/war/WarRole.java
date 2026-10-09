package com.pfkfks.flightsuit.war;

/** What a Three Kingdoms soldier or general is doing (M12), which decides who they fight. */
public enum WarRole {
    /** Marching on a target: the player's village (M10 raid), or another kingdom's fortress (a storm). */
    RAID,
    /** Keeping their own kingdom's fortress. Not saved: the fortress fills up again whenever someone comes. */
    GARRISON,
    /** On the player's side: support troops (지원군) or an allied army on an invasion. */
    ALLY;

    private static final WarRole[] VALUES = values();

    public static WarRole byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : RAID;
    }
}
