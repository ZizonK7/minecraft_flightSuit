package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a Three Kingdoms fortress (DESIGN.md 4-11 성채, M12) as an ordered list of block placements (and the supply chests, {@link #chests}) around a
 * centre on the ground: the ground levelled, a stone-brick curtain wall with four gates and corner towers
 * flying the kingdom's colours, a palace with a tiled roof and a throne (where the ruler sits), two barracks,
 * a storehouse with two chests of supplies, lamp posts and a training yard.
 *
 * Coordinates are relative: x east, z south, y = 0 the ground. The palace is to the north, the storehouse to
 * the south, barracks east and west.
 */
public final class FortressBuilder {
    /** Half the wall's side. */
    public static final int WALL = 20;
    /** Half the levelled area's side (the wall plus a margin). */
    public static final int CLEAR = 24;
    private static final int CLEAR_HEIGHT = 22;

    /**
     * @param fillOnly only placed where the spot is empty (air, water, grass) - the foundation under the ground
     *                 shouldn't eat the caves' walls or ores.
     */
    public record Placement(BlockPos pos, BlockState state, boolean fillOnly) {
    }

    private final BlockPos origin;
    private final BlockState trim;
    private final BlockState banner;
    private final List<Placement> out = new ArrayList<>();
    private final List<BlockPos> chests = new ArrayList<>();

    private FortressBuilder(Kingdom kingdom, BlockPos origin) {
        this.origin = origin;
        this.trim = switch (kingdom) {
            case WEI -> Blocks.BLUE_CONCRETE.defaultBlockState();
            case SHU -> Blocks.GREEN_CONCRETE.defaultBlockState();
            case WU -> Blocks.RED_CONCRETE.defaultBlockState();
        };
        this.banner = switch (kingdom) {
            case WEI -> Blocks.BLUE_WOOL.defaultBlockState();
            case SHU -> Blocks.GREEN_WOOL.defaultBlockState();
            case WU -> Blocks.RED_WOOL.defaultBlockState();
        };
    }

    /** The full plan, in build order. */
    public static FortressBuilder plan(Kingdom kingdom, BlockPos origin) {
        FortressBuilder builder = new FortressBuilder(kingdom, origin);
        builder.ground();
        builder.walls();
        builder.towers();
        builder.palace();
        builder.barracks(-12);
        builder.barracks(12);
        builder.storehouse();
        builder.yard();
        return builder;
    }

    public List<Placement> placements() {
        return out;
    }

    public List<BlockPos> chests() {
        return chests;
    }

    /** Where the ruler stands (in front of the throne). */
    public static BlockPos throne(BlockPos origin) {
        return origin.offset(0, 2, -11);
    }

    /** Where the generals stand (in front of the palace). */
    public static BlockPos palaceSteps(BlockPos origin) {
        return origin.offset(0, 1, -3);
    }

    // ---------------------------------------------------------------- pieces

    private void put(int x, int y, int z, BlockState state) {
        out.add(new Placement(origin.offset(x, y, z), state, false));
    }

    private void fill(int x, int y, int z, BlockState state) {
        out.add(new Placement(origin.offset(x, y, z), state, true));
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

    /** Hollow walls only (no floor or ceiling). */
    private void ring(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (x == x0 || x == x1 || z == z0 || z == z1) {
                        put(x, y, z, state);
                    }
                }
            }
        }
    }

    /** Clear the hills and trees, fill the hollows, lay grass with stone roads to the four gates. */
    private void ground() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = CLEAR_HEIGHT; y >= 1; y--) {
            box(-CLEAR, y, -CLEAR, CLEAR, y, CLEAR, air);
        }
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int y = -5; y <= -1; y++) {
            for (int z = -CLEAR; z <= CLEAR; z++) {
                for (int x = -CLEAR; x <= CLEAR; x++) {
                    fill(x, y, z, dirt);
                }
            }
        }
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState road = Blocks.STONE_BRICKS.defaultBlockState();
        for (int z = -CLEAR; z <= CLEAR; z++) {
            for (int x = -CLEAR; x <= CLEAR; x++) {
                boolean isRoad = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && Math.max(Math.abs(x), Math.abs(z)) <= WALL + 2;
                put(x, 0, z, isRoad ? road : grass);
            }
        }
    }

    private void walls() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int i = -WALL; i <= WALL; i++) {
            for (int y = 1; y <= 4; y++) {
                BlockState state = (i * 7 + y * 3) % 11 == 0 ? mossy : brick;
                put(i, y, -WALL, state);
                put(i, y, WALL, state);
                put(-WALL, y, i, state);
                put(WALL, y, i, state);
            }
            if (i % 2 == 0) {
                put(i, 5, -WALL, brick);
                put(i, 5, WALL, brick);
                put(-WALL, 5, i, brick);
                put(WALL, 5, i, brick);
            }
        }
        // Gates: three wide, three high, the kingdom's colour over each.
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int a = -1; a <= 1; a++) {
            for (int y = 1; y <= 3; y++) {
                put(a, y, -WALL, air);
                put(a, y, WALL, air);
                put(-WALL, y, a, air);
                put(WALL, y, a, air);
            }
            put(a, 4, -WALL, trim);
            put(a, 4, WALL, trim);
            put(-WALL, 4, a, trim);
            put(WALL, 4, a, trim);
        }
        // Banners on poles either side of each gate.
        for (int[] gate : new int[][]{{0, -WALL - 1}, {0, WALL + 1}, {-WALL - 1, 0}, {WALL + 1, 0}}) {
            boolean alongX = gate[1] != 0;
            for (int side = -1; side <= 1; side += 2) {
                int x = gate[0] + (alongX ? side * 3 : 0);
                int z = gate[1] + (alongX ? 0 : side * 3);
                for (int y = 1; y <= 4; y++) {
                    put(x, y, z, Blocks.OAK_FENCE.defaultBlockState());
                }
                put(x, 5, z, banner);
                put(x, 6, z, banner);
            }
        }
    }

    private void towers() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * WALL;
                int cz = sz * WALL;
                ring(cx - 2, 1, cz - 2, cx + 2, 7, cz + 2, brick);
                box(cx - 1, 1, cz - 1, cx + 1, 6, cz + 1, Blocks.AIR.defaultBlockState());
                box(cx - 2, 8, cz - 2, cx + 2, 8, cz + 2, brick);
                for (int i = -2; i <= 2; i += 2) {
                    put(cx + i, 9, cz - 2, brick);
                    put(cx + i, 9, cz + 2, brick);
                    put(cx - 2, 9, cz + i, brick);
                    put(cx + 2, 9, cz + i, brick);
                }
                put(cx, 9, cz, Blocks.OAK_FENCE.defaultBlockState());
                put(cx, 10, cz, Blocks.OAK_FENCE.defaultBlockState());
                put(cx, 11, cz, banner);
                put(cx, 12, cz, banner);
                put(cx, 7, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
    }

    /** The palace (north): raised floor, coloured pillars, dark timber walls, three-tier tiled roof, a throne. */
    private void palace() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wood = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        int x0 = -7;
        int x1 = 7;
        int z0 = -14;
        int z1 = -6;
        box(x0, 1, z0, x1, 1, z1 + 1, brick);
        put(-1, 1, z1 + 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        put(0, 1, z1 + 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        put(1, 1, z1 + 2, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        ring(x0, 2, z0, x1, 5, z1, wood);
        for (int x : new int[]{x0, -3, 3, x1}) {
            for (int z : new int[]{z0, z1}) {
                box(x, 2, z, x, 5, z, trim);
            }
        }
        // Door and windows in the front wall.
        box(-1, 2, z1, 1, 4, z1, Blocks.AIR.defaultBlockState());
        for (int x : new int[]{-5, 5}) {
            put(x, 3, z1, Blocks.GLASS_PANE.defaultBlockState());
            put(x, 4, z1, Blocks.GLASS_PANE.defaultBlockState());
        }
        // Inside: red carpet to the throne, gold beside it.
        for (int z = z0 + 2; z <= z1 - 1; z++) {
            put(0, 2, z, Blocks.RED_CARPET.defaultBlockState());
        }
        put(0, 2, z0 + 1, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        put(-1, 2, z0 + 1, Blocks.GOLD_BLOCK.defaultBlockState());
        put(1, 2, z0 + 1, Blocks.GOLD_BLOCK.defaultBlockState());
        put(-1, 3, z0 + 1, banner);
        put(1, 3, z0 + 1, banner);
        put(-5, 5, -10, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(5, 5, -10, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // Roof: wide eaves, then two steps up to the ridge, coloured ridge ends.
        box(x0 - 1, 6, z0 - 1, x1 + 1, 6, z1 + 1, tile);
        box(x0 + 1, 7, z0 + 1, x1 - 1, 7, z1 - 1, tile);
        box(x0 + 3, 8, z0 + 3, x1 - 3, 8, z1 - 3, tile);
        put(x0 + 3, 9, -10, trim);
        put(x1 - 3, 9, -10, trim);
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            put(x, 9, -10, tile);
        }
        for (int[] corner : new int[][]{{x0 - 1, z0 - 1}, {x1 + 1, z0 - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}}) {
            put(corner[0], 7, corner[1], trim);
        }
        put(-4, 5, z1 + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(4, 5, z1 + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** A barracks hall along the east or west wall: timber walls, slab roof, hay for bedding. */
    private void barracks(int cx) {
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        int x0 = cx - 3;
        int x1 = cx + 3;
        int z0 = 2;
        int z1 = 12;
        box(x0, 1, z0, x1, 1, z1, Blocks.COBBLESTONE.defaultBlockState());
        ring(x0, 2, z0, x1, 4, z1, wall);
        for (int z : new int[]{z0, z1}) {
            put(x0, 2, z, Blocks.SPRUCE_LOG.defaultBlockState());
            put(x1, 2, z, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        box(x0 - 1, 5, z0 - 1, x1 + 1, 5, z1 + 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        // Door facing the courtyard.
        int door = cx < 0 ? x1 : x0;
        put(door, 2, 7, Blocks.AIR.defaultBlockState());
        put(door, 3, 7, Blocks.AIR.defaultBlockState());
        for (int z = z0 + 2; z <= z1 - 2; z += 2) {
            put(cx < 0 ? x0 + 1 : x1 - 1, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
        put(cx, 4, 7, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        for (int z = z0 + 2; z <= z1 - 2; z += 4) {
            put(cx < 0 ? x0 : x1, 3, z, Blocks.GLASS_PANE.defaultBlockState());
        }
    }

    /** The storehouse (south): stone walls, two chests of supplies (filled once built). */
    private void storehouse() {
        int x0 = -3;
        int x1 = 3;
        int z0 = 12;
        int z1 = 17;
        box(x0, 1, z0, x1, 1, z1, Blocks.STONE_BRICKS.defaultBlockState());
        ring(x0, 2, z0, x1, 4, z1, Blocks.COBBLESTONE.defaultBlockState());
        box(x0 - 1, 5, z0 - 1, x1 + 1, 5, z1 + 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
        put(0, 2, z0, Blocks.AIR.defaultBlockState());
        put(0, 3, z0, Blocks.AIR.defaultBlockState());
        put(0, 4, z0, trim);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        for (int x : new int[]{-2, 2}) {
            put(x, 2, z1 - 1, chest);
            chests.add(origin.offset(x, 2, z1 - 1));
        }
        put(-2, 2, z0 + 1, Blocks.BARREL.defaultBlockState());
        put(2, 2, z0 + 1, Blocks.BARREL.defaultBlockState());
        put(0, 4, z1 - 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** Lamp posts along the roads and a few straw dummies in the yard. */
    private void yard() {
        for (int[] post : new int[][]{{-3, -16}, {-16, -3}, {16, 3}, {-3, 5}, {8, -3}, {-8, 3}}) {
            put(post[0], 1, post[1], Blocks.OAK_FENCE.defaultBlockState());
            put(post[0], 2, post[1], Blocks.OAK_FENCE.defaultBlockState());
            put(post[0], 3, post[1], Blocks.LANTERN.defaultBlockState());
        }
        for (int[] dummy : new int[][]{{-9, 15}, {-12, 15}, {9, 15}, {12, 15}}) {
            put(dummy[0], 1, dummy[1], Blocks.HAY_BLOCK.defaultBlockState());
            put(dummy[0], 2, dummy[1], Blocks.CARVED_PUMPKIN.defaultBlockState());
        }
    }
}
