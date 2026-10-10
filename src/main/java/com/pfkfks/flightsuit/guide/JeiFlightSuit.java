package com.pfkfks.flightsuit.guide;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.client.ItemTipsClient;
import com.pfkfks.flightsuit.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * JEI, if it's installed (optional): our recipes show on R / U like everything else's, and each of our items with a
 * tip (ItemTips) gets an information page saying how it's used. Only JEI loads this class.
 */
@JeiPlugin
public class JeiFlightSuit implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(FlightSuitMod.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
            List<Component> lines = ItemTipsClient.lines(entry.get());
            if (!lines.isEmpty()) {
                registration.addIngredientInfo(new ItemStack(entry.get()), VanillaTypes.ITEM_STACK, lines.toArray(new Component[0]));
            }
        }
    }
}
