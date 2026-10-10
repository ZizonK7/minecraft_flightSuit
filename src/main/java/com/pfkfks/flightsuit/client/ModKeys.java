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

    /** R: suit wheel (needs EDITH glasses). */
    public static final KeyMapping SUIT_WHEEL = new KeyMapping(
            "key.flightsuit.suit_wheel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
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

    /** X: suit skill 1 (Mark 1 missiles, Mark 3 Judgment Draw). */
    public static final KeyMapping SKILL_1 = new KeyMapping(
            "key.flightsuit.skill_1",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            CATEGORY
    );

    /** C: suit skill 2 (Mark 3 Rose Carte Finale). */
    public static final KeyMapping SKILL_2 = new KeyMapping(
            "key.flightsuit.skill_2",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            CATEGORY
    );

    /** V: the ultimate (M17) - Mark 3's Tempest, Mark 5's Super Saiyan. */
    public static final KeyMapping ULTIMATE = new KeyMapping(
            "key.flightsuit.ultimate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            CATEGORY
    );

    /** B: Mark 3's stolen skill (M17). */
    public static final KeyMapping STOLEN_SKILL = new KeyMapping(
            "key.flightsuit.stolen_skill",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_B,
            CATEGORY
    );

    private ModKeys() {
    }
}
