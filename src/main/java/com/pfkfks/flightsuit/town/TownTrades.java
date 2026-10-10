package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.war.Kingdom;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * What the towns' shops sell and buy (the vanilla trading screen), priced in the town's own money - wuzhu coins
 * in a fortress, dollars in Hero City (TownTrades.money). A fortress has a grain merchant, an arms dealer and a
 * dealer in the kingdom's own goods (who also changes emeralds); Hero City an electronics shop, a grocery, a
 * building supplies store and a tech store (which changes emeralds too). Restocked each visit.
 */
public final class TownTrades {
    private static final int USES = 12;

    private TownTrades() {
    }

    /** The town's money: wuzhu coins in the fortresses, dollars in Hero City. */
    public static Item money(boolean city) {
        return city ? ModItems.DOLLAR.get() : ModItems.WUZHU_COIN.get();
    }

    /** The money of a town by its id (TownsfolkEntity.town): zeni on Dragon Ball Earth and Namek (M17). */
    public static Item money(int town) {
        return town == TownsfolkEntity.WEST_CITY || town == TownsfolkEntity.NAMEK ? ModItems.ZENI.get() : money(town == TownsfolkEntity.HERO_CITY);
    }

    private static Item money;

    /** Pay {@code price} of the town's money for {@code count} of {@code item}. */
    private static MerchantOffer sell(Item item, int count, int price) {
        return new MerchantOffer(new ItemStack(money, price), new ItemStack(item, count), USES, 1, 0.05F);
    }

    /** They buy {@code count} of {@code item} for {@code paid} of the town's money. */
    private static MerchantOffer buy(Item item, int count, int paid) {
        return new MerchantOffer(new ItemStack(item, count), new ItemStack(money, paid), USES, 1, 0.05F);
    }

    /** Changing emeralds (the wider world's money) into the town's, and back at a loss. */
    private static void exchange(MerchantOffers offers, int perEmerald) {
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(money, perEmerald), 32, 1, 0.0F));
        offers.add(new MerchantOffer(new ItemStack(money, perEmerald + perEmerald / 2), new ItemStack(Items.EMERALD), 32, 1, 0.0F));
    }

    public static MerchantOffers offers(TownRole role, int kind, int town) {
        MerchantOffers offers = new MerchantOffers();
        money = money(town);
        if (role == TownRole.DBZ_SHOPKEEPER) {
            dbzOffers(offers, kind);
            return offers;
        }
        if (role.isCity()) {
            switch (kind) {
                case 0 -> {
                    // Electronics.
                    offers.add(sell(ModItems.ENERGY_CELL.get(), 1, 20));
                    offers.add(sell(Items.REDSTONE, 8, 5));
                    offers.add(sell(Items.OBSERVER, 1, 10));
                    offers.add(sell(Items.DAYLIGHT_DETECTOR, 1, 10));
                    offers.add(buy(Items.COPPER_INGOT, 6, 5));
                    offers.add(buy(Items.GOLD_INGOT, 3, 5));
                }
                case 1 -> {
                    // Grocery.
                    offers.add(sell(Items.BREAD, 6, 4));
                    offers.add(sell(Items.COOKED_BEEF, 4, 5));
                    offers.add(sell(Items.GOLDEN_CARROT, 3, 6));
                    offers.add(sell(Items.CAKE, 1, 8));
                    offers.add(buy(Items.WHEAT, 20, 5));
                    offers.add(buy(Items.CARROT, 16, 5));
                }
                case 2 -> {
                    // Building supplies.
                    offers.add(sell(Items.GLASS, 8, 4));
                    offers.add(sell(Items.WHITE_CONCRETE, 8, 4));
                    offers.add(sell(Items.QUARTZ_BLOCK, 4, 5));
                    offers.add(sell(Items.IRON_INGOT, 2, 5));
                    offers.add(buy(Items.COBBLESTONE, 32, 4));
                    offers.add(buy(Items.OAK_LOG, 16, 5));
                }
                default -> {
                    // Tech: the good stuff, at a price.
                    offers.add(sell(ModItems.ARC_REACTOR.get(), 1, 120));
                    offers.add(sell(ModItems.ENERGY_CELL.get(), 3, 50));
                    offers.add(sell(Items.SPYGLASS, 1, 15));
                    offers.add(buy(Items.DIAMOND, 1, 20));
                    offers.add(buy(Items.NETHERITE_SCRAP, 1, 60));
                    exchange(offers, 5);
                }
            }
            return offers;
        }
        switch (kind) {
            case 0 -> {
                // Grain merchant.
                offers.add(sell(Items.BREAD, 6, 3));
                offers.add(sell(Items.COOKED_MUTTON, 4, 4));
                offers.add(sell(Items.WHEAT_SEEDS, 16, 2));
                offers.add(buy(Items.WHEAT, 20, 4));
                offers.add(buy(Items.POTATO, 16, 4));
                offers.add(buy(Items.LEATHER, 6, 4));
            }
            case 1 -> {
                // Arms dealer.
                offers.add(sell(Items.IRON_SWORD, 1, 16));
                offers.add(sell(Items.BOW, 1, 12));
                offers.add(sell(Items.ARROW, 16, 4));
                offers.add(sell(Items.SHIELD, 1, 20));
                offers.add(sell(Items.IRON_CHESTPLATE, 1, 36));
                offers.add(buy(Items.IRON_INGOT, 4, 4));
            }
            default -> {
                // The kingdom's own goods.
                Kingdom kingdom = Kingdom.byId(town);
                switch (kingdom) {
                    case SHU -> offers.add(sell(Items.GOLDEN_APPLE, 1, 32));
                    case WEI -> offers.add(sell(Items.DIAMOND, 1, 24));
                    case WU -> offers.add(sell(Items.TRIDENT, 1, 64));
                }
                offers.add(sell(Items.STRING, 8, 4));
                offers.add(sell(Items.LANTERN, 2, 4));
                offers.add(sell(Items.FLOWER_POT, 3, 3));
                offers.add(sell(Items.SPYGLASS, 1, 16));
                offers.add(buy(Items.GOLD_INGOT, 3, 4));
                exchange(offers, 4);
            }
        }
        return offers;
    }

    /** West City (M17): a Capsule Corp shop, a bakery, a training goods shop (who changes emeralds: 1 = 5 zeni). */
    private static void dbzOffers(MerchantOffers offers, int kind) {
        switch (kind) {
            case 0 -> {
                // Capsule Corp: machines and power.
                offers.add(sell(ModItems.ENERGY_CELL.get(), 1, 20));
                offers.add(sell(ModItems.ARC_REACTOR.get(), 1, 120));
                offers.add(sell(Items.REDSTONE, 8, 5));
                offers.add(sell(Items.PISTON, 2, 6));
                offers.add(buy(Items.COPPER_INGOT, 6, 5));
                offers.add(buy(Items.IRON_INGOT, 4, 5));
            }
            case 1 -> {
                // Bakery.
                offers.add(sell(Items.BREAD, 6, 4));
                offers.add(sell(Items.CAKE, 1, 8));
                offers.add(sell(Items.PUMPKIN_PIE, 3, 5));
                offers.add(sell(Items.COOKIE, 12, 4));
                offers.add(buy(Items.WHEAT, 20, 5));
                offers.add(buy(Items.SUGAR_CANE, 16, 5));
            }
            default -> {
                // Training goods: for fighters - and a senzu bean, at a price.
                offers.add(sell(ModItems.SENZU_BEAN.get(), 1, 60));
                offers.add(sell(Items.IRON_CHESTPLATE, 1, 30));
                offers.add(sell(Items.GOLDEN_APPLE, 1, 30));
                offers.add(sell(Items.COOKED_BEEF, 6, 6));
                offers.add(buy(Items.DIAMOND, 1, 20));
                exchange(offers, 5);
            }
        }
    }
}
