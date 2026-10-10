package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.guide.ItemTips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Hold Shift over one of our items to read how it's used (guide/ItemTips); otherwise a grey hint that you can. */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class ItemTipsClient {
    private ItemTipsClient() {
    }

    /** The tip's lines with the keys as bound now, or none if {@code item} has no tip. */
    public static List<Component> lines(Item item) {
        List<Component> lines = new ArrayList<>();
        String key = ItemTips.key(item);
        if (key == null || !I18n.exists(key)) {
            return lines;
        }
        String text = Component.translatable(key, GuideBook.keys()).getString();
        for (String line : text.split("\n")) {
            lines.add(Component.literal(line));
        }
        return lines;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        String key = ItemTips.key(event.getItemStack().getItem());
        if (key == null || !I18n.exists(key)) {
            return;
        }
        if (Screen.hasShiftDown()) {
            for (Component line : lines(event.getItemStack().getItem())) {
                event.getToolTip().add(line.copy().withStyle(ChatFormatting.GRAY));
            }
        } else {
            event.getToolTip().add(Component.translatable("tooltip.flightsuit.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
