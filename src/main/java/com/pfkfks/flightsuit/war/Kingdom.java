package com.pfkfks.flightsuit.war;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The three kingdoms whose armies march on the player's village (DESIGN.md 4-11 "삼국지 세력"). */
public enum Kingdom {
    WEI("wei", ChatFormatting.BLUE),
    SHU("shu", ChatFormatting.GREEN),
    WU("wu", ChatFormatting.RED);

    private static final Kingdom[] VALUES = values();

    private final String id;
    private final ChatFormatting color;

    Kingdom(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public ChatFormatting color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("kingdom.flightsuit." + id).withStyle(color);
    }

    public List<General> generals() {
        List<General> list = new ArrayList<>();
        for (General general : General.values()) {
            if (general.kingdom() == this) {
                list.add(general);
            }
        }
        return list;
    }

    public static Kingdom byId(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SHU;
    }

    public static Kingdom byName(String id) {
        for (Kingdom kingdom : VALUES) {
            if (kingdom.id.equals(id)) {
                return kingdom;
            }
        }
        return null;
    }
}
