package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-side lookup of the loaded village halls, so an explosion or a resident can find "its" village
 * without scanning chunks. Halls add themselves on load and drop out when unloaded or broken.
 */
public final class Villages {
    private static final Map<Level, Set<VillageHallBlockEntity>> HALLS = new WeakHashMap<>();

    private Villages() {
    }

    static void add(VillageHallBlockEntity hall) {
        HALLS.computeIfAbsent(hall.getLevel(), level -> Collections.newSetFromMap(new WeakHashMap<>())).add(hall);
    }

    static void remove(VillageHallBlockEntity hall) {
        Set<VillageHallBlockEntity> halls = HALLS.get(hall.getLevel());
        if (halls != null) {
            halls.remove(hall);
        }
    }

    /** The hall whose village covers this spot (the closest one if two overlap), if it is loaded. */
    public static @Nullable VillageHallBlockEntity containing(Level level, BlockPos pos) {
        Set<VillageHallBlockEntity> halls = HALLS.get(level);
        if (halls == null) {
            return null;
        }
        VillageHallBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (VillageHallBlockEntity hall : halls) {
            if (!hall.isRemoved() && hall.contains(pos)) {
                double dist = hall.getBlockPos().distSqr(pos);
                if (dist < bestDist) {
                    best = hall;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    public static @Nullable VillageHallBlockEntity hallAt(Level level, @Nullable BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof VillageHallBlockEntity hall ? hall : null;
    }

    /** A block of some village broke in a fight: note it for the builders. */
    public static void recordDamage(Level level, BlockPos pos, BlockState state) {
        VillageHallBlockEntity hall = containing(level, pos);
        if (hall != null && !pos.equals(hall.getBlockPos())) {
            hall.recordDamage(pos, state);
        }
    }
}
