package com.pfkfks.flightsuit.thanos;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/** The six Infinity Stones (DESIGN.md 4-16, M16). Bits for PlanetData.Traveller.stones. */
public enum InfinityStone {
    SPACE("space", 0x2E6CFF),
    MIND("mind", 0xF2D21E),
    REALITY("reality", 0xE0202A),
    POWER("power", 0x9A3AE8),
    TIME("time", 0x2EC85A),
    SOUL("soul", 0xF28A1E);

    public static final int ALL = (1 << 6) - 1;

    private final String id;
    private final int colour;

    InfinityStone(String id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    public int colour() {
        return colour;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Component displayName() {
        return Component.translatable("item.flightsuit." + id + "_stone").withStyle(style -> style.withColor(TextColor.fromRgb(colour)));
    }

    public Item item() {
        return com.pfkfks.flightsuit.registry.ModItems.stone(this);
    }

    public static @Nullable InfinityStone of(Item item) {
        for (InfinityStone stone : values()) {
            if (stone.item() == item) {
                return stone;
            }
        }
        return null;
    }

    /** "공간 · 마인드 · ..." for a mask. */
    public static Component list(int mask) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean first = true;
        for (InfinityStone stone : values()) {
            if ((mask & stone.bit()) != 0) {
                if (!first) {
                    out.append(" · ");
                }
                out.append(stone.displayName());
                first = false;
            }
        }
        return first ? Component.literal("-") : out;
    }
}
