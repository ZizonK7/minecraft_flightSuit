package com.pfkfks.flightsuit.suit;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Diamond-tier protection; durability matters later for forced ejection (DESIGN.md 4-2). */
public enum SuitArmorMaterial implements ArmorMaterial {
    MARK_1("flightsuit:mark_1", 33, 3, 8, 6, 3, 10, 2.0F, 0.1F),
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
