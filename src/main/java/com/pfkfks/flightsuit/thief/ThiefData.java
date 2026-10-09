package com.pfkfks.flightsuit.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Batman's crew's world state (DESIGN.md 4-14, M14), kept with the overworld: when they next come, tonight's
 * visit, and nights nobody watched that a village still has to find out about (worked out when it next loads).
 */
public class ThiefData extends SavedData {
    private static final String NAME = "flightsuit_thieves";

    /** Tonight's visit to one village. */
    public static final class Visit {
        public final ResourceKey<Level> dimension;
        public final BlockPos hall;
        public final String villageKey;
        public final long day;
        public final int crew;
        public final int planned;
        public boolean spawned;
        /** How many of the crew actually came in (live nights). */
        public int out;
        public boolean spotted;
        public int done;
        public int stolen;
        public int caught;
        public int escaped;

        public Visit(ResourceKey<Level> dimension, BlockPos hall, String villageKey, long day, int crew, int planned) {
            this.dimension = dimension;
            this.hall = hall.immutable();
            this.villageKey = villageKey;
            this.day = day;
            this.crew = crew;
            this.planned = planned;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.put("Hall", NbtUtils.writeBlockPos(hall));
            tag.putString("Key", villageKey);
            tag.putLong("Day", day);
            tag.putInt("Crew", crew);
            tag.putInt("Planned", planned);
            tag.putBoolean("Spawned", spawned);
            tag.putInt("Out", out);
            tag.putBoolean("Spotted", spotted);
            tag.putInt("Done", done);
            tag.putInt("Stolen", stolen);
            tag.putInt("Caught", caught);
            tag.putInt("Escaped", escaped);
            return tag;
        }

        static Visit load(CompoundTag tag) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            Visit visit = new Visit(dim, NbtUtils.readBlockPos(tag.getCompound("Hall")), tag.getString("Key"), tag.getLong("Day"),
                    tag.getInt("Crew"), tag.getInt("Planned"));
            visit.spawned = tag.getBoolean("Spawned");
            visit.out = tag.getInt("Out");
            visit.spotted = tag.getBoolean("Spotted");
            visit.done = tag.getInt("Done");
            visit.stolen = tag.getInt("Stolen");
            visit.caught = tag.getInt("Caught");
            visit.escaped = tag.getInt("Escaped");
            return visit;
        }
    }

    /** A night nobody watched: what's left to work out, and what already happened (for the news). */
    public static final class Pending {
        public int crew;
        public int remaining;
        public int stolen;
        public long day;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Crew", crew);
            tag.putInt("Remaining", remaining);
            tag.putInt("Stolen", stolen);
            tag.putLong("Day", day);
            return tag;
        }

        static Pending load(CompoundTag tag) {
            Pending pending = new Pending();
            pending.crew = tag.getInt("Crew");
            pending.remaining = tag.getInt("Remaining");
            pending.stolen = tag.getInt("Stolen");
            pending.day = tag.getLong("Day");
            return pending;
        }
    }

    public long nextVisitDay = -1L;
    public @Nullable Visit visit;
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    public static ThiefData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ThiefData::load, ThiefData::new, NAME);
    }

    public Map<String, Pending> pending() {
        return pending;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("NextVisit", nextVisitDay);
        if (visit != null) {
            tag.put("Visit", visit.save());
        }
        CompoundTag pendingTag = new CompoundTag();
        for (Map.Entry<String, Pending> entry : pending.entrySet()) {
            pendingTag.put(entry.getKey(), entry.getValue().save());
        }
        tag.put("Pending", pendingTag);
        return tag;
    }

    public static ThiefData load(CompoundTag tag) {
        ThiefData data = new ThiefData();
        data.nextVisitDay = tag.contains("NextVisit") ? tag.getLong("NextVisit") : -1L;
        if (tag.contains("Visit")) {
            data.visit = Visit.load(tag.getCompound("Visit"));
        }
        CompoundTag pendingTag = tag.getCompound("Pending");
        for (String key : pendingTag.getAllKeys()) {
            data.pending.put(key, Pending.load(pendingTag.getCompound(key)));
        }
        return data;
    }
}
