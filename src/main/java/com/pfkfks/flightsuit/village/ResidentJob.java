package com.pfkfks.flightsuit.village;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A resident's job (DESIGN.md 4-12). The id doubles as the skin name in textures/entity/resident/. Jobs whose
 * work is not built yet can still be given (the resident wears the clothes and lives in the village).
 */
public enum ResidentJob {
    NONE("unemployed", false),
    FARMER("farmer", true),
    RANCHER("rancher", false),
    COOK("cook", false),
    ARCHITECT("architect", true),
    TEACHER("teacher", false),
    DOCTOR("doctor", false),
    GUARD("guard", true),
    BLACKSMITH("blacksmith", false),
    MERCHANT("merchant", false),
    MUSICIAN("musician", false),
    SOLDIER("soldier", true);

    private static final ResidentJob[] VALUES = values();

    private final String id;
    private final boolean works;

    ResidentJob(String id, boolean works) {
        this.id = id;
        this.works = works;
    }

    public String id() {
        return id;
    }

    /** Whether this job already has its work (M9: farmer, architect, guard, soldier). */
    public boolean works() {
        return works;
    }

    /**
     * Guards and soldiers stand and fight instead of running for shelter. Guards keep the village (patrol,
     * watch); soldiers are the army (DESIGN.md 4-12) - until there are campaigns to march on, they rally to
     * wherever the alarm was raised.
     */
    public boolean isFighter() {
        return this == GUARD || this == SOLDIER;
    }

    public String translationKey() {
        return "job.flightsuit." + id;
    }

    public static ResidentJob byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    /** What they carry, so the job reads at a glance (the musician especially - the skin has no instrument). */
    public ItemStack tool() {
        return switch (this) {
            case FARMER -> new ItemStack(Items.IRON_HOE);
            case GUARD, SOLDIER -> new ItemStack(Items.IRON_SWORD);
            case MUSICIAN -> new ItemStack(Items.GOAT_HORN);
            case TEACHER -> new ItemStack(Items.BOOK);
            case MERCHANT -> new ItemStack(Items.EMERALD);
            case COOK -> new ItemStack(Items.BREAD);
            case RANCHER -> new ItemStack(Items.WHEAT);
            default -> ItemStack.EMPTY;
        };
    }
}
