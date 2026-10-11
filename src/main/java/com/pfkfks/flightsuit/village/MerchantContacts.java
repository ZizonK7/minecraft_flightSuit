package com.pfkfks.flightsuit.village;

import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.war.Kingdom;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The village merchant's outside contacts (2026-10-12 user: "상인 직업을 가진 주민이 외부 상인과 접촉한다는 컨셉으로,
 * 외부의 것을 기간 동안 판매"): every CONTACT_DAYS days the merchant strikes a deal with one trading house - Wei's,
 * Shu's or Wu's (wuzhu coins) or a Hero City dealer (dollars) - and for that stretch sells its goods from the shop,
 * the things a suit is made of: iron, gold, copper, redstone, and the dearer ones (diamonds, energy cells, an arc
 * reactor) once the merchant is good enough. Paid in that house's money (the coins and dollars found in vanilla
 * village chests, VillageMoney), or emeralds changed at the counter. A kingdom that has turned on the player
 * (trust -50 or less) sends nobody.
 */
public final class MerchantContacts {
    public enum Contact {
        WEI("wei", false),
        SHU("shu", false),
        WU("wu", false),
        HERO_CITY("hero_city", true);

        private final String id;
        private final boolean dollars;

        Contact(String id, boolean dollars) {
            this.id = id;
            this.dollars = dollars;
        }

        public String translationKey() {
            return "contact.flightsuit." + id;
        }

        public Item money() {
            return dollars ? ModItems.DOLLAR.get() : ModItems.WUZHU_COIN.get();
        }

        /** How much of this house's money an emerald is worth (the towns' own rates, TownTrades). */
        int perEmerald() {
            return dollars ? 5 : 4;
        }

        @Nullable Kingdom kingdom() {
            return switch (this) {
                case WEI -> Kingdom.WEI;
                case SHU -> Kingdom.SHU;
                case WU -> Kingdom.WU;
                default -> null;
            };
        }

        public static @Nullable Contact byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : null;
        }
    }

    private MerchantContacts() {
    }

    /** The next house to deal with: any but the last one, and none whose kingdom is at odds with the owner. */
    static @Nullable Contact next(ServerLevel level, @Nullable UUID owner, @Nullable Contact last, RandomSource random) {
        List<Contact> open = new ArrayList<>();
        for (Contact contact : Contact.values()) {
            Kingdom kingdom = contact.kingdom();
            if (contact == last || kingdom != null && owner != null
                    && WarData.get(level.getServer()).trust(owner, kingdom) <= -50) {
                continue;
            }
            open.add(contact);
        }
        if (open.isEmpty()) {
            return last;
        }
        return open.get(random.nextInt(open.size()));
    }

    /** The shop's shelves for this stretch: what the house brings, more of it (and dearer things) for a better merchant. */
    static MerchantOffers offers(Contact contact, int stars, boolean market) {
        int uses = VillageTuning.CONTACT_USES_BASE + VillageTuning.CONTACT_USES_PER_STAR * stars;
        if (market) {
            uses += uses / 3;
        }
        Shelf shelf = new Shelf(contact.money(), uses);
        switch (contact) {
            case WEI -> {
                // Wei: iron and steel.
                shelf.sell(Items.IRON_INGOT, 3, 4);
                shelf.sell(Items.COAL, 8, 2);
                shelf.sell(Items.SHIELD, 1, 6);
                if (stars >= 3) {
                    shelf.sell(Items.DIAMOND, 1, 28);
                }
                if (stars >= 4) {
                    shelf.sell(Items.IRON_BLOCK, 1, 11);
                }
            }
            case SHU -> {
                // Shu: gold, hides, and the fine things of the court.
                shelf.sell(Items.GOLD_INGOT, 2, 4);
                shelf.sell(Items.LEATHER, 4, 3);
                shelf.sell(Items.BOOK, 2, 3);
                if (stars >= 3) {
                    shelf.sell(Items.GOLD_BLOCK, 1, 16);
                }
                if (stars >= 4) {
                    shelf.sell(Items.GOLDEN_APPLE, 1, 30);
                }
            }
            case WU -> {
                // Wu: the river trade - copper, powder, sand and glass.
                shelf.sell(Items.COPPER_INGOT, 6, 3);
                shelf.sell(Items.GUNPOWDER, 3, 4);
                shelf.sell(Items.GLASS, 8, 3);
                if (stars >= 2) {
                    shelf.sell(Items.TNT, 1, 6);
                }
                if (stars >= 3) {
                    shelf.sell(Items.COPPER_BLOCK, 1, 5);
                }
            }
            case HERO_CITY -> {
                // Hero City: electronics and the odd bit of tech.
                shelf.sell(Items.REDSTONE, 8, 5);
                shelf.sell(Items.LAPIS_LAZULI, 4, 5);
                shelf.sell(Items.AMETHYST_SHARD, 2, 5);
                if (stars >= 2) {
                    shelf.sell(ModItems.ENERGY_CELL.get(), 1, 20);
                }
                if (stars >= 3) {
                    shelf.sell(Items.DIAMOND, 1, 35);
                }
                if (stars >= 4) {
                    shelf.sell(ModItems.ARC_REACTOR.get(), 1, 110);
                }
            }
        }
        // Emeralds changed at the counter, at the house's own rate.
        shelf.offers.add(new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(contact.money(), contact.perEmerald()), uses * 4, 0, 0.0F));
        return shelf.offers;
    }

    private static final class Shelf {
        final MerchantOffers offers = new MerchantOffers();
        final Item money;
        final int uses;

        Shelf(Item money, int uses) {
            this.money = money;
            this.uses = uses;
        }

        void sell(Item item, int count, int price) {
            offers.add(new MerchantOffer(new ItemStack(money, price), new ItemStack(item, count), uses, 0, 0.05F));
        }
    }
}
