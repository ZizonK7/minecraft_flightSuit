package com.pfkfks.flightsuit.energy;

import com.pfkfks.flightsuit.block.CableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The power grid (DESIGN.md 4-8): every power block trades energy with the others within {@link #RADIUS} blocks
 * without any wiring (v1), and since M18 ("공장식 전력") also with whatever a run of cables (CableBlock) leads to,
 * however far. Producers (solar, generator, the power plant) feed stations first and batteries second; batteries
 * only feed stations, so energy never ping-pongs between batteries.
 *
 * Nodes register themselves while loaded, so a producer only scans the handful of nodes in its level instead of
 * the blocks around it. Cable runs are traced from a block's six sides (cable to cable, up to CABLE_MAX_RUN
 * cables) and the ends remembered for a few seconds, or until any cable anywhere is placed or broken.
 */
public final class PowerGrid {
    public static final int RADIUS = 8;

    public enum Role {
        PRODUCER,
        STORAGE,
        CONSUMER
    }

    public interface Node {
        Role powerRole();

        NodeEnergyStorage energy();

        default BlockEntity self() {
            return (BlockEntity) this;
        }
    }

    // BlockEntity uses identity equality/hash, so a weak identity-style set is safe here.
    private static final Map<Level, Set<Node>> NODES = new WeakHashMap<>();

    /** A traced cable run from one block: the node positions at its ends, for which cable layout, traced when. */
    private record Linked(int version, long tick, List<BlockPos> ends) {
    }

    private static final Map<Level, Map<BlockPos, Linked>> LINKED = new WeakHashMap<>();
    /** Bumped whenever a cable is placed or broken: every remembered run is traced afresh. */
    private static int cableVersion;
    private static final int LINK_MEMORY_TICKS = 100;

    private PowerGrid() {
    }

    public static void add(Level level, Node node) {
        if (!level.isClientSide) {
            NODES.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(node);
        }
    }

    public static void remove(Level level, Node node) {
        Set<Node> nodes = NODES.get(level);
        if (nodes != null) {
            nodes.remove(node);
        }
    }

    /** A cable went in or came out somewhere: the remembered runs are stale. */
    public static void cablesChanged() {
        cableVersion++;
    }

    public static List<Node> nodesNear(Level level, BlockPos pos, Role role) {
        Set<Node> nodes = NODES.get(level);
        List<Node> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (Node node : nodes) {
            BlockEntity be = node.self();
            if (node.powerRole() == role && !be.isRemoved() && be.getBlockPos().closerThan(pos, RADIUS + 0.5D)) {
                result.add(node);
            }
        }
        return result;
    }

    /** Every loaded node of {@code role} within {@code distance} blocks of {@code pos} (the village's engineer looks this way). */
    public static List<Node> nodesWithin(Level level, BlockPos pos, double distance, Role role) {
        Set<Node> nodes = NODES.get(level);
        List<Node> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (Node node : nodes) {
            BlockEntity be = node.self();
            if (node.powerRole() == role && !be.isRemoved() && be.getBlockPos().closerThan(pos, distance)) {
                result.add(node);
            }
        }
        return result;
    }

    /** The nodes of {@code role} a cable run from the block at {@code pos} leads to (M18). */
    public static List<Node> nodesLinked(Level level, BlockPos pos, Role role) {
        List<Node> result = new ArrayList<>();
        if (level.isClientSide) {
            return result;
        }
        Map<BlockPos, Linked> cache = LINKED.computeIfAbsent(level, l -> new HashMap<>());
        Linked linked = cache.get(pos);
        long now = level.getGameTime();
        if (linked == null || linked.version() != cableVersion || now - linked.tick() > LINK_MEMORY_TICKS) {
            linked = new Linked(cableVersion, now, trace(level, pos));
            cache.put(pos.immutable(), linked);
        }
        for (BlockPos end : linked.ends()) {
            if (level.isLoaded(end) && level.getBlockEntity(end) instanceof Node node && node.powerRole() == role && !node.self().isRemoved()) {
                result.add(node);
            }
        }
        return result;
    }

    /** Follows the cables touching {@code from}: every power block a cable touches on the way, nearest first. */
    private static List<BlockPos> trace(Level level, BlockPos from) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> ends = new LinkedHashSet<>();
        for (Direction side : Direction.values()) {
            BlockPos next = from.relative(side);
            if (isCable(level, next) && seen.add(next)) {
                queue.add(next);
            }
        }
        while (!queue.isEmpty() && seen.size() <= PowerTuning.CABLE_MAX_RUN) {
            BlockPos cable = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = cable.relative(side);
                if (next.equals(from) || !level.isLoaded(next)) {
                    continue;
                }
                if (isCable(level, next)) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                } else if (level.getBlockEntity(next) instanceof Node) {
                    ends.add(next.immutable());
                }
            }
        }
        return new ArrayList<>(ends);
    }

    private static boolean isCable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CableBlock;
    }

    /**
     * Pushes up to {@code maxOut} FE from {@code source} into the nodes of the given roles, in order - the ones in
     * wireless reach first, then the ones down its cables.
     * @return energy actually moved.
     */
    public static int distribute(Level level, Node source, int maxOut, Role... targets) {
        int budget = Math.min(maxOut, source.energy().getEnergyStored());
        int moved = 0;
        BlockPos at = source.self().getBlockPos();
        for (Role role : targets) {
            List<Node> reach = nodesNear(level, at, role);
            for (Node linked : nodesLinked(level, at, role)) {
                if (!reach.contains(linked)) {
                    reach.add(linked);
                }
            }
            for (Node target : reach) {
                if (target == source || budget <= 0) {
                    continue;
                }
                int accepted = target.energy().receiveEnergy(budget, false);
                if (accepted > 0) {
                    source.energy().consume(accepted);
                    budget -= accepted;
                    moved += accepted;
                }
            }
        }
        return moved;
    }
}
