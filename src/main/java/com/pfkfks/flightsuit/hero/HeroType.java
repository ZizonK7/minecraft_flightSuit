package com.pfkfks.flightsuit.hero;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.pfkfks.flightsuit.registry.ModItems;

import java.util.function.Supplier;

/**
 * Who lives in Hero City (DESIGN.md 4-13, M13). Seven heroes - Captain leads, Iron Man helps with the suits -
 * and S.H.I.E.L.D. agents as the city's guards. Each hero has one signature move (CityHeroEntity.customServerAiStep).
 */
public enum HeroType {
    CAPTAIN("captain", 160.0D, 9.0D, 0.32D, 1.0F, () -> ModItems.CAPTAIN_SHIELD.get(), false),
    IRON_MAN("iron_man", 150.0D, 8.0D, 0.30D, 1.0F, null, true),
    THOR("thor", 200.0D, 12.0D, 0.30D, 1.05F, () -> ModItems.MJOLNIR.get(), false),
    HULK("hulk", 360.0D, 16.0D, 0.32D, 1.6F, null, false),
    SPIDER_MAN("spider_man", 110.0D, 7.0D, 0.38D, 0.95F, null, false),
    BLACK_WIDOW("black_widow", 100.0D, 8.0D, 0.36D, 0.95F, () -> ModItems.WIDOW_BATON.get(), false),
    HAWKEYE("hawkeye", 100.0D, 6.0D, 0.32D, 1.0F, () -> ModItems.HAWKEYE_BOW.get(), true),
    // Agents shoot (S.H.I.E.L.D. pistols, after the M13 test).
    AGENT("agent", 30.0D, 5.0D, 0.30D, 1.0F, () -> ModItems.SHIELD_PISTOL.get(), true);

    private static final HeroType[] VALUES = values();

    private final String id;
    private final double health;
    private final double damage;
    private final double speed;
    private final float scale;
    /** Looked up late: the mod's own items (Mjolnir) aren't registered yet when the enum loads. */
    private final Supplier<Item> held;
    private final boolean ranged;

    HeroType(String id, double health, double damage, double speed, float scale, Supplier<Item> held, boolean ranged) {
        this.id = id;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.scale = scale;
        this.held = held;
        this.ranged = ranged;
    }

    public String id() {
        return id;
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

    /** Hulk is half again as big as everyone else. */
    public float scale() {
        return scale;
    }

    public ItemStack held() {
        return held == null ? ItemStack.EMPTY : new ItemStack(held.get());
    }

    /** Fights from a distance (Iron Man's repulsors, Hawkeye's bow, the agents' pistols). */
    public boolean ranged() {
        return ranged;
    }

    public boolean isHero() {
        return this != AGENT;
    }

    public Component displayName() {
        return Component.translatable("hero.flightsuit." + id).withStyle(this == AGENT ? ChatFormatting.GRAY : ChatFormatting.AQUA);
    }

    /** "<hero>: <line>". */
    public Component line(String key, Object... args) {
        return Component.translatable("general.flightsuit.says", displayName(), Component.translatable("hero.flightsuit." + id + "." + key, args));
    }

    public static HeroType byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : AGENT;
    }
}
