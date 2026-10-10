package com.pfkfks.flightsuit.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Space travel state (DESIGN.md 4-16, M15), kept with the overworld: each planet's landing site, and per player
 * the launch pad to come home to, and how far they are in each planet's story (Story).
 */
public class PlanetData extends SavedData {
    private static final String NAME = "flightsuit_planets";

    public record Home(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** One player's progress on the planets. */
    public static final class Traveller {
        public @Nullable Home home;
        /** Story stage per planet by ordinal - how it was saved before M17 (PlanetStory converts it on reading). */
        public final Map<Planet, Integer> stage = new EnumMap<>(Planet.class);
        /** M17: story stage per planet by name (PlanetStory.DbzStage). */
        public final Map<Planet, String> stageName = new EnumMap<>(Planet.class);
        /** Day the next story beat may happen (e.g. the Saiyans land a day after Raditz). */
        public long nextBeatDay;
        /** Chapters cleared, per planet. */
        public final Map<Planet, Integer> cleared = new EnumMap<>(Planet.class);
        /** Infinity Stones this player has won (thanos.InfinityStone bits) - Thanos comes for them. */
        public int stones;
        /** M17: cutscenes this player has watched (each plays once; "/planet replay" shows one again). */
        public final java.util.Set<String> seenCutscenes = new java.util.LinkedHashSet<>();

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (home != null) {
                tag.putString("HomeDim", home.dimension().location().toString());
                tag.putLong("HomePos", home.pos().asLong());
            }
            CompoundTag stages = new CompoundTag();
            stage.forEach((planet, value) -> stages.putInt(planet.id(), value));
            tag.put("Stage", stages);
            CompoundTag names = new CompoundTag();
            stageName.forEach((planet, value) -> names.putString(planet.id(), value));
            tag.put("StageName", names);
            CompoundTag clears = new CompoundTag();
            cleared.forEach((planet, value) -> clears.putInt(planet.id(), value));
            tag.put("Cleared", clears);
            tag.putLong("NextBeat", nextBeatDay);
            tag.putInt("Stones", stones);
            net.minecraft.nbt.ListTag seen = new net.minecraft.nbt.ListTag();
            seenCutscenes.forEach(id -> seen.add(net.minecraft.nbt.StringTag.valueOf(id)));
            tag.put("Seen", seen);
            return tag;
        }

        static Traveller load(CompoundTag tag) {
            Traveller traveller = new Traveller();
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("HomeDim"));
            if (tag.contains("HomePos") && dim != null) {
                traveller.home = new Home(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("HomePos")));
            }
            CompoundTag stages = tag.getCompound("Stage");
            CompoundTag names = tag.getCompound("StageName");
            CompoundTag clears = tag.getCompound("Cleared");
            for (Planet planet : Planet.values()) {
                if (names.contains(planet.id())) {
                    traveller.stageName.put(planet, names.getString(planet.id()));
                }
                if (stages.contains(planet.id())) {
                    traveller.stage.put(planet, stages.getInt(planet.id()));
                }
                if (clears.contains(planet.id())) {
                    traveller.cleared.put(planet, clears.getInt(planet.id()));
                }
            }
            traveller.nextBeatDay = tag.getLong("NextBeat");
            traveller.stones = tag.getInt("Stones");
            net.minecraft.nbt.ListTag seen = tag.getList("Seen", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < seen.size(); i++) {
                traveller.seenCutscenes.add(seen.getString(i));
            }
            return traveller;
        }
    }

    private final Map<Planet, BlockPos> sites = new EnumMap<>(Planet.class);
    private final Map<UUID, Traveller> travellers = new HashMap<>();
    /** Free-form per-planet world state (landmarks built, dragon balls...), each feature keeps its own tag. */
    private final Map<Planet, CompoundTag> world = new EnumMap<>(Planet.class);

    public static PlanetData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlanetData::load, PlanetData::new, NAME);
    }

    public @Nullable BlockPos site(Planet planet) {
        return sites.get(planet);
    }

    public void setSite(Planet planet, BlockPos pos) {
        sites.put(planet, pos.immutable());
        setDirty();
    }

    public Traveller traveller(UUID player) {
        return travellers.computeIfAbsent(player, id -> new Traveller());
    }

    public CompoundTag world(Planet planet) {
        return world.computeIfAbsent(planet, p -> new CompoundTag());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag siteTag = new CompoundTag();
        sites.forEach((planet, pos) -> siteTag.putLong(planet.id(), pos.asLong()));
        tag.put("Sites", siteTag);
        CompoundTag people = new CompoundTag();
        travellers.forEach((id, traveller) -> people.put(id.toString(), traveller.save()));
        tag.put("Travellers", people);
        CompoundTag worldTag = new CompoundTag();
        world.forEach((planet, data) -> worldTag.put(planet.id(), data));
        tag.put("World", worldTag);
        return tag;
    }

    public static PlanetData load(CompoundTag tag) {
        PlanetData data = new PlanetData();
        CompoundTag siteTag = tag.getCompound("Sites");
        CompoundTag worldTag = tag.getCompound("World");
        for (Planet planet : Planet.values()) {
            if (siteTag.contains(planet.id())) {
                data.sites.put(planet, BlockPos.of(siteTag.getLong(planet.id())));
            }
            if (worldTag.contains(planet.id())) {
                data.world.put(planet, worldTag.getCompound(planet.id()));
            }
        }
        CompoundTag people = tag.getCompound("Travellers");
        for (String key : people.getAllKeys()) {
            try {
                data.travellers.put(UUID.fromString(key), Traveller.load(people.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID - skip it.
            }
        }
        return data;
    }
}
