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
        /** Prisoners taken at a fortress, on their way here: soldiers, and generals (bits by General ordinal). */
        public int pendingRecruits;
        public int pendingGenerals;

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
            tag.putInt("PendingRecruits", pendingRecruits);
            tag.putInt("PendingGenerals", pendingGenerals);
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
            record.pendingRecruits = tag.getInt("PendingRecruits");
            record.pendingGenerals = tag.getInt("PendingGenerals");
            return record;
        }
    }

    private final Map<String, VillageRecord> villages = new LinkedHashMap<>();
    private final Map<Integer, RaidState> raids = new LinkedHashMap<>();
    /** Per player: trust (-100..100) and the rest of their dealings with each kingdom (M12). */
    private final Map<UUID, Standing> standings = new HashMap<>();
    private final Map<Kingdom, FortRecord> forts = new java.util.EnumMap<>(Kingdom.class);
    /** Where each general is: at home, away with an army, serving the player, or beaten (until a day). */
    private final Map<General, Integer> generalState = new java.util.EnumMap<>(General.class);
    private final Map<General, Long> generalUntil = new java.util.EnumMap<>(General.class);
    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    private final Map<Integer, Battle> battles = new LinkedHashMap<>();
    private int nextRaidId = 1;
    private int nextRequestId = 1;
    private int nextBattleId = 1;

    public static final int GENERAL_HOME = 0;
    public static final int GENERAL_AWAY = 1;
    public static final int GENERAL_RECRUITED = 2;
    public static final int GENERAL_BEATEN = 3;

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

    /** The player's first village (where prisoners and tribute go), or null. */
    public @Nullable VillageRecord homeVillage(UUID owner) {
        for (VillageRecord record : villages.values()) {
            if (owner.equals(record.owner)) {
                return record;
            }
        }
        return null;
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
        Standing standing = standings.get(player);
        return standing == null ? 0 : standing.trust(kingdom);
    }

    public void addTrust(UUID player, Kingdom kingdom, int delta) {
        Standing standing = standing(player);
        int i = kingdom.ordinal();
        standing.trust[i] = Math.max(-100, Math.min(100, standing.trust[i] + delta));
        // Falling out with a kingdom you lead ends it.
        if (standing.trust[i] < 50 && standing.leads(kingdom)) {
            standing.leaderOf &= ~(1 << i);
        }
        setDirty();
    }

    /** Drops trust to at least {@code floor} (declaring war: hostile at once). */
    public void lowerTrustTo(UUID player, Kingdom kingdom, int floor) {
        Standing standing = standing(player);
        if (standing.trust[kingdom.ordinal()] > floor) {
            addTrust(player, kingdom, floor - standing.trust[kingdom.ordinal()]);
        }
    }

    public Standing standing(UUID player) {
        return standings.computeIfAbsent(player, id -> new Standing());
    }

    public Map<UUID, Standing> standings() {
        return standings;
    }

    public void markFound(UUID player, Kingdom kingdom) {
        standing(player).discovered |= 1 << kingdom.ordinal();
        setDirty();
    }

    public void markDone(UUID player, Kingdom kingdom) {
        standing(player).done[kingdom.ordinal()]++;
        setDirty();
    }

    public void setLeader(UUID player, Kingdom kingdom) {
        standing(player).leaderOf |= 1 << kingdom.ordinal();
        setDirty();
    }

    // ---- fortresses ----

    public @Nullable FortRecord fort(Kingdom kingdom) {
        return forts.get(kingdom);
    }

    public Map<Kingdom, FortRecord> forts() {
        return forts;
    }

    public FortRecord newFort(Kingdom kingdom) {
        FortRecord fort = new FortRecord(kingdom);
        forts.put(kingdom, fort);
        setDirty();
        return fort;
    }

    // ---- generals ----

    public int generalState(General general, long today) {
        int state = generalState.getOrDefault(general, GENERAL_HOME);
        if (state == GENERAL_BEATEN && today >= generalUntil.getOrDefault(general, 0L)) {
            generalState.put(general, GENERAL_HOME);
            setDirty();
            return GENERAL_HOME;
        }
        return state;
    }

    public void setGeneralState(General general, int state, long untilDay) {
        generalState.put(general, state);
        generalUntil.put(general, untilDay);
        setDirty();
    }

    // ---- requests and battles ----

    public Map<Integer, Request> requests() {
        return requests;
    }

    public Request newRequest(UUID player, Kingdom kingdom, Request.Type type, @Nullable Kingdom target, long day) {
        Request request = new Request(nextRequestId++, player, kingdom, type, target, day);
        requests.put(request.id, request);
        setDirty();
        return request;
    }

    public void endRequest(Request request) {
        requests.remove(request.id);
        setDirty();
    }

    public @Nullable Request openRequest(UUID player, Kingdom kingdom) {
        for (Request request : requests.values()) {
            if (request.player.equals(player) && request.kingdom == kingdom) {
                return request;
            }
        }
        return null;
    }

    public Map<Integer, Battle> battles() {
        return battles;
    }

    public Battle newBattle(Battle.Kind kind, Kingdom fort, Kingdom attacker, @Nullable UUID player, int request) {
        Battle battle = new Battle(nextBattleId++, kind, fort, attacker, player, request);
        battles.put(battle.id, battle);
        setDirty();
        return battle;
    }

    public void endBattle(Battle battle) {
        battles.remove(battle.id);
        setDirty();
    }

    public @Nullable Battle battleAt(Kingdom fort) {
        for (Battle battle : battles.values()) {
            if (battle.fort == fort) {
                return battle;
            }
        }
        return null;
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
        CompoundTag standingTag = new CompoundTag();
        for (Map.Entry<UUID, Standing> entry : standings.entrySet()) {
            standingTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("Standings", standingTag);
        CompoundTag fortTag = new CompoundTag();
        for (FortRecord fort : forts.values()) {
            fortTag.put(fort.kingdom.id(), fort.save());
        }
        tag.put("Forts", fortTag);
        CompoundTag generalTag = new CompoundTag();
        for (Map.Entry<General, Integer> entry : generalState.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("State", entry.getValue());
            one.putLong("Until", generalUntil.getOrDefault(entry.getKey(), 0L));
            generalTag.put(entry.getKey().id(), one);
        }
        tag.put("Generals", generalTag);
        ListTag requestList = new ListTag();
        for (Request request : requests.values()) {
            requestList.add(request.save());
        }
        tag.put("Requests", requestList);
        ListTag battleList = new ListTag();
        for (Battle battle : battles.values()) {
            battleList.add(battle.save());
        }
        tag.put("Battles", battleList);
        tag.putInt("NextRaidId", nextRaidId);
        tag.putInt("NextRequestId", nextRequestId);
        tag.putInt("NextBattleId", nextBattleId);
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
        CompoundTag standingTag = tag.getCompound("Standings");
        for (String key : standingTag.getAllKeys()) {
            try {
                data.standings.put(UUID.fromString(key), Standing.load(standingTag.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        // From M10 saves: trust only.
        CompoundTag trustTag = tag.getCompound("Trust");
        for (String key : trustTag.getAllKeys()) {
            try {
                Standing.copy(trustTag.getIntArray(key), data.standing(UUID.fromString(key)).trust);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        CompoundTag fortTag = tag.getCompound("Forts");
        for (Kingdom kingdom : Kingdom.values()) {
            if (fortTag.contains(kingdom.id())) {
                data.forts.put(kingdom, FortRecord.load(kingdom, fortTag.getCompound(kingdom.id())));
            }
        }
        CompoundTag generalTag = tag.getCompound("Generals");
        for (General general : General.values()) {
            if (generalTag.contains(general.id())) {
                CompoundTag one = generalTag.getCompound(general.id());
                data.generalState.put(general, one.getInt("State"));
                data.generalUntil.put(general, one.getLong("Until"));
            }
        }
        for (Tag raw : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            Request request = Request.load((CompoundTag) raw);
            if (request != null) {
                data.requests.put(request.id, request);
            }
        }
        for (Tag raw : tag.getList("Battles", Tag.TAG_COMPOUND)) {
            Battle battle = Battle.load((CompoundTag) raw);
            data.battles.put(battle.id, battle);
        }
        data.nextRaidId = Math.max(1, tag.getInt("NextRaidId"));
        data.nextRequestId = Math.max(1, tag.getInt("NextRequestId"));
        data.nextBattleId = Math.max(1, tag.getInt("NextBattleId"));
        return data;
    }
}
