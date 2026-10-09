package com.pfkfks.flightsuit.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * v1 "wireless" grid (DESIGN.md 4-8): no cables - every power block automatically trades energy with
 * other power blocks within {@link #RADIUS} blocks. Producers (solar, generator) feed stations first and
 * batteries second; batteries only feed stations, so energy never ping-pongs between batteries.
 *
 * Nodes register themselves while loaded, so a producer only scans the handful of nodes in its level
 * instead of the blocks around it.
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

    /**
     * Pushes up to {@code maxOut} FE from {@code source} into nearby nodes of the given roles, in order.
     * @return energy actually moved.
     */
    public static int distribute(Level level, Node source, int maxOut, Role... targets) {
        int budget = Math.min(maxOut, source.energy().getEnergyStored());
        int moved = 0;
        for (Role role : targets) {
            for (Node target : nodesNear(level, source.self().getBlockPos(), role)) {
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
