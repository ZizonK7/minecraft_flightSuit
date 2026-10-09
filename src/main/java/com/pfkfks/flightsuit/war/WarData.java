package com.pfkfks.flightsuit.war;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-wide war state, kept with the overworld (villages may be unloaded when their raid comes - DESIGN.md
 * 4-15: the raid loads the village and happens for real). Knows every village hall that has ticked, when its
 * next raid is due, the raids under way, and how each player stands with each kingdom (for M12's diplomacy).
 */
public class WarData extends SavedData {
    private static final String NAME = "flightsuit_war";

    /** A village hall the raids know about. */
    public static final class VillageRecord {
        public final ResourceKey<Level> dimension;
        public final BlockPos hall;
        public @Nullable UUID owner;
        public int population;
        public long nextRaidDay;
        public boolean warned;
        /** The kingdom the scouts reported for the coming raid (-1 = not picked yet). */
        public int nextKingdom = -1;

        VillageRecord(ResourceKey<Level> dimension, BlockPos hall) {
            this.dimension = dimension;
            this.hall = hall.immutable();
        }

        public String key() {
            return keyOf(dimension, hall);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.put("Hall", NbtUtils.writeBlockPos(hall));
            if (owner != null) {
                tag.putUUID("Owner", owner);
            }
            tag.putInt("Population", population);
            tag.putLong("NextRaidDay", nextRaidDay);
            tag.putBoolean("Warned", warned);
            tag.putInt("NextKingdom", nextKingdom);
            return tag;
        }

        static @Nullable VillageRecord load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
            if (dim == null) {
                return null;
            }
            VillageRecord record = new VillageRecord(ResourceKey.create(Registries.DIMENSION, dim), NbtUtils.readBlockPos(tag.getCompound("Hall")));
            record.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            record.population = tag.getInt("Population");
            record.nextRaidDay = tag.getLong("NextRaidDay");
            record.warned = tag.getBoolean("Warned");
            record.nextKingdom = tag.contains("NextKingdom") ? tag.getInt("NextKingdom") : -1;
            return record;
        }
    }

    private final Map<String, VillageRecord> villages = new LinkedHashMap<>();
    private final Map<Integer, RaidState> raids = new LinkedHashMap<>();
    /** Per player, per kingdom ordinal: how they stand (-100..100). Raised by sparing prisoners, lowered by fighting. */
    private final Map<UUID, int[]> trust = new HashMap<>();
    private int nextRaidId = 1;

    public static WarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WarData::load, WarData::new, NAME);
    }

    public static String keyOf(ResourceKey<Level> dimension, BlockPos hall) {
        return dimension.location() + "|" + hall.asLong();
    }

    public Map<String, VillageRecord> villages() {
        return villages;
    }

    public VillageRecord village(ResourceKey<Level> dimension, BlockPos hall) {
        String key = keyOf(dimension, hall);
        VillageRecord record = villages.get(key);
        if (record == null) {
            record = new VillageRecord(dimension, hall);
            villages.put(key, record);
            setDirty();
        }
        return record;
    }

    public void forgetVillage(String key) {
        if (villages.remove(key) != null) {
            setDirty();
        }
    }

    public Map<Integer, RaidState> raids() {
        return raids;
    }

    public @Nullable RaidState raidOf(String villageKey) {
        for (RaidState raid : raids.values()) {
            if (raid.villageKey.equals(villageKey)) {
                return raid;
            }
        }
        return null;
    }

    public RaidState newRaid(VillageRecord village, Kingdom kingdom) {
        RaidState raid = new RaidState(nextRaidId++, village.key(), village.dimension, village.hall, village.owner, kingdom);
        raids.put(raid.id, raid);
        setDirty();
        return raid;
    }

    public void endRaid(RaidState raid) {
        raids.remove(raid.id);
        setDirty();
    }

    public int trust(UUID player, Kingdom kingdom) {
        int[] values = trust.get(player);
        return values == null ? 0 : values[kingdom.ordinal()];
    }

    public void addTrust(UUID player, Kingdom kingdom, int delta) {
        int[] values = trust.computeIfAbsent(player, id -> new int[Kingdom.values().length]);
        values[kingdom.ordinal()] = Math.max(-100, Math.min(100, values[kingdom.ordinal()] + delta));
        setDirty();
    }

    // ---- saving ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag villageList = new ListTag();
        for (VillageRecord record : villages.values()) {
            villageList.add(record.save());
        }
        tag.put("Villages", villageList);
        ListTag raidList = new ListTag();
        for (RaidState raid : raids.values()) {
            raidList.add(raid.save());
        }
        tag.put("Raids", raidList);
        CompoundTag trustTag = new CompoundTag();
        for (Map.Entry<UUID, int[]> entry : trust.entrySet()) {
            trustTag.putIntArray(entry.getKey().toString(), entry.getValue());
        }
        tag.put("Trust", trustTag);
        tag.putInt("NextRaidId", nextRaidId);
        return tag;
    }

    public static WarData load(CompoundTag tag) {
        WarData data = new WarData();
        for (Tag raw : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            VillageRecord record = VillageRecord.load((CompoundTag) raw);
            if (record != null) {
                data.villages.put(record.key(), record);
            }
        }
        for (Tag raw : tag.getList("Raids", Tag.TAG_COMPOUND)) {
            RaidState raid = RaidState.load((CompoundTag) raw);
            if (raid != null) {
                data.raids.put(raid.id, raid);
            }
        }
        CompoundTag trustTag = tag.getCompound("Trust");
        for (String key : trustTag.getAllKeys()) {
            try {
                int[] saved = trustTag.getIntArray(key);
                int[] values = new int[Kingdom.values().length];
                System.arraycopy(saved, 0, values, 0, Math.min(saved.length, values.length));
                data.trust.put(UUID.fromString(key), values);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        data.nextRaidId = Math.max(1, tag.getInt("NextRaidId"));
        return data;
    }
}
