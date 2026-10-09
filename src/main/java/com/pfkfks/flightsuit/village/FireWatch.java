package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fire inside the village (DESIGN.md 4-11 화공): fire leaves no trace of what it ate, so around every blaze the
 * hall keeps a snapshot of the flammable blocks; any that burn away go on the ledger for the builders. The
 * fires themselves are what the guards run to put out (GuardFirefightGoal). Spreading fire is followed: a
 * blaze found at the edge of a watched spot becomes a watched spot of its own.
 */
public class FireWatch {
    private static final int RADIUS = 3;
    private static final int MAX_SPOTS = 48;
    /** A spot with no fire left for this long is dropped (with its snapshot). */
    private static final long QUIET_TICKS = 20 * 20;

    /** Watched spot -> game time a fire was last seen there. */
    private final Map<BlockPos, Long> spots = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> snapshot = new HashMap<>();
    private final List<BlockPos> fires = new ArrayList<>();

    public void watch(BlockPos pos, long now) {
        if (spots.size() < MAX_SPOTS) {
            spots.put(pos.immutable(), now);
        }
    }

    public boolean isEmpty() {
        return spots.isEmpty();
    }

    /** Burning blocks seen at the last scan. */
    public List<BlockPos> fires() {
        return fires;
    }

    public @Nullable BlockPos nearestFire(BlockPos from) {
        BlockPos best = null;
        for (BlockPos fire : fires) {
            if (best == null || fire.distSqr(from) < best.distSqr(from)) {
                best = fire;
            }
        }
        return best;
    }

    /** Every second, from the hall's tick. */
    void tick(Level level, VillageHallBlockEntity hall) {
        fires.clear();
        if (spots.isEmpty()) {
            snapshot.clear();
            return;
        }
        long now = level.getGameTime();
        List<BlockPos> newSpots = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Long>> it = spots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> spot = it.next();
            BlockPos center = spot.getKey();
            if (!level.isLoaded(center)) {
                continue;
            }
            boolean burning = false;
            for (BlockPos scan : BlockPos.betweenClosed(center.offset(-RADIUS, -2, -RADIUS), center.offset(RADIUS, 3, RADIUS))) {
                BlockState state = level.getBlockState(scan);
                BlockPos pos = scan.immutable();
                if (state.getBlock() instanceof BaseFireBlock) {
                    burning = true;
                    if (!fires.contains(pos) && hall.contains(pos)) {
                        fires.add(pos);
                    }
                    boolean edge = Math.abs(pos.getX() - center.getX()) >= RADIUS || Math.abs(pos.getZ() - center.getZ()) >= RADIUS;
                    if (edge && !spots.containsKey(pos) && hall.contains(pos)) {
                        newSpots.add(pos);
                    }
                    continue;
                }
                BlockState before = snapshot.get(pos);
                if (before != null && (state.isAir() || state.getBlock() instanceof BaseFireBlock)) {
                    // It was here a moment ago and the fire took it.
                    hall.recordDamage(pos, before);
                    snapshot.remove(pos);
                } else if (!state.isAir() && state.isFlammable(level, pos, Direction.UP) && hall.contains(pos)) {
                    snapshot.put(pos, state);
                }
            }
            if (burning) {
                spot.setValue(now);
            } else if (now - spot.getValue() > QUIET_TICKS) {
                it.remove();
            }
        }
        for (BlockPos pos : newSpots) {
            watch(pos, now);
        }
        if (spots.isEmpty()) {
            snapshot.clear();
        }
    }
}
