package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class ModKeys {
    private static final String CATEGORY = "key.categories.flightsuit";

    /** G: suit up from a capsule / pack the worn suit away (becomes "call main suit" once glasses exist). */
    public static final KeyMapping SUIT_TOGGLE = new KeyMapping(
            "key.flightsuit.suit_toggle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY
    );

    private ModKeys() {
    }
}
