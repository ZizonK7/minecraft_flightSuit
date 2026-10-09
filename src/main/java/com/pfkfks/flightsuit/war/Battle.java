package com.pfkfks.flightsuit.war;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A fight at a fortress (M12): another kingdom's waves storming it (the player defending it on request), or
 * an allied army marching on it with the player (an invasion). Uses the raid wave machinery's numbers.
 */
public class Battle {
    public enum Kind {
        /** {@link #attacker} storms {@link #fort}'s fortress in waves; the player helps the defenders. */
        STORM,
        /** {@link #attacker}'s army (allied to the player) marches on {@link #fort}'s fortress with the player. */
        INVADE
    }

    public final int id;
    public final Kind kind;
    /** Whose fortress it is. */
    public final Kingdom fort;
    public final Kingdom attacker;
    public final @Nullable UUID player;
    public final int request;
    public long startedAt;
    public long waveAt;
    public int wave;
    public int lastWave;
    public int spawned;
    public int waveSize;

    Battle(int id, Kind kind, Kingdom fort, Kingdom attacker, @Nullable UUID player, int request) {
        this.id = id;
        this.kind = kind;
        this.fort = fort;
        this.attacker = attacker;
        this.player = player;
        this.request = request;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putInt("Kind", kind.ordinal());
        tag.putInt("Fort", fort.ordinal());
        tag.putInt("Attacker", attacker.ordinal());
        if (player != null) {
            tag.putUUID("Player", player);
        }
        tag.putInt("Request", request);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("WaveAt", waveAt);
        tag.putInt("Wave", wave);
        tag.putInt("LastWave", lastWave);
        tag.putInt("Spawned", spawned);
        tag.putInt("WaveSize", waveSize);
        return tag;
    }

    static Battle load(CompoundTag tag) {
        int kind = tag.getInt("Kind");
        Battle battle = new Battle(tag.getInt("Id"), Kind.values()[Math.max(0, Math.min(1, kind))], Kingdom.byId(tag.getInt("Fort")),
                Kingdom.byId(tag.getInt("Attacker")), tag.hasUUID("Player") ? tag.getUUID("Player") : null, tag.getInt("Request"));
        battle.startedAt = tag.getLong("StartedAt");
        battle.waveAt = tag.getLong("WaveAt");
        battle.wave = tag.getInt("Wave");
        battle.lastWave = tag.getInt("LastWave");
        battle.spawned = tag.getInt("Spawned");
        battle.waveSize = Math.max(1, tag.getInt("WaveSize"));
        return battle;
    }
}
