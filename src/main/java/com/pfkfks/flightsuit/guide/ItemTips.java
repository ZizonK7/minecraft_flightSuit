package com.pfkfks.flightsuit.guide;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * How-to text for our items (after the 4th test round: "make the controls easy to find, like other mods do"),
 * shown two ways: hold Shift over an item (client/ItemTipsClient, Create-style), and the item's info page in JEI
 * (JeiFlightSuit). One translation per item, "tip.flightsuit.<item>"; a suit's four parts and its capsule share
 * "tip.flightsuit.suit.<suit>", the infinity stones share "tip.flightsuit.infinity_stone". Lines split on "\n";
 * the guide's key arguments apply (client/GuideBook).
 */
public final class ItemTips {
    private static final String[] PART_SUFFIXES = {"_helmet", "_chestplate", "_leggings", "_boots", "_capsule"};

    private ItemTips() {
    }

    /** The tip's translation key for {@code item}, or null for items that aren't ours. Whether it exists is for the caller. */
    public static @Nullable String key(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !id.getNamespace().equals(FlightSuitMod.MODID)) {
            return null;
        }
        String path = id.getPath();
        if (path.endsWith("_stone")) {
            return "tip.flightsuit.infinity_stone";
        }
        if (!path.equals("hover_car_capsule")) {
            for (String suffix : PART_SUFFIXES) {
                if (path.endsWith(suffix)) {
                    return "tip.flightsuit.suit." + path.substring(0, path.length() - suffix.length());
                }
            }
        }
        return "tip.flightsuit." + path;
    }
}
