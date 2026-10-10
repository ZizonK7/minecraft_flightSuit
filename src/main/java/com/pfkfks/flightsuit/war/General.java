package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Named generals (DESIGN.md 4-11 장수 표). Each leads the last wave of a raid by their kingdom and has their
 * own skills. Each carries their own weapon (display items, after the M13 test: 청룡언월도, 장팔사모, ...) - it's
 * for looks and adds no damage of its own (the weapon bonus is 0; it was the vanilla weapons' bonus before).
 */
public enum General {
    GUAN_YU("guan_yu", Kingdom.SHU, 160.0D, 11.0D, 0.30D, 0.20F, () -> ModItems.GREEN_DRAGON_BLADE.get(), 0.0D, Skill.WHIRLWIND, Skill.DUEL),
    ZHANG_FEI("zhang_fei", Kingdom.SHU, 150.0D, 11.0D, 0.30D, 0.10F, () -> ModItems.SERPENT_SPEAR.get(), 0.0D, Skill.ROAR, Skill.CHARGE),
    XIAHOU_DUN("xiahou_dun", Kingdom.WEI, 150.0D, 10.0D, 0.30D, 0.15F, () -> ModItems.XIAHOU_BROADSWORD.get(), 0.0D, Skill.CHARGE, Skill.ENRAGE),
    GAN_NING("gan_ning", Kingdom.WU, 120.0D, 9.0D, 0.36D, 0.0F, () -> ModItems.BELLED_SABRE.get(), 0.0D, Skill.AMBUSH, Skill.WHIRLWIND),
    // The rulers (M12): they stay in their fortress, rally the men around them, and are who you talk to.
    LIU_BEI("liu_bei", Kingdom.SHU, 120.0D, 7.0D, 0.28D, 0.15F, () -> ModItems.TWIN_SWORDS.get(), 0.0D, Skill.RALLY),
    CAO_CAO("cao_cao", Kingdom.WEI, 130.0D, 8.0D, 0.28D, 0.15F, () -> ModItems.YITIAN_SWORD.get(), 0.0D, Skill.RALLY),
    SUN_QUAN("sun_quan", Kingdom.WU, 120.0D, 7.0D, 0.28D, 0.15F, () -> ModItems.GU_DING_DAO.get(), 0.0D, Skill.RALLY);

    public enum Skill {
        /** 회전베기: hits everything around. */
        WHIRLWIND,
        /** 일기토: calls a player out; the soldiers leave that player to him, and losing it breaks the army. */
        DUEL,
        /** 장판교 고함: stuns everyone nearby and shatters glass and doors. */
        ROAR,
        /** 돌진: dashes into the target. */
        CHARGE,
        /** Hurt below half: hits harder from then on. */
        ENRAGE,
        /** 기습: slips behind the target. */
        AMBUSH,
        /** 지휘: the men around fight harder and heal (the rulers). */
        RALLY
    }

    private static final General[] VALUES = values();

    private final String id;
    private final Kingdom kingdom;
    private final double health;
    private final double damage;
    private final double speed;
    /** Added to the raid's will to fight while this general leads it. */
    private final float resolveBonus;
    /** Looked up late: the mod's own items aren't registered yet when the enum loads. */
    private final Supplier<Item> weapon;
    private final double weaponBonus;
    private final Set<Skill> skills;

    General(String id, Kingdom kingdom, double health, double damage, double speed, float resolveBonus,
            Supplier<Item> weapon, double weaponBonus, Skill... skills) {
        this.id = id;
        this.kingdom = kingdom;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.resolveBonus = resolveBonus;
        this.weapon = weapon;
        this.weaponBonus = weaponBonus;
        this.skills = EnumSet.noneOf(Skill.class);
        java.util.Collections.addAll(this.skills, skills);
    }

    public String id() {
        return id;
    }

    public Kingdom kingdom() {
        return kingdom;
    }

    public double health() {
        return health;
    }

    /** Base attack damage, before the weapon's own bonus is added back on by the game. */
    public double baseDamage() {
        return Math.max(1.0D, damage - weaponBonus);
    }

    public double speed() {
        return speed;
    }

    public float resolveBonus() {
        return resolveBonus;
    }

    public ItemStack weapon() {
        return new ItemStack(weapon.get());
    }

    /** A ruler: never marches out with a raid; the one you deal with (DESIGN 4-11 외교와 의뢰). */
    public boolean isLeader() {
        return skills.contains(Skill.RALLY);
    }

    public static General leaderOf(Kingdom kingdom) {
        for (General general : VALUES) {
            if (general.kingdom == kingdom && general.isLeader()) {
                return general;
            }
        }
        return null;
    }

    public boolean has(Skill skill) {
        return skills.contains(skill);
    }

    public Component displayName() {
        return Component.translatable("general.flightsuit." + id).withStyle(kingdom.color());
    }

    /** "<general>: <line>" - what they shout. */
    public Component line(String key, Object... args) {
        return Component.translatable("general.flightsuit.says", displayName(),
                Component.translatable("general.flightsuit." + id + "." + key, args));
    }

    public static General byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : GUAN_YU;
    }

    public static General byName(String id) {
        for (General general : VALUES) {
            if (general.id.equals(id)) {
                return general;
            }
        }
        return null;
    }
}
