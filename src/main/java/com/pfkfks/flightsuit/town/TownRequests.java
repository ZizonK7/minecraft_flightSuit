package com.pfkfks.flightsuit.town;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The small favours the townsfolk ask of a visitor: something their work needs (seed and bone meal for a farmer,
 * coal for the smith, redstone for a scientist), paid in the town's money (TownTrades.money) - and it wins the
 * town's goodwill. Rolled once when they appear (most people have one). Rewards below are in emeralds' worth and
 * turned into coins (x4) or dollars (x5).
 */
public final class TownRequests {
    private record Want(Item item, int count, int emeralds) {
    }

    private TownRequests() {
    }

    private static Want[] wants(TownRole role) {
        return switch (role) {
            case FORT_FARMER -> new Want[]{new Want(Items.BONE_MEAL, 12, 2), new Want(Items.WHEAT_SEEDS, 24, 1), new Want(Items.IRON_HOE, 1, 3)};
            case FORT_SMITH -> new Want[]{new Want(Items.COAL, 16, 3), new Want(Items.IRON_INGOT, 8, 4), new Want(Items.CHARCOAL, 12, 2)};
            case FORT_COOK -> new Want[]{new Want(Items.BEEF, 6, 2), new Want(Items.SUGAR, 8, 2), new Want(Items.EGG, 8, 2)};
            case FORT_ELDER -> new Want[]{new Want(Items.BOOK, 2, 3), new Want(Items.PAPER, 12, 2), new Want(Items.HONEY_BOTTLE, 2, 3)};
            case FORT_CHILD -> new Want[]{new Want(Items.APPLE, 4, 1), new Want(Items.COOKIE, 6, 1), new Want(Items.STRING, 6, 1)};
            case CITY_WORKER -> new Want[]{new Want(Items.PAPER, 16, 2), new Want(Items.COCOA_BEANS, 8, 2), new Want(Items.BOOK, 2, 3)};
            case CITY_SCIENTIST -> new Want[]{new Want(Items.REDSTONE, 16, 3), new Want(Items.AMETHYST_SHARD, 6, 4), new Want(Items.GLOWSTONE_DUST, 8, 3)};
            case CITY_POLICE -> new Want[]{new Want(Items.IRON_INGOT, 4, 2), new Want(Items.COOKED_BEEF, 4, 2)};
            case CITY_CITIZEN -> new Want[]{new Want(Items.POPPY, 6, 1), new Want(Items.CAKE, 1, 3), new Want(Items.SWEET_BERRIES, 12, 1)};
            case CITY_CHILD -> new Want[]{new Want(Items.COOKIE, 6, 1), new Want(Items.SLIME_BALL, 2, 2), new Want(Items.GLOW_BERRIES, 6, 1)};
            case DBZ_CITIZEN -> new Want[]{new Want(Items.CAKE, 1, 3), new Want(Items.COPPER_INGOT, 8, 2), new Want(Items.GLASS, 16, 2)};
            case DBZ_CHILD -> new Want[]{new Want(Items.COOKIE, 6, 1), new Want(Items.FIREWORK_ROCKET, 3, 2), new Want(Items.MELON_SLICE, 8, 1)};
            // Namekians drink only water: they ask for it, and for seeds for the Ajisa groves.
            case NAMEKIAN -> new Want[]{new Want(Items.WATER_BUCKET, 1, 2), new Want(Items.OAK_SAPLING, 4, 2), new Want(Items.BONE_MEAL, 12, 2)};
            default -> new Want[0];
        };
    }

    /** A request for this role, or none (about a third have nothing to ask). */
    public static @Nullable TownsfolkEntity.Request roll(TownRole role, RandomSource random) {
        return roll(role, random, role.isCity() ? 5 : 4);
    }

    /** The same, paid at {@code perEmerald} of the town's money (zeni: 5). */
    public static @Nullable TownsfolkEntity.Request roll(TownRole role, RandomSource random, int perEmerald) {
        Want[] wants = wants(role);
        if (wants.length == 0 || random.nextInt(3) == 0) {
            return null;
        }
        Want want = wants[random.nextInt(wants.length)];
        return new TownsfolkEntity.Request(want.item(), want.count(), want.emeralds() * perEmerald);
    }
}
