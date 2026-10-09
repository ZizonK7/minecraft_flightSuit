package com.pfkfks.flightsuit.hero;

import com.pfkfks.flightsuit.war.FortressBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out Hero City (DESIGN.md 4-13, M13): a very advanced little city - a concrete plaza with roads, the
 * Hero HQ tower in the middle (glass bands, a landing pad on top, the vault in its lobby), three glass
 * skyscrapers, a park with a fountain, street lights and a low quartz wall. Same placement format as the
 * fortresses (FortressBuilder.Placement).
 */
public final class HeroCityBuilder {
    public static final int EDGE = 30;
    private static final int CLEAR_HEIGHT = 40;

    private final BlockPos origin;
    private final List<FortressBuilder.Placement> out = new ArrayList<>();
    private final List<BlockPos> vault = new ArrayList<>();

    private HeroCityBuilder(BlockPos origin) {
        this.origin = origin;
    }

    public static HeroCityBuilder plan(BlockPos origin) {
        HeroCityBuilder builder = new HeroCityBuilder(origin);
        builder.ground();
        builder.headquarters();
        builder.skyscraper(-19, 12, 9, 22);
        builder.skyscraper(19, 12, 9, 28);
        builder.skyscraper(19, -17, 7, 18);
        builder.park(-17, -16);
        builder.lights();
        builder.wall();
        return builder;
    }

    public List<FortressBuilder.Placement> placements() {
        return out;
    }

    public List<BlockPos> vault() {
        return vault;
    }

    /** Where Captain and Iron Man stand: the HQ lobby. */
    public static BlockPos lobby(BlockPos origin) {
        return origin.offset(0, 1, 0);
    }

    private void put(int x, int y, int z, BlockState state) {
        out.add(new FortressBuilder.Placement(origin.offset(x, y, z), state, false));
    }

    private void fill(int x, int y, int z, BlockState state) {
        out.add(new FortressBuilder.Placement(origin.offset(x, y, z), state, true));
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    put(x, y, z, state);
                }
            }
        }
    }

    private void ground() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = CLEAR_HEIGHT; y >= 1; y--) {
            box(-EDGE, y, -EDGE, EDGE, y, EDGE, air);
        }
        BlockState base = Blocks.STONE.defaultBlockState();
        for (int y = -5; y <= -1; y++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                for (int x = -EDGE; x <= EDGE; x++) {
                    fill(x, y, z, base);
                }
            }
        }
        BlockState plaza = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState road = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState line = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int z = -EDGE; z <= EDGE; z++) {
            for (int x = -EDGE; x <= EDGE; x++) {
                boolean roadX = Math.abs(z) <= 2;
                boolean roadZ = Math.abs(x) <= 2;
                BlockState state = plaza;
                if (roadX || roadZ) {
                    state = (roadX && z == 0 && x % 4 != 0) || (roadZ && x == 0 && z % 4 != 0) ? line : road;
                }
                put(x, 0, z, state);
            }
        }
    }

    /** The HQ tower: 11x11, glass bands every other floor, the vault in the lobby, a landing pad on the roof. */
    private void headquarters() {
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        int r = 5;
        int top = 34;
        for (int y = 1; y <= top; y++) {
            int storey = (y - 1) / 5;
            boolean band = storey % 2 == 1 && (y - 1) % 5 != 0;
            for (int i = -r; i <= r; i++) {
                boolean corner = Math.abs(i) == r;
                BlockState wall = corner || !band ? white : glass;
                put(i, y, -r, wall);
                put(i, y, r, wall);
                put(-r, y, i, wall);
                put(r, y, i, wall);
            }
            if (y % 5 == 0 && y < top) {
                box(-r + 1, y, -r + 1, r - 1, y, r - 1, floor);
            }
        }
        // Lobby: a wide glass entrance facing south, sea lanterns overhead, the vault at the back.
        box(-2, 1, r, 2, 3, r, Blocks.AIR.defaultBlockState());
        box(-1, 4, r, 1, 4, r, glass);
        box(-r + 1, 1, -r + 1, r - 1, 3, r - 1, Blocks.AIR.defaultBlockState());
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{-3, 3}) {
                put(x, 4, z, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        for (int x : new int[]{-2, 2}) {
            put(x, 1, -r + 1, chest);
            vault.add(origin.offset(x, 1, -r + 1));
        }
        put(0, 1, -r + 1, Blocks.IRON_BLOCK.defaultBlockState());
        put(0, 2, -r + 1, Blocks.BEACON.defaultBlockState());
        // Roof: landing pad with an "H", lit edges, an antenna.
        box(-r - 1, top + 1, -r - 1, r + 1, top + 1, r + 1, Blocks.IRON_BLOCK.defaultBlockState());
        for (int i = -r - 1; i <= r + 1; i += 2) {
            put(i, top + 2, -r - 1, Blocks.SEA_LANTERN.defaultBlockState());
            put(i, top + 2, r + 1, Blocks.SEA_LANTERN.defaultBlockState());
            put(-r - 1, top + 2, i, Blocks.SEA_LANTERN.defaultBlockState());
            put(r + 1, top + 2, i, Blocks.SEA_LANTERN.defaultBlockState());
        }
        BlockState mark = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int z = -2; z <= 2; z++) {
            put(-2, top + 1, z, mark);
            put(2, top + 1, z, mark);
        }
        put(-1, top + 1, 0, mark);
        put(0, top + 1, 0, mark);
        put(1, top + 1, 0, mark);
        for (int y = top + 2; y <= top + 7; y++) {
            put(r, y, r, Blocks.IRON_BARS.defaultBlockState());
        }
        put(r, top + 8, r, Blocks.REDSTONE_LAMP.defaultBlockState());
    }

    /** A glass tower with white corners and a roof lantern ring. */
    private void skyscraper(int cx, int cz, int size, int height) {
        int r = size / 2;
        BlockState glass = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int y = 1; y <= height; y++) {
            boolean slab = y % 4 == 0;
            for (int i = -r; i <= r; i++) {
                boolean corner = Math.abs(i) == r;
                BlockState wall = corner || slab ? white : glass;
                put(cx + i, y, cz - r, wall);
                put(cx + i, y, cz + r, wall);
                put(cx - r, y, cz + i, wall);
                put(cx + r, y, cz + i, wall);
            }
        }
        box(cx - r, height + 1, cz - r, cx + r, height + 1, cz + r, white);
        put(cx, height + 2, cz, Blocks.SEA_LANTERN.defaultBlockState());
        // Ground-floor doors on all sides.
        put(cx, 1, cz - r, Blocks.AIR.defaultBlockState());
        put(cx, 2, cz - r, Blocks.AIR.defaultBlockState());
        put(cx, 1, cz + r, Blocks.AIR.defaultBlockState());
        put(cx, 2, cz + r, Blocks.AIR.defaultBlockState());
    }

    /** A grass park with a few trees and a quartz fountain. */
    private void park(int cx, int cz) {
        box(cx - 6, 0, cz - 6, cx + 6, 0, cz + 6, Blocks.GRASS_BLOCK.defaultBlockState());
        box(cx - 2, 1, cz - 2, cx + 2, 1, cz + 2, Blocks.QUARTZ_BLOCK.defaultBlockState());
        box(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, Blocks.WATER.defaultBlockState());
        put(cx, 2, cz, Blocks.QUARTZ_PILLAR.defaultBlockState());
        put(cx, 3, cz, Blocks.SEA_LANTERN.defaultBlockState());
        for (int[] tree : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            int x = cx + tree[0];
            int z = cz + tree[1];
            for (int y = 1; y <= 4; y++) {
                put(x, y, z, Blocks.OAK_LOG.defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(x + dx, 5, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
                    if (dx != 0 || dz != 0) {
                        put(x + dx, 4, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
                    }
                }
            }
            put(x, 6, z, Blocks.OAK_LEAVES.defaultBlockState());
        }
    }

    private void lights() {
        for (int i = -24; i <= 24; i += 8) {
            for (int[] at : new int[][]{{i, -4}, {i, 4}, {-4, i}, {4, i}}) {
                if (Math.abs(at[0]) <= 6 && Math.abs(at[1]) <= 6) {
                    continue;
                }
                put(at[0], 1, at[1], Blocks.IRON_BARS.defaultBlockState());
                put(at[0], 2, at[1], Blocks.IRON_BARS.defaultBlockState());
                put(at[0], 3, at[1], Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }

    private void wall() {
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        for (int i = -EDGE; i <= EDGE; i++) {
            if (Math.abs(i) <= 3) {
                continue;
            }
            put(i, 1, -EDGE, quartz);
            put(i, 1, EDGE, quartz);
            put(-EDGE, 1, i, quartz);
            put(EDGE, 1, i, quartz);
        }
    }
}
