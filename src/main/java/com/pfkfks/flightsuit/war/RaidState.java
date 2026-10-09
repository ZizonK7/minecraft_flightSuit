package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One raid on one village, from the scouts' report to the end (saved, so a restart mid-raid carries on). */
public class RaidState {
    public enum Phase {
        /** The village is being loaded; the army forms up once its hall is there. */
        STARTING,
        ACTIVE,
        /** The army laid down its arms: waiting for the owner to recruit or release them. */
        SURRENDERED
    }

    public final int id;
    public final String villageKey;
    public final ResourceKey<Level> dimension;
    public final BlockPos hall;
    public final @Nullable UUID owner;
    public final Kingdom kingdom;
    public Phase phase = Phase.STARTING;
    /** 0..1: the share of the army that has to fall before the rest gives up (항전 의지). */
    public float resolve;
    public @Nullable General general;
    public boolean fireAttack;
    /** Waves sent so far (1..3). */
    public int wave;
    public int spawned;
    public int planned;
    public long startedAt;
    public long waveAt;
    public long surrenderedAt;
    public boolean generalDefeated;
    /** Guan Yu's duel was fought and lost: the army breaks at once. */
    public boolean duelLost;
    /** Compass angle the army comes from. */
    public float approach;
    public boolean wavering;
    /** Soldiers per wave for this raid (from the village's size when it started), and how many the last wave had. */
    public int waveSize;
    public int lastWave;

    RaidState(int id, String villageKey, ResourceKey<Level> dimension, BlockPos hall, @Nullable UUID owner, Kingdom kingdom) {
        this.id = id;
        this.villageKey = villageKey;
        this.dimension = dimension;
        this.hall = hall.immutable();
        this.owner = owner;
        this.kingdom = kingdom;
    }

    /** How the scouts would put the army's spirit (shown when it wavers, and on the surrender). */
    public String resolveKey() {
        if (resolve >= 0.85F) {
            return "resolve.flightsuit.die_hard";
        }
        if (resolve >= 0.5F) {
            return "resolve.flightsuit.steady";
        }
        return "resolve.flightsuit.cowardly";
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Village", villageKey);
        tag.putString("Dim", dimension.location().toString());
        tag.put("Hall", NbtUtils.writeBlockPos(hall));
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Kingdom", kingdom.ordinal());
        tag.putInt("Phase", phase.ordinal());
        tag.putFloat("Resolve", resolve);
        tag.putInt("General", general == null ? -1 : general.ordinal());
        tag.putBoolean("Fire", fireAttack);
        tag.putInt("Wave", wave);
        tag.putInt("Spawned", spawned);
        tag.putInt("Planned", planned);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("WaveAt", waveAt);
        tag.putLong("SurrenderedAt", surrenderedAt);
        tag.putBoolean("GeneralDefeated", generalDefeated);
        tag.putBoolean("DuelLost", duelLost);
        tag.putFloat("Approach", approach);
        tag.putBoolean("Wavering", wavering);
        tag.putInt("WaveSize", waveSize);
        tag.putInt("LastWave", lastWave);
        return tag;
    }

    static @Nullable RaidState load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        if (dim == null) {
            return null;
        }
        RaidState raid = new RaidState(tag.getInt("Id"), tag.getString("Village"), ResourceKey.create(Registries.DIMENSION, dim),
                NbtUtils.readBlockPos(tag.getCompound("Hall")), tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                Kingdom.byId(tag.getInt("Kingdom")));
        int phase = tag.getInt("Phase");
        raid.phase = phase >= 0 && phase < Phase.values().length ? Phase.values()[phase] : Phase.STARTING;
        raid.resolve = tag.getFloat("Resolve");
        int general = tag.getInt("General");
        raid.general = general < 0 ? null : General.byId(general);
        raid.fireAttack = tag.getBoolean("Fire");
        raid.wave = tag.getInt("Wave");
        raid.spawned = tag.getInt("Spawned");
        raid.planned = tag.getInt("Planned");
        raid.startedAt = tag.getLong("StartedAt");
        raid.waveAt = tag.getLong("WaveAt");
        raid.surrenderedAt = tag.getLong("SurrenderedAt");
        raid.generalDefeated = tag.getBoolean("GeneralDefeated");
        raid.duelLost = tag.getBoolean("DuelLost");
        raid.approach = tag.getFloat("Approach");
        raid.wavering = tag.getBoolean("Wavering");
        raid.waveSize = Math.max(1, tag.getInt("WaveSize"));
        raid.lastWave = tag.getInt("LastWave");
        return raid;
    }
}
