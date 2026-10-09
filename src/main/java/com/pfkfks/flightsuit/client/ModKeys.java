package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class ModKeys {
    private static final String CATEGORY = "key.categories.flightsuit";

    /** G tap: call / board / step out of the suit. G hold: every suit home. */
    public static final KeyMapping SUIT_TOGGLE = new KeyMapping(
            "key.flightsuit.suit_toggle",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY
    );

    /** H: companion suits attack the aimed-at target (aim at nothing to call them off). */
    public static final KeyMapping COMMAND_ATTACK = new KeyMapping(
            "key.flightsuit.command_attack",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            CATEGORY
    );

    /** Z: tap = parry window, hold = nano shield (needs the suit chestplate). */
    public static final KeyMapping COUNTER = new KeyMapping(
            "key.flightsuit.counter",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            CATEGORY
    );

    private ModKeys() {
    }
}
