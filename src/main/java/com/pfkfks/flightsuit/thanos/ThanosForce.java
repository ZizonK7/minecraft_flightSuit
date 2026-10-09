package com.pfkfks.flightsuit.thanos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Thanos and his forces (DESIGN.md 4-16, M16): the Chitauri foot soldiers, the Black Order - each guarding a
 * stone on Titan - Thanos himself, and Red Skull, the keeper of the Soul Stone on Vormir.
 */
public enum ThanosForce {
    CHITAURI("chitauri", Role.MINION, 32.0D, 5.0D, 0.30D, 1.0F, null),
    EBONY_MAW("ebony_maw", Role.BOSS, 260.0D, 6.0D, 0.28D, 1.0F, InfinityStone.SPACE),
    PROXIMA_MIDNIGHT("proxima_midnight", Role.BOSS, 300.0D, 11.0D, 0.36D, 1.05F, InfinityStone.REALITY),
    CORVUS_GLAIVE("corvus_glaive", Role.BOSS, 280.0D, 12.0D, 0.33D, 1.05F, InfinityStone.MIND),
    CULL_OBSIDIAN("cull_obsidian", Role.BOSS, 460.0D, 15.0D, 0.27D, 1.4F, InfinityStone.POWER),
    THANOS("thanos", Role.FINAL, 1200.0D, 16.0D, 0.30D, 1.35F, null),
    RED_SKULL("red_skull", Role.NPC, 40.0D, 0.0D, 0.2D, 1.0F, InfinityStone.SOUL);

    public enum Role { MINION, BOSS, FINAL, NPC }

    private static final ThanosForce[] VALUES = values();

    private final String id;
    private final Role role;
    private final double health;
    private final double damage;
    private final double speed;
    private final float scale;
    private final @Nullable InfinityStone stone;

    ThanosForce(String id, Role role, double health, double damage, double speed, float scale, @Nullable InfinityStone stone) {
        this.id = id;
        this.role = role;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.scale = scale;
        this.stone = stone;
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

    /** The stone this one guards on Titan (Red Skull: the Soul Stone). */
    public @Nullable InfinityStone stone() {
        return stone;
    }

    public boolean isBlackOrder() {
        return role == Role.BOSS;
    }

    public Component displayName() {
        return Component.translatable("thanos.flightsuit." + id).withStyle(role == Role.NPC ? ChatFormatting.DARK_RED : ChatFormatting.DARK_PURPLE);
    }

    public Component line(String key, Object... args) {
        return Component.translatable("general.flightsuit.says", displayName(), Component.translatable("thanos.flightsuit." + id + "." + key, args));
    }

    public static ThanosForce byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CHITAURI;
    }
}
