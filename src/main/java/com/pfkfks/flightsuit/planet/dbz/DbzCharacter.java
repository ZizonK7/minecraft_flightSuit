package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Who you meet on Dragon Ball Earth (DESIGN.md 4-16, M15): Bulma and Goku (Goku also fights beside you), the
 * Saiyans of chapter 1 and their Saibamen. Power levels are what the scouter reads.
 */
public enum DbzCharacter {
    BULMA("bulma", Role.NPC, 40.0D, 0.0D, 0.28D, 1.0F, 12),
    GOKU("goku", Role.ALLY, 220.0D, 10.0D, 0.34D, 1.0F, 8_000),
    RADITZ("raditz", Role.BOSS, 280.0D, 9.0D, 0.33D, 1.05F, 1_500),
    NAPPA("nappa", Role.BOSS, 420.0D, 13.0D, 0.30D, 1.2F, 4_000),
    VEGETA("vegeta", Role.BOSS, 600.0D, 12.0D, 0.36D, 0.95F, 18_000),
    SAIBAMAN("saibaman", Role.MINION, 24.0D, 4.0D, 0.36D, 0.85F, 1_200);

    public enum Role { NPC, ALLY, BOSS, MINION }

    private static final DbzCharacter[] VALUES = values();

    private final String id;
    private final Role role;
    private final double health;
    private final double damage;
    private final double speed;
    private final float scale;
    private final int power;

    DbzCharacter(String id, Role role, double health, double damage, double speed, float scale, int power) {
        this.id = id;
        this.role = role;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.scale = scale;
        this.power = power;
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public double speed() {
        return speed;
    }

    public float scale() {
        return scale;
    }

    /** What the scouter reads. */
    public int power() {
        return power;
    }

    public boolean isFoe() {
        return role == Role.BOSS || role == Role.MINION;
    }

    public Component displayName() {
        return Component.translatable("dbz.flightsuit." + id).withStyle(isFoe() ? ChatFormatting.RED : ChatFormatting.GOLD);
    }

    /** "<who>: <line>". */
    public Component line(String key, Object... args) {
        return Component.translatable("general.flightsuit.says", displayName(), Component.translatable("dbz.flightsuit." + id + "." + key, args));
    }

    public static DbzCharacter byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BULMA;
    }
}
