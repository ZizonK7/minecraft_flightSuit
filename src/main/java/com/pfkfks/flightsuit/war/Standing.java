package com.pfkfks.flightsuit.war;

import net.minecraft.nbt.CompoundTag;

/**
 * How one player stands with the three kingdoms (DESIGN.md 4-11 외교와 의뢰): trust, which fortresses they've
 * found, requests done, when each kingdom last asked or sent help, and where they rule.
 */
public class Standing {
    private static final int K = Kingdom.values().length;

    final int[] trust = new int[K];
    final int[] done = new int[K];
    final long[] lastRequest = new long[K];
    final long[] lastMuster = new long[K];
    final long[] lastTribute = new long[K];
    int discovered;
    int leaderOf;
    long lastDecayDay;

    public int trust(Kingdom kingdom) {
        return trust[kingdom.ordinal()];
    }

    public int done(Kingdom kingdom) {
        return done[kingdom.ordinal()];
    }

    public boolean hasFound(Kingdom kingdom) {
        return (discovered & (1 << kingdom.ordinal())) != 0;
    }

    public boolean leads(Kingdom kingdom) {
        return (leaderOf & (1 << kingdom.ordinal())) != 0;
    }

    public static Tier tier(int trust) {
        if (trust <= -50) {
            return Tier.HOSTILE;
        }
        if (trust < 0) {
            return Tier.WARY;
        }
        if (trust < 30) {
            return Tier.NEUTRAL;
        }
        if (trust < 70) {
            return Tier.FRIENDLY;
        }
        return Tier.ALLIED;
    }

    /** DESIGN 신뢰도 단계. */
    public enum Tier {
        HOSTILE, WARY, NEUTRAL, FRIENDLY, ALLIED;

        public String key() {
            return "tier.flightsuit." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Trust", trust);
        tag.putIntArray("Done", done);
        tag.putLongArray("LastRequest", lastRequest);
        tag.putLongArray("LastMuster", lastMuster);
        tag.putLongArray("LastTribute", lastTribute);
        tag.putInt("Discovered", discovered);
        tag.putInt("Leader", leaderOf);
        tag.putLong("LastDecay", lastDecayDay);
        return tag;
    }

    static Standing load(CompoundTag tag) {
        Standing standing = new Standing();
        copy(tag.getIntArray("Trust"), standing.trust);
        copy(tag.getIntArray("Done"), standing.done);
        copy(tag.getLongArray("LastRequest"), standing.lastRequest);
        copy(tag.getLongArray("LastMuster"), standing.lastMuster);
        copy(tag.getLongArray("LastTribute"), standing.lastTribute);
        standing.discovered = tag.getInt("Discovered");
        standing.leaderOf = tag.getInt("Leader");
        standing.lastDecayDay = tag.getLong("LastDecay");
        return standing;
    }

    static void copy(int[] from, int[] to) {
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }

    static void copy(long[] from, long[] to) {
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }
}
