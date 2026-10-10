package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Who you meet on Dragon Ball Earth and Namek (DESIGN.md 4-16, M15; chapters 2-4 after the M16 test): Bulma and
 * Goku (Goku also fights beside you), the Saiyans of chapter 1 and their Saibamen; Dende, Frieza's men, the Ginyu
 * Force and Frieza on Namek; Trunks, the androids, Cell and his juniors; Majin Buu and Kid Buu. Power levels are
 * what the scouter reads (the androids have none). Added at the end: the type is saved by its ordinal.
 */
public enum DbzCharacter {
    BULMA("bulma", Role.NPC, 40.0D, 0.0D, 0.28D, 1.0F, 12),
    GOKU("goku", Role.ALLY, 220.0D, 10.0D, 0.34D, 1.0F, 8_000),
    RADITZ("raditz", Role.BOSS, 280.0D, 9.0D, 0.33D, 1.05F, 1_500),
    NAPPA("nappa", Role.BOSS, 420.0D, 13.0D, 0.30D, 1.2F, 4_000),
    VEGETA("vegeta", Role.BOSS, 600.0D, 12.0D, 0.36D, 0.95F, 18_000),
    SAIBAMAN("saibaman", Role.MINION, 24.0D, 4.0D, 0.36D, 0.85F, 1_200),
    // Chapter 2, Namek.
    DENDE("dende", Role.NPC, 40.0D, 0.0D, 0.26D, 0.8F, 1_000),
    FRIEZA_SOLDIER("frieza_soldier", Role.MINION, 32.0D, 5.0D, 0.30D, 1.0F, 1_100),
    DODORIA("dodoria", Role.BOSS, 420.0D, 12.0D, 0.28D, 1.25F, 22_000),
    ZARBON("zarbon", Role.BOSS, 380.0D, 11.0D, 0.34D, 1.0F, 23_000),
    GULDO("guldo", Role.BOSS, 300.0D, 8.0D, 0.26D, 0.85F, 13_500),
    RECOOME("recoome", Role.BOSS, 600.0D, 14.0D, 0.28D, 1.25F, 40_000),
    BURTER("burter", Role.BOSS, 380.0D, 10.0D, 0.42D, 1.15F, 42_000),
    JEICE("jeice", Role.BOSS, 380.0D, 11.0D, 0.36D, 1.0F, 42_000),
    GINYU("ginyu", Role.BOSS, 520.0D, 12.0D, 0.33D, 1.05F, 120_000),
    FRIEZA("frieza", Role.BOSS, 900.0D, 16.0D, 0.36D, 0.95F, 120_000_000),
    // Chapter 3, the androids and Cell.
    TRUNKS("trunks", Role.ALLY, 320.0D, 13.0D, 0.36D, 1.0F, 1_000_000),
    ANDROID_17("android_17", Role.BOSS, 640.0D, 13.0D, 0.36D, 1.0F, 0),
    ANDROID_18("android_18", Role.BOSS, 600.0D, 13.0D, 0.38D, 0.95F, 0),
    CELL_JR("cell_jr", Role.MINION, 70.0D, 8.0D, 0.40D, 0.7F, 300_000),
    CELL("cell", Role.BOSS, 1400.0D, 18.0D, 0.36D, 1.1F, 900_000_000),
    // Chapter 4, Majin Buu.
    MAJIN_BUU("majin_buu", Role.BOSS, 1600.0D, 16.0D, 0.30D, 1.3F, 1_000_000_000),
    KID_BUU("kid_buu", Role.BOSS, 1800.0D, 20.0D, 0.42D, 0.9F, 1_500_000_000);

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
