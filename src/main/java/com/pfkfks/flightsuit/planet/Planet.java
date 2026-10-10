package com.pfkfks.flightsuit.planet;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Where the spaceship can fly (DESIGN.md 4-16, M15). Each planet is its own dimension
 * (data/flightsuit/dimension/<id>.json) - an open world with a main story on top: Dragon Ball Earth, Titan
 * (the Thanos saga) and Namek (Dragon Ball chapter 2, opened once chapter 1 is done).
 */
public enum Planet {
    DBZ_EARTH("dbz_earth"),
    TITAN("titan"),
    /** Dragon Ball chapter 2 (after the M16 test): its own biome - green sky, teal water, blue-green grass. */
    NAMEK("namek");

    private final String id;
    private final ResourceKey<Level> dimension;

    Planet(String id) {
        this.id = id;
        this.dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(FlightSuitMod.MODID, id));
    }

    public String id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Component displayName() {
        return Component.translatable("planet.flightsuit." + id).withStyle(ChatFormatting.GOLD);
    }

    public static @Nullable Planet byId(String id) {
        for (Planet planet : values()) {
            if (planet.id.equals(id)) {
                return planet;
            }
        }
        return null;
    }

    public static @Nullable Planet of(ResourceKey<Level> dimension) {
        for (Planet planet : values()) {
            if (planet.dimension.equals(dimension)) {
                return planet;
            }
        }
        return null;
    }
}
