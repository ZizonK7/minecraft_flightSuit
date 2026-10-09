package com.pfkfks.flightsuit.hero;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hero City's world state (DESIGN.md 4-13, M13), kept with the overworld: where the city is, whether it has been
 * built or taken lately, which heroes are away recovering, how each player stands with it, its requests, and a
 * villain attack under way.
 */
public class HeroData extends SavedData {
    private static final String NAME = "flightsuit_hero_city";

    /** How one player stands with Hero City, and what Iron Man has done for them lately. */
    public static final class Standing {
        public int trust;
        public int done;
        public boolean found;
        public long lastRequest;
        public long repairDay = -1;
        public long reactorDay = -1;
        public boolean gift;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Trust", trust);
            tag.putInt("Done", done);
            tag.putBoolean("Found", found);
            tag.putLong("LastRequest", lastRequest);
            tag.putLong("RepairDay", repairDay);
            tag.putLong("ReactorDay", reactorDay);
            tag.putBoolean("Gift", gift);
            return tag;
        }

        static Standing load(CompoundTag tag) {
            Standing standing = new Standing();
            standing.trust = tag.getInt("Trust");
            standing.done = tag.getInt("Done");
            standing.found = tag.getBoolean("Found");
            standing.lastRequest = tag.getLong("LastRequest");
            standing.repairDay = tag.contains("RepairDay") ? tag.getLong("RepairDay") : -1;
            standing.reactorDay = tag.contains("ReactorDay") ? tag.getLong("ReactorDay") : -1;
            standing.gift = tag.getBoolean("Gift");
            return standing;
        }
    }

    /** Hero City asking for help: hold off a villain attack, or lend soldiers (it never asks to invade anyone). */
    public static final class Request {
        public enum Type { DEFEND, REINFORCE }

        public enum State { OFFERED, ACCEPTED, ACTIVE }

        public final int id;
        public final UUID player;
        public final Type type;
        public final long offeredDay;
        public long day;
        public int needed;
        public int days;
        public State state = State.OFFERED;
        public ListTag away = new ListTag();
        public String villageKey = "";
        public long returnDay;
        public boolean reminded;

        Request(int id, UUID player, Type type, long offeredDay) {
            this.id = id;
            this.player = player;
            this.type = type;
            this.offeredDay = offeredDay;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Id", id);
            tag.putUUID("Player", player);
            tag.putInt("Type", type.ordinal());
            tag.putLong("Offered", offeredDay);
            tag.putLong("Day", day);
            tag.putInt("Needed", needed);
            tag.putInt("Days", days);
            tag.putInt("State", state.ordinal());
            tag.put("Away", away);
            tag.putString("Village", villageKey);
            tag.putLong("Return", returnDay);
            tag.putBoolean("Reminded", reminded);
            return tag;
        }

        static @Nullable Request load(CompoundTag tag) {
            if (!tag.hasUUID("Player")) {
                return null;
            }
            Request request = new Request(tag.getInt("Id"), tag.getUUID("Player"),
                    Type.values()[Math.max(0, Math.min(1, tag.getInt("Type")))], tag.getLong("Offered"));
            request.day = tag.getLong("Day");
            request.needed = tag.getInt("Needed");
            request.days = tag.getInt("Days");
            request.state = State.values()[Math.max(0, Math.min(2, tag.getInt("State")))];
            request.away = tag.getList("Away", Tag.TAG_COMPOUND);
            request.villageKey = tag.getString("Village");
            request.returnDay = tag.getLong("Return");
            request.reminded = tag.getBoolean("Reminded");
            return request;
        }
    }

    /** A villain attack on the city (the DEFEND request's battle). */
    public static final class Storm {
        public int request = -1;
        public @Nullable UUID player;
        public long startedAt;
        public long waveAt;
        public int wave;
        public int lastWave;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Request", request);
            if (player != null) {
                tag.putUUID("Player", player);
            }
            tag.putLong("StartedAt", startedAt);
            tag.putLong("WaveAt", waveAt);
            tag.putInt("Wave", wave);
            tag.putInt("LastWave", lastWave);
            return tag;
        }

        static Storm load(CompoundTag tag) {
            Storm storm = new Storm();
            storm.request = tag.getInt("Request");
            storm.player = tag.hasUUID("Player") ? tag.getUUID("Player") : null;
            storm.startedAt = tag.getLong("StartedAt");
            storm.waveAt = tag.getLong("WaveAt");
            storm.wave = tag.getInt("Wave");
            storm.lastWave = tag.getInt("LastWave");
            return storm;
        }
    }

    public boolean sited;
    public int x;
    public int z;
    public int y;
    public boolean built;
    public long fallenUntilDay;
    private final Map<HeroType, Long> awayUntil = new EnumMap<>(HeroType.class);
    private final Map<UUID, Standing> standings = new HashMap<>();
    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    public @Nullable Storm storm;
    private int nextRequestId = 1;

    public static HeroData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HeroData::load, HeroData::new, NAME);
    }

    public BlockPos center() {
        return new BlockPos(x, y, z);
    }

    public boolean isFallen(long day) {
        return day < fallenUntilDay;
    }

    /** A hero beaten (or driven out with the city) is away recovering until this day. */
    public boolean isHome(HeroType hero, long day) {
        return day >= awayUntil.getOrDefault(hero, 0L);
    }

    public void sendAway(HeroType hero, long untilDay) {
        awayUntil.put(hero, untilDay);
        setDirty();
    }

    public Standing standing(UUID player) {
        return standings.computeIfAbsent(player, id -> new Standing());
    }

    public Map<UUID, Standing> standings() {
        return standings;
    }

    public void addTrust(UUID player, int delta) {
        Standing standing = standing(player);
        standing.trust = Math.max(-100, Math.min(100, standing.trust + delta));
        setDirty();
    }

    public int trust(UUID player) {
        Standing standing = standings.get(player);
        return standing == null ? 0 : standing.trust;
    }

    public Map<Integer, Request> requests() {
        return requests;
    }

    public Request newRequest(UUID player, Request.Type type, long day) {
        Request request = new Request(nextRequestId++, player, type, day);
        requests.put(request.id, request);
        setDirty();
        return request;
    }

    public void endRequest(Request request) {
        requests.remove(request.id);
        setDirty();
    }

    public @Nullable Request openRequest(UUID player) {
        for (Request request : requests.values()) {
            if (request.player.equals(player)) {
                return request;
            }
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Sited", sited);
        tag.putInt("X", x);
        tag.putInt("Z", z);
        tag.putInt("Y", y);
        tag.putBoolean("Built", built);
        tag.putLong("FallenUntil", fallenUntilDay);
        CompoundTag away = new CompoundTag();
        for (Map.Entry<HeroType, Long> entry : awayUntil.entrySet()) {
            away.putLong(entry.getKey().id(), entry.getValue());
        }
        tag.put("Away", away);
        CompoundTag standingTag = new CompoundTag();
        for (Map.Entry<UUID, Standing> entry : standings.entrySet()) {
            standingTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("Standings", standingTag);
        ListTag requestList = new ListTag();
        for (Request request : requests.values()) {
            requestList.add(request.save());
        }
        tag.put("Requests", requestList);
        if (storm != null) {
            tag.put("Storm", storm.save());
        }
        tag.putInt("NextRequestId", nextRequestId);
        return tag;
    }

    public static HeroData load(CompoundTag tag) {
        HeroData data = new HeroData();
        data.sited = tag.getBoolean("Sited");
        data.x = tag.getInt("X");
        data.z = tag.getInt("Z");
        data.y = tag.getInt("Y");
        data.built = tag.getBoolean("Built");
        data.fallenUntilDay = tag.getLong("FallenUntil");
        CompoundTag away = tag.getCompound("Away");
        for (HeroType hero : HeroType.values()) {
            if (away.contains(hero.id())) {
                data.awayUntil.put(hero, away.getLong(hero.id()));
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
        for (Tag raw : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            Request request = Request.load((CompoundTag) raw);
            if (request != null) {
                data.requests.put(request.id, request);
            }
        }
        if (tag.contains("Storm")) {
            data.storm = Storm.load(tag.getCompound("Storm"));
        }
        data.nextRequestId = Math.max(1, tag.getInt("NextRequestId"));
        return data;
    }
}
