package com.pfkfks.flightsuit.thanos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The final battle's world state (DESIGN.md 4-16 최종전, M16), kept with the overworld: when Thanos comes for
 * whom, who has beaten him, the raid under way, and the people snapped away from a village (back in a few days).
 */
public class ThanosData extends SavedData {
    private static final String NAME = "flightsuit_thanos";

    public static final class Raid {
        public final UUID player;
        public final ResourceKey<Level> dimension;
        public final BlockPos center;
        public final boolean city;
        public final String villageKey;
        public long startedAt;
        public long waveAt;
        public int wave;
        public boolean won;
        /** This raid's Thanos (so a stray copy, or one from an earlier raid, means nothing). */
        public @Nullable UUID thanos;

        public Raid(UUID player, ResourceKey<Level> dimension, BlockPos center, boolean city, String villageKey) {
            this.player = player;
            this.dimension = dimension;
            this.center = center.immutable();
            this.city = city;
            this.villageKey = villageKey;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", player);
            tag.putString("Dim", dimension.location().toString());
            tag.put("Center", NbtUtils.writeBlockPos(center));
            tag.putBoolean("City", city);
            tag.putString("Village", villageKey);
            tag.putLong("StartedAt", startedAt);
            tag.putLong("WaveAt", waveAt);
            tag.putInt("Wave", wave);
            if (thanos != null) {
                tag.putUUID("Thanos", thanos);
            }
            return tag;
        }

        static @Nullable Raid load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
            if (!tag.hasUUID("Player") || dim == null) {
                return null;
            }
            Raid raid = new Raid(tag.getUUID("Player"), ResourceKey.create(Registries.DIMENSION, dim), NbtUtils.readBlockPos(tag.getCompound("Center")),
                    tag.getBoolean("City"), tag.getString("Village"));
            raid.startedAt = tag.getLong("StartedAt");
            raid.waveAt = tag.getLong("WaveAt");
            raid.wave = tag.getInt("Wave");
            raid.thanos = tag.hasUUID("Thanos") ? tag.getUUID("Thanos") : null;
            return raid;
        }
    }

    /** People of a village snapped away: back on this day. */
    public static final class Snapped {
        public long returnDay;
        public ListTag residents = new ListTag();
    }

    public final Map<UUID, Long> raidDay = new HashMap<>();
    public final Set<UUID> defeated = new HashSet<>();
    public final Set<UUID> rewardOwed = new HashSet<>();
    public final Map<String, Snapped> snapped = new LinkedHashMap<>();
    public @Nullable Raid raid;

    public static ThanosData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ThanosData::load, ThanosData::new, NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag days = new CompoundTag();
        raidDay.forEach((id, day) -> days.putLong(id.toString(), day));
        tag.put("RaidDay", days);
        ListTag beat = new ListTag();
        for (UUID id : defeated) {
            beat.add(NbtUtils.createUUID(id));
        }
        tag.put("Defeated", beat);
        ListTag owed = new ListTag();
        for (UUID id : rewardOwed) {
            owed.add(NbtUtils.createUUID(id));
        }
        tag.put("RewardOwed", owed);
        CompoundTag gone = new CompoundTag();
        snapped.forEach((key, s) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Return", s.returnDay);
            entry.put("Residents", s.residents);
            gone.put(key, entry);
        });
        tag.put("Snapped", gone);
        if (raid != null) {
            tag.put("Raid", raid.save());
        }
        return tag;
    }

    public static ThanosData load(CompoundTag tag) {
        ThanosData data = new ThanosData();
        CompoundTag days = tag.getCompound("RaidDay");
        for (String key : days.getAllKeys()) {
            try {
                data.raidDay.put(UUID.fromString(key), days.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        for (Tag raw : tag.getList("Defeated", Tag.TAG_INT_ARRAY)) {
            data.defeated.add(NbtUtils.loadUUID(raw));
        }
        for (Tag raw : tag.getList("RewardOwed", Tag.TAG_INT_ARRAY)) {
            data.rewardOwed.add(NbtUtils.loadUUID(raw));
        }
        CompoundTag gone = tag.getCompound("Snapped");
        for (String key : gone.getAllKeys()) {
            CompoundTag entry = gone.getCompound(key);
            Snapped s = new Snapped();
            s.returnDay = entry.getLong("Return");
            s.residents = entry.getList("Residents", Tag.TAG_COMPOUND);
            data.snapped.put(key, s);
        }
        if (tag.contains("Raid")) {
            data.raid = Raid.load(tag.getCompound("Raid"));
        }
        return data;
    }
}
