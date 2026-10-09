package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A kingdom's fortress in the overworld (DESIGN.md 4-11: 먼 곳에 삼국지 세력의 성채가 자연 생성): where it is, whether
 * it's been built yet (it goes up the first time a player comes near), and whether it has fallen lately.
 */
public class FortRecord {
    public final Kingdom kingdom;
    public int x;
    public int z;
    /** Ground level it was built on (-1000 = not built yet). */
    public int y = -1000;
    public boolean built;
    /** Fallen (conquered): weak garrison, no generals, until this day. */
    public long fallenUntilDay;
    /** 0..1: how much of the garrison must fall before it gives up (항전 의지, rolled once per fortress). */
    public float resolve;

    FortRecord(Kingdom kingdom) {
        this.kingdom = kingdom;
    }

    public BlockPos center() {
        return new BlockPos(x, built ? y : 64, z);
    }

    public boolean isFallen(long day) {
        return day < fallenUntilDay;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", x);
        tag.putInt("Z", z);
        tag.putInt("Y", y);
        tag.putBoolean("Built", built);
        tag.putLong("FallenUntil", fallenUntilDay);
        tag.putFloat("Resolve", resolve);
        return tag;
    }

    static FortRecord load(Kingdom kingdom, CompoundTag tag) {
        FortRecord fort = new FortRecord(kingdom);
        fort.x = tag.getInt("X");
        fort.z = tag.getInt("Z");
        fort.y = tag.getInt("Y");
        fort.built = tag.getBoolean("Built");
        fort.fallenUntilDay = tag.getLong("FallenUntil");
        fort.resolve = tag.getFloat("Resolve");
        return fort;
    }
}
