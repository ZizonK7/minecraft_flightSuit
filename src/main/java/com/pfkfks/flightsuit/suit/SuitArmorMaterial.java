package com.pfkfks.flightsuit.suit;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Diamond-tier protection; durability matters later for forced ejection (DESIGN.md 4-2). */
public enum SuitArmorMaterial implements ArmorMaterial {
    // The starter suit (survival balance check after the M16 test): between iron (15) and diamond (20) - it's
    // cheaper than diamond armour and flies; the later Marks are the step up.
    MARK_1("flightsuit:mark_1", 33, 2, 7, 5, 2, 10, 1.0F, 0.0F),
    /** Stealth: lighter plating, a little less protection. */
    MARK_2("flightsuit:mark_2", 28, 2, 7, 5, 2, 12, 1.0F, 0.0F),
    /** Phantom: a light, tailored coat - quick rather than tough. */
    MARK_3("flightsuit:mark_3", 30, 3, 7, 6, 3, 15, 1.5F, 0.0F),
    /** Hero of Twilight: plate over chainmail - a frontline swordsman, sturdier than Mark 1. */
    MARK_4("flightsuit:mark_4", 35, 3, 8, 6, 3, 12, 2.5F, 0.1F),
    /** Trunks Mark 5: a light fighter's suit, quick rather than tough. */
    MARK_5("flightsuit:mark_5", 30, 3, 7, 6, 3, 14, 1.5F, 0.0F),
    /** Hulkbuster Mark 44: the heaviest plate there is - and it doesn't get knocked about. */
    MARK_44("flightsuit:mark_44", 45, 4, 10, 8, 4, 10, 4.0F, 0.4F),
    /** Nanotech Mark 50 (the reward for beating Thanos): the toughest plating, and it repairs itself (NanotechHandler). */
    MARK_50("flightsuit:mark_50", 40, 4, 9, 7, 4, 15, 3.0F, 0.15F),
    /** EDITH glasses: no protection, zero durability = unbreakable. */
    EDITH("flightsuit:edith", 0, 0, 0, 0, 0, 15, 0.0F, 0.0F);

    private final String name;
    private final int durabilityMultiplier;
    private final int helmet;
    private final int chest;
    private final int legs;
    private final int boots;
    private final int enchantability;
    private final float toughness;
    private final float knockbackResistance;

    SuitArmorMaterial(String name, int durabilityMultiplier, int helmet, int chest, int legs, int boots,
                      int enchantability, float toughness, float knockbackResistance) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.helmet = helmet;
        this.chest = chest;
        this.legs = legs;
        this.boots = boots;
        this.enchantability = enchantability;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
        return base * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> helmet;
            case CHESTPLATE -> chest;
            case LEGGINGS -> legs;
            case BOOTS -> boots;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this == EDITH ? SoundEvents.ARMOR_EQUIP_GENERIC : SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.IRON_INGOT);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
