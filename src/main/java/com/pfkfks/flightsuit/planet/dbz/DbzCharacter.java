package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Who you meet on Dragon Ball Earth and Namek (DESIGN.md 4-16, M15; chapters 2-4 after the M16 test; the M17 cast):
 * Bulma, Goku and his friends (Gohan, Piccolo, Krillin, Yamcha, Tien), the Saiyans of chapter 1 and their Saibamen;
 * Dende, Frieza's men, the Ginyu Force and Frieza on Namek; Trunks, the androids, Cell (three forms) and his juniors;
 * Majin Buu (fat, Super, absorbed) and Kid Buu; Yajirobe and Mr. Satan for their moments. Some are another form of
 * the same person (GOKU_SSJ, FRIEZA_FINAL...): they speak with the same voice ({@link #voice}).
 * Power levels are what the scouter reads (the androids have none). Since M17 saved by name, in story order here;
 * {@link #byLegacyId} reads the old ordinals.
 */
public enum DbzCharacter {
    BULMA("bulma", Role.NPC, 40.0D, 0.0D, 0.28D, 1.0F, 12),
    GOKU("goku", Role.ALLY, 220.0D, 10.0D, 0.34D, 1.0F, 8_000),
    /** Goku after Krillin's death on Namek (chapter 2's turn), and from chapter 3 on. */
    GOKU_SSJ("goku_ssj", "goku", Role.ALLY, 320.0D, 16.0D, 0.38D, 1.0F, 150_000_000),
    GOHAN_KID("gohan_kid", "gohan", Role.ALLY, 120.0D, 6.0D, 0.34D, 0.75F, 1_307),
    GOHAN_TEEN("gohan_teen", "gohan", Role.ALLY, 260.0D, 12.0D, 0.36D, 0.95F, 200_000_000),
    /** Gohan awakened at the Cell Games (Super Saiyan 2). */
    GOHAN_TEEN_SSJ2("gohan_teen_ssj2", "gohan", Role.ALLY, 380.0D, 20.0D, 0.40D, 0.95F, 900_000_000),
    PICCOLO("piccolo", Role.ALLY, 260.0D, 11.0D, 0.34D, 1.1F, 3_500),
    KRILLIN("krillin", Role.ALLY, 160.0D, 8.0D, 0.34D, 0.85F, 1_770),
    YAMCHA("yamcha", Role.ALLY, 140.0D, 7.0D, 0.34D, 1.0F, 1_480),
    TIEN("tien", Role.ALLY, 170.0D, 9.0D, 0.34D, 1.05F, 1_830),
    YAJIROBE("yajirobe", Role.NPC, 60.0D, 4.0D, 0.30D, 1.0F, 970),
    MR_SATAN("mr_satan", Role.NPC, 40.0D, 0.0D, 0.30D, 1.05F, 50),
    RADITZ("raditz", Role.BOSS, 280.0D, 9.0D, 0.33D, 1.1F, 1_500),
    NAPPA("nappa", Role.BOSS, 420.0D, 13.0D, 0.30D, 1.3F, 4_000),
    VEGETA("vegeta", Role.BOSS, 600.0D, 12.0D, 0.36D, 0.95F, 18_000),
    /** Vegeta as a Great Ape: its own model (OozaruModel), four times the size. */
    OOZARU_VEGETA("oozaru_vegeta", "vegeta", Role.BOSS, 900.0D, 18.0D, 0.30D, 4.0F, 180_000),
    SAIBAMAN("saibaman", Role.MINION, 24.0D, 4.0D, 0.36D, 0.85F, 1_200),
    // Chapter 2, Namek.
    DENDE("dende", Role.NPC, 40.0D, 0.0D, 0.26D, 0.8F, 1_000),
    FRIEZA_SOLDIER("frieza_soldier", Role.MINION, 32.0D, 5.0D, 0.30D, 1.0F, 1_100),
    DODORIA("dodoria", Role.BOSS, 420.0D, 12.0D, 0.28D, 1.3F, 22_000),
    ZARBON("zarbon", Role.BOSS, 380.0D, 11.0D, 0.34D, 1.0F, 23_000),
    GULDO("guldo", Role.BOSS, 300.0D, 8.0D, 0.26D, 0.85F, 13_500),
    RECOOME("recoome", Role.BOSS, 600.0D, 14.0D, 0.28D, 1.3F, 40_000),
    BURTER("burter", Role.BOSS, 380.0D, 10.0D, 0.42D, 1.2F, 42_000),
    JEICE("jeice", Role.BOSS, 380.0D, 11.0D, 0.36D, 1.0F, 42_000),
    GINYU("ginyu", Role.BOSS, 520.0D, 12.0D, 0.33D, 1.15F, 120_000),
    FRIEZA("frieza", Role.BOSS, 900.0D, 16.0D, 0.36D, 1.0F, 1_000_000),
    /** Frieza's final form (the transformation at half health). */
    FRIEZA_FINAL("frieza_final", "frieza", Role.BOSS, 900.0D, 20.0D, 0.38D, 0.9F, 120_000_000),
    // Chapter 3, the androids and Cell.
    TRUNKS("trunks", Role.ALLY, 320.0D, 13.0D, 0.36D, 1.0F, 1_000_000),
    ANDROID_16("android_16", Role.ALLY, 300.0D, 12.0D, 0.32D, 1.25F, 0),
    ANDROID_17("android_17", Role.BOSS, 640.0D, 13.0D, 0.36D, 1.0F, 0),
    ANDROID_18("android_18", Role.BOSS, 600.0D, 13.0D, 0.38D, 0.95F, 0),
    CELL_JR("cell_jr", Role.MINION, 70.0D, 8.0D, 0.40D, 0.7F, 300_000),
    CELL_IMPERFECT("cell_imperfect", "cell", Role.BOSS, 700.0D, 13.0D, 0.34D, 1.15F, 300_000_000),
    CELL_SEMI("cell_semi", "cell", Role.BOSS, 1_000.0D, 15.0D, 0.32D, 1.3F, 600_000_000),
    CELL("cell", Role.BOSS, 1400.0D, 18.0D, 0.36D, 1.2F, 900_000_000),
    // Chapter 4, Majin Buu.
    MAJIN_BUU("majin_buu", Role.BOSS, 1600.0D, 16.0D, 0.30D, 1.3F, 1_000_000_000),
    /** Evil Buu after eating the fat one. */
    SUPER_BUU("super_buu", "majin_buu", Role.BOSS, 1700.0D, 18.0D, 0.40D, 1.15F, 1_200_000_000),
    /** Super Buu after absorbing Gohan and Piccolo: 1.3x health and damage. */
    SUPER_BUU_ABSORBED("super_buu_absorbed", "majin_buu", Role.BOSS, 2210.0D, 23.4D, 0.40D, 1.2F, 1_600_000_000),
    KID_BUU("kid_buu", Role.BOSS, 1800.0D, 20.0D, 0.42D, 0.9F, 1_500_000_000);

    public enum Role { NPC, ALLY, BOSS, MINION }

    private static final DbzCharacter[] VALUES = values();
    /** The order before M17 (saved by ordinal then). */
    private static final DbzCharacter[] LEGACY = {BULMA, GOKU, RADITZ, NAPPA, VEGETA, SAIBAMAN, DENDE, FRIEZA_SOLDIER, DODORIA, ZARBON,
            GULDO, RECOOME, BURTER, JEICE, GINYU, FRIEZA, TRUNKS, ANDROID_17, ANDROID_18, CELL_JR, CELL, MAJIN_BUU, KID_BUU};

    private final String id;
    private final String voice;
    private final Role role;
    private final double health;
    private final double damage;
    private final double speed;
    private final float scale;
    private final int power;

    DbzCharacter(String id, Role role, double health, double damage, double speed, float scale, int power) {
        this(id, id, role, health, damage, speed, scale, power);
    }

    DbzCharacter(String id, String voice, Role role, double health, double damage, double speed, float scale, int power) {
        this.id = id;
        this.voice = voice;
        this.role = role;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.scale = scale;
        this.power = power;
    }

    /** Its skin (textures/entity/dbz/<id>.png) and name key. */
    public String id() {
        return id;
    }

    /** Whose lines it speaks (another form of the same person shares them). */
    public String voice() {
        return voice;
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

    /** Body size against a player's, for both the model and the hitbox. */
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

    /** "<who>: <line>" (dbz.flightsuit.<voice>.<key>). */
    public Component line(String key, Object... args) {
        return Component.translatable("general.flightsuit.says", displayName(), Component.translatable("dbz.flightsuit." + voice + "." + key, args));
    }

    /** By ordinal - for the synced entity data only (both sides have the same list). */
    public static DbzCharacter byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BULMA;
    }

    public static DbzCharacter byName(String name) {
        for (DbzCharacter character : VALUES) {
            if (character.name().equalsIgnoreCase(name)) {
                return character;
            }
        }
        return BULMA;
    }

    /** An ordinal saved before M17. */
    public static DbzCharacter byLegacyId(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY.length ? LEGACY[ordinal] : BULMA;
    }
}
