package com.pfkfks.flightsuit.thief;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Batman's crew (DESIGN.md 4-14, M14): wandering thieves with no home. What each does once spotted -
 * Batman smoke + EMP then runs, Catwoman bolts with the best of it, Robin fights a little before he goes.
 */
public enum ThiefType {
    BATMAN("batman", 60.0D, 0.30D, 2),
    CATWOMAN("catwoman", 40.0D, 0.34D, 3),
    ROBIN("robin", 45.0D, 0.32D, 2);

    private static final ThiefType[] VALUES = values();

    private final String id;
    private final double health;
    private final double speed;
    private final int stacksPerChest;

    ThiefType(String id, double health, double speed, int stacksPerChest) {
        this.id = id;
        this.health = health;
        this.speed = speed;
        this.stacksPerChest = stacksPerChest;
    }

    public String id() {
        return id;
    }

    public double health() {
        return health;
    }

    public double speed() {
        return speed;
    }

    /** How many stacks (half of each) they lift from one chest. Catwoman goes for the valuables only. */
    public int stacksPerChest() {
        return stacksPerChest;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Component displayName() {
        return Component.translatable("thief.flightsuit." + id).withStyle(ChatFormatting.DARK_PURPLE);
    }

    public static ThiefType byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BATMAN;
    }

    /** "배트맨 · 로빈" for a crew bitmask. */
    public static Component crewName(int mask) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean first = true;
        for (ThiefType type : VALUES) {
            if ((mask & type.bit()) != 0) {
                if (!first) {
                    out.append(" · ");
                }
                out.append(type.displayName());
                first = false;
            }
        }
        return out;
    }
}
