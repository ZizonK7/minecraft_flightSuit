package com.pfkfks.flightsuit.war;

import net.minecraft.world.entity.Entity;

/** A soldier or general of a raid (or one who joined the player's side). */
public interface RaidMember {
    /** The raid they came with (-1 = none: spawned by hand, or recruited). */
    int raidId();

    /** Kneeling after a surrender, or fighting for the player now - either way not something to fight. */
    boolean isNoThreat();

    static boolean isNoThreat(Entity entity) {
        return entity instanceof RaidMember member && member.isNoThreat();
    }
}
