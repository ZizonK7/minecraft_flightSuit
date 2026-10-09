package com.pfkfks.flightsuit.cleaner;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One cleaning region (ported from minebutler's GuardArea): an arbitrary X/Z polygon plus a vertical range
 * around the height it was drawn at. Not limited to rectangles - you click the corners of any loop.
 */
public record CleanArea(List<BlockPos> vertices, int baseY, int upRange, int downRange) {
    public static final int DEFAULT_UP_RANGE = 8;
    public static final int DEFAULT_DOWN_RANGE = 4;

    public boolean contains(BlockPos pos) {
        if (pos.getY() < baseY - downRange || pos.getY() > baseY + upRange) {
            return false;
        }
        return containsXZ(pos.getX() + 0.5D, pos.getZ() + 0.5D);
    }

    public BlockPos minCorner() {
        int minX = vertices.get(0).getX();
        int minZ = vertices.get(0).getZ();
        for (BlockPos vertex : vertices) {
            minX = Math.min(minX, vertex.getX());
            minZ = Math.min(minZ, vertex.getZ());
        }
        return new BlockPos(minX, baseY - downRange, minZ);
    }

    public BlockPos maxCorner() {
        int maxX = vertices.get(0).getX();
        int maxZ = vertices.get(0).getZ();
        for (BlockPos vertex : vertices) {
            maxX = Math.max(maxX, vertex.getX());
            maxZ = Math.max(maxZ, vertex.getZ());
        }
        return new BlockPos(maxX, baseY + upRange, maxZ);
    }

    /** Even-odd ray casting point-in-polygon test on block centers. */
    private boolean containsXZ(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            BlockPos vi = vertices.get(i);
            BlockPos vj = vertices.get(j);
            double zi = vi.getZ() + 0.5D;
            double zj = vj.getZ() + 0.5D;
            if ((zi > z) != (zj > z)) {
                double xi = vi.getX() + 0.5D;
                double xj = vj.getX() + 0.5D;
                double intersectX = (xj - xi) * (z - zi) / (zj - zi) + xi;
                if (x < intersectX) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag points = new ListTag();
        for (BlockPos vertex : vertices) {
            points.add(LongTag.valueOf(vertex.asLong()));
        }
        tag.put("Vertices", points);
        tag.putInt("BaseY", baseY);
        tag.putInt("Up", upRange);
        tag.putInt("Down", downRange);
        return tag;
    }

    public static CleanArea load(CompoundTag tag) {
        List<BlockPos> vertices = new ArrayList<>();
        for (Tag point : tag.getList("Vertices", Tag.TAG_LONG)) {
            vertices.add(BlockPos.of(((LongTag) point).getAsLong()));
        }
        return new CleanArea(vertices, tag.getInt("BaseY"), tag.getInt("Up"), tag.getInt("Down"));
    }
}
