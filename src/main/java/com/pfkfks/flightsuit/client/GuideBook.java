package com.pfkfks.flightsuit.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the guide (guide/GuideBookItem) in the vanilla book screen. Each page is a translation
 * ("guide.flightsuit.page.N") whose arguments are the keys as bound right now, so rebinding a key rewrites the book:
 * %1$s suit (G), %2$s attack order (H), %3$s wheel (R), %4$s counter (Z), %5$s skill 1 (X), %6$s skill 2 (C),
 * %7$s jump, %8$s sprint, %9$s use, %10$s sneak.
 */
public final class GuideBook {
    public static final int PAGES = 17;

    private GuideBook() {
    }

    /** The keys as bound now, in the order the guide's (and item tips') translations use them. */
    public static Object[] keys() {
        Options options = Minecraft.getInstance().options;
        return new Object[]{key(ModKeys.SUIT_TOGGLE), key(ModKeys.COMMAND_ATTACK), key(ModKeys.SUIT_WHEEL), key(ModKeys.COUNTER),
                key(ModKeys.SKILL_1), key(ModKeys.SKILL_2), key(options.keyJump), key(options.keySprint), key(options.keyUse),
                key(options.keyShift)};
    }

    public static void open() {
        Object[] keys = keys();
        List<FormattedText> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) {
            pages.add(Component.translatable("guide.flightsuit.page." + i, keys));
        }
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return pages.size();
            }

            @Override
            public FormattedText getPageRaw(int index) {
                return pages.get(index);
            }
        }));
    }

    private static Component key(KeyMapping mapping) {
        return Component.literal("[").append(mapping.getTranslatedKeyMessage()).append("]").withStyle(ChatFormatting.DARK_RED);
    }
}
