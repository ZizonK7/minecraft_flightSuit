package com.pfkfks.flightsuit.doom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Doom saga's state (DESIGN.md 4-17), kept with the overworld: Battleworld's sights (where each was built), and
 * per player how far they are in the story (DoomSaga.Stage, by name), which world cores they've freed, the gate
 * they came through (to go back to), and when the incursion last shook their world.
 */
public class DoomData extends SavedData {
    private static final String NAME = "flightsuit_doom";

    /** Where a player stepped into a gate from: going back through any gate in Battleworld lands them there. */
    public record Origin(ResourceKey<Level> dimension, BlockPos gate) {
    }

    public static final class Walker {
        /** DoomSaga.Stage name ("" before the story starts). */
        public String stage = "";
        /** World cores freed (DoomSaga.Domain bits). */
        public int cores;
        public @Nullable Origin origin;
        /** Overworld day the incursion is due (the prologue), 0 = not set. */
        public long incursionDay;
        /** Rematches with Doom won after the story (Castle Doom keeps its lord). */
        public int rematches;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Stage", stage);
            tag.putInt("Cores", cores);
            if (origin != null) {
                tag.putString("OriginDim", origin.dimension().location().toString());
                tag.putLong("OriginPos", origin.gate().asLong());
            }
            tag.putLong("IncursionDay", incursionDay);
            tag.putInt("Rematches", rematches);
            return tag;
        }

        static Walker load(CompoundTag tag) {
            Walker walker = new Walker();
            walker.stage = tag.getString("Stage");
            walker.cores = tag.getInt("Cores");
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("OriginDim"));
            if (dim != null && tag.contains("OriginPos")) {
                walker.origin = new Origin(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("OriginPos")));
            }
            walker.incursionDay = tag.getLong("IncursionDay");
            walker.rematches = tag.getInt("Rematches");
            return walker;
        }
    }

    private final Map<UUID, Walker> walkers = new HashMap<>();
    /** Battleworld's sights by id (DoomSaga.Site): where each was built. */
    private final CompoundTag world = new CompoundTag();

    public static DoomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DoomData::load, DoomData::new, NAME);
    }

    public Walker walker(UUID player) {
        return walkers.computeIfAbsent(player, id -> new Walker());
    }

    public CompoundTag world() {
        return world;
    }

    public @Nullable BlockPos site(String id) {
        return world.contains(id) ? BlockPos.of(world.getLong(id)) : null;
    }

    public void setSite(String id, BlockPos pos) {
        world.putLong(id, pos.asLong());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag people = new CompoundTag();
        walkers.forEach((id, walker) -> people.put(id.toString(), walker.save()));
        tag.put("Walkers", people);
        tag.put("World", world.copy());
        return tag;
    }

    public static DoomData load(CompoundTag tag) {
        DoomData data = new DoomData();
        CompoundTag people = tag.getCompound("Walkers");
        for (String key : people.getAllKeys()) {
            try {
                data.walkers.put(UUID.fromString(key), Walker.load(people.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        CompoundTag world = tag.getCompound("World");
        for (String key : world.getAllKeys()) {
            data.world.put(key, world.get(key).copy());
        }
        return data;
    }
}
