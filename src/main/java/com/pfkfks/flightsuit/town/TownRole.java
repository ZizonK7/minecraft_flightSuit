package com.pfkfks.flightsuit.town;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Who lives in the Three Kingdoms fortress towns and in Hero City (after the M16 test: "make them real towns,
 * so I can go in and do things there"). The id is also the skin (textures/entity/townsfolk/<id>.png).
 */
public enum TownRole {
    FORT_FARMER("fort_farmer", Place.FIELD),
    FORT_MERCHANT("fort_merchant", Place.SHOP),
    FORT_SMITH("fort_smith", Place.SMITHY),
    FORT_COOK("fort_cook", Place.KITCHEN),
    FORT_ELDER("fort_elder", Place.SQUARE),
    FORT_CHILD("fort_child", Place.PLAY),
    CITY_WORKER("city_worker", Place.OFFICE),
    CITY_SCIENTIST("city_scientist", Place.LAB),
    CITY_POLICE("city_police", Place.PATROL),
    CITY_SHOPKEEPER("city_shopkeeper", Place.SHOP),
    CITY_CITIZEN("city_citizen", Place.SQUARE),
    CITY_CHILD("city_child", Place.PLAY);

    /** Where their day is spent (the town's plan has spots of each kind). */
    public enum Place { FIELD, SHOP, SMITHY, KITCHEN, SQUARE, PLAY, OFFICE, LAB, PATROL }

    private static final TownRole[] VALUES = values();

    private final String id;
    private final Place place;

    TownRole(String id, Place place) {
        this.id = id;
        this.place = place;
    }

    public String id() {
        return id;
    }

    public Place place() {
        return place;
    }

    public boolean isChild() {
        return this == FORT_CHILD || this == CITY_CHILD;
    }

    /** Opens the trading screen when spoken to. */
    public boolean trades() {
        return place == Place.SHOP;
    }

    public boolean isCity() {
        return name().startsWith("CITY_");
    }

    public Component displayName() {
        return Component.translatable("town.flightsuit.role." + id);
    }

    /** What they carry at work, so the job reads at a glance. */
    public ItemStack tool() {
        return switch (this) {
            case FORT_FARMER -> new ItemStack(Items.IRON_HOE);
            case FORT_SMITH -> new ItemStack(Items.IRON_AXE);
            case FORT_COOK -> new ItemStack(Items.BREAD);
            case FORT_MERCHANT -> new ItemStack(com.pfkfks.flightsuit.registry.ModItems.WUZHU_COIN.get());
            case CITY_SHOPKEEPER -> new ItemStack(com.pfkfks.flightsuit.registry.ModItems.DOLLAR.get());
            case FORT_ELDER -> new ItemStack(Items.STICK);
            case CITY_SCIENTIST -> new ItemStack(Items.BOOK);
            case CITY_WORKER -> new ItemStack(Items.PAPER);
            default -> ItemStack.EMPTY;
        };
    }

    public static TownRole byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FORT_ELDER;
    }
}
