package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A soldier or general of a raid (or one who joined the player's side). */
public interface RaidMember {
    /** The raid they came with (-1 = none: spawned by hand, or recruited). */
    int raidId();

    /** Kneeling after a surrender, or fighting for the player now - either way not something to fight. */
    boolean isNoThreat();

    Kingdom kingdom();

    WarRole role();

    /** RAID at a fortress: whose fortress; ALLY: the kingdom being invaded (null = none). */
    @Nullable Kingdom foe();

    /** ALLY: the player they follow (and a recruited general: their lord). */
    @Nullable UUID commander();

    /** GARRISON: the fortress centre. */
    @Nullable BlockPos home();

    /** Recruited generals and support troops fight for the player. */
    default boolean isPlayerSide() {
        return role() == WarRole.ALLY;
    }

    static boolean isNoThreat(Entity entity) {
        return entity instanceof RaidMember member && member.isNoThreat();
    }
}
