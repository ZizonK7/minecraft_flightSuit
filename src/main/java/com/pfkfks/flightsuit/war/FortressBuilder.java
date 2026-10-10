package com.pfkfks.flightsuit.war;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a Three Kingdoms fortress town (DESIGN.md 4-11 성채, M12; made twice as wide after the M13 test) as
 * an ordered list of block placements (and the supply chests, {@link #chests}) around a centre on the ground.
 * The site itself is cleared by {@link StructureJob} ({@link #CLEAR}, {@link #CLEAR_HEIGHT}).
 *
 * Coordinates are relative: x east, z south, y = 0 the ground. Inside an 81x81 curtain wall with four
 * gatehouses, corner and mid-wall towers:
 * <ul>
 *   <li>north: the palace compound - its own low wall and gate, a courtyard, the palace on a podium with a
 *       three-tier roof and the throne on a dais at the back (where the ruler sits);</li>
 *   <li>north-west the granary (the two supply chests), north-east the stables;</li>
 *   <li>the middle: a bell pavilion on the central square where the roads cross, barracks along the east and
 *       west walls, a training yard and an archery range;</li>
 *   <li>south-west the houses and their fields, south-east the market.</li>
 * </ul>
 */
public final class FortressBuilder {
    /** Half the wall's side. */
    public static final int WALL = 40;
    /** Half the cleared, levelled area's side (the wall plus a margin). */
    public static final int CLEAR = 46;
    public static final int CLEAR_HEIGHT = 24;
    /** The plan's version: 1 the first 41x41 fortress (saved as 0), 2 the 81x81 town (after the M13 test). */
    public static final int LAYOUT = 2;
    /** The throne faces south (down the palace towards the gate). */
    public static final float THRONE_YAW = 0.0F;

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

    /** The full plan, in build order (after the site is cleared). */
    public static FortressBuilder plan(Kingdom kingdom, BlockPos origin) {
        FortressBuilder builder = new FortressBuilder(kingdom, origin);
        builder.ground();
        builder.walls();
        builder.towers();
        builder.palace();
        builder.granary();
        builder.stables();
        builder.square();
        for (int side = -1; side <= 1; side += 2) {
            builder.barracks(side * 30, -13, -3);
            builder.barracks(side * 30, 3, 13);
        }
        builder.trainingYard();
        builder.archeryRange();
        for (int cx : new int[]{-33, -23, -13}) {
            builder.house(cx, 20);
            builder.house(cx, 30);
        }
        builder.fields();
        for (int cx : new int[]{12, 20, 28}) {
            builder.stall(cx, 21);
            builder.stall(cx, 31);
        }
        builder.lamps();
        return builder;
    }

    /** The building work for this plan: clear the site, then lay it out. */
    public StructureJob job() {
        return new StructureJob(origin, CLEAR, CLEAR_HEIGHT, out);
    }

    public List<Placement> placements() {
        return out;
    }

    public List<BlockPos> chests() {
        return chests;
    }

    /** Where the ruler stands (on the dais, in front of the throne). */
    public static BlockPos throne(BlockPos origin) {
        return origin.offset(0, 4, -32);
    }

    /** The throne itself (a stair the ruler sits on, facing {@link #THRONE_YAW}). */
    public static BlockPos throneSeat(BlockPos origin) {
        return origin.offset(0, 4, -33);
    }

    /** Where the generals stand (the palace courtyard, in front of the steps). */
    public static BlockPos palaceSteps(BlockPos origin) {
        return origin.offset(0, 1, -18);
    }

    /** How far from the centre the garrison walks (the whole town inside the walls). */
    public static int patrolRadius() {
        return WALL - 4;
    }

    // ---------------------------------------------------------------- helpers

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

    private static BlockState stairs(BlockState stair, Direction facing) {
        return stair.setValue(StairBlock.FACING, facing);
    }

    private static BlockState hanging() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    private void lampPost(int x, int z) {
        put(x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
        put(x, 2, z, Blocks.OAK_FENCE.defaultBlockState());
        put(x, 3, z, Blocks.LANTERN.defaultBlockState());
    }

    private void flag(int x, int y, int z, int pole) {
        for (int i = 0; i < pole; i++) {
            put(x, y + i, z, Blocks.OAK_FENCE.defaultBlockState());
        }
        put(x, y + pole, z, banner);
        put(x, y + pole + 1, z, banner);
    }

    /** A stepped tiled roof over x0..x1, z0..z1 (resting on walls that end at y - 1), its ridge in the kingdom's colour. */
    private void tiledRoof(int x0, int z0, int x1, int z1, int y) {
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        int step = 0;
        while (x0 + step * 2 <= x1 - step * 2 && z0 + step * 2 <= z1 - step * 2) {
            int a0 = x0 - 1 + step * 2;
            int a1 = x1 + 1 - step * 2;
            int b0 = z0 - 1 + step * 2;
            int b1 = z1 + 1 - step * 2;
            box(a0, y + step, b0, a1, y + step, b1, tile);
            step++;
        }
        int top = y + step;
        int mx0 = x0 - 1 + step * 2;
        int mx1 = x1 + 1 - step * 2;
        int mz0 = z0 - 1 + step * 2;
        int mz1 = z1 + 1 - step * 2;
        box(Math.min(mx0, mx1), top, Math.min(mz0, mz1), Math.max(mx0, mx1), top, Math.max(mz0, mz1), tile);
        put(Math.min(mx0, mx1), top + 1, (mz0 + mz1) / 2, trim);
        put(Math.max(mx0, mx1), top + 1, (mz0 + mz1) / 2, trim);
        for (int[] corner : new int[][]{{x0 - 1, z0 - 1}, {x1 + 1, z0 - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}}) {
            put(corner[0], y + 1, corner[1], trim);
        }
    }

    // ---------------------------------------------------------------- pieces

    /** Fill the hollows under the site, lay grass with stone roads from gate to gate and a paved square in the middle. */
    private void ground() {
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
        BlockState kerb = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState paving = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState path = Blocks.DIRT_PATH.defaultBlockState();
        for (int z = -CLEAR; z <= CLEAR; z++) {
            for (int x = -CLEAR; x <= CLEAR; x++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                BlockState state = grass;
                boolean inside = Math.max(ax, az) <= WALL + 3;
                if (inside && (ax <= 2 || az <= 2)) {
                    state = ax == 2 && az > 10 || az == 2 && ax > 10 ? kerb : road;
                } else if (ax <= 10 && az <= 10) {
                    state = (ax + az) % 4 == 0 ? kerb : paving;
                } else if (Math.max(ax, az) < WALL && (z == 25 && x < -2 && x > -38 || z == 26 && x > 2 && x < 34)) {
                    // Lanes through the houses and the market.
                    state = path;
                }
                put(x, 0, z, state);
            }
        }
    }

    private void walls() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        for (int i = -WALL; i <= WALL; i++) {
            for (int y = 1; y <= 6; y++) {
                int noise = (i * 7 + y * 3) % 13;
                BlockState state = noise == 0 ? mossy : noise == 5 ? cracked : brick;
                put(i, y, -WALL, state);
                put(i, y, WALL, state);
                put(-WALL, y, i, state);
                put(WALL, y, i, state);
            }
            if (i % 2 == 0) {
                put(i, 7, -WALL, brick);
                put(i, 7, WALL, brick);
                put(-WALL, 7, i, brick);
                put(WALL, 7, i, brick);
            }
        }
        // Gatehouses: a five-wide opening under the kingdom's colour, a tower either side flying banners.
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int a = -2; a <= 2; a++) {
            for (int y = 1; y <= 4; y++) {
                put(a, y, -WALL, air);
                put(a, y, WALL, air);
                put(-WALL, y, a, air);
                put(WALL, y, a, air);
            }
            put(a, 5, -WALL, trim);
            put(a, 5, WALL, trim);
            put(-WALL, 5, a, trim);
            put(WALL, 5, a, trim);
        }
        for (int[] gate : new int[][]{{0, -WALL}, {0, WALL}, {-WALL, 0}, {WALL, 0}}) {
            boolean alongX = gate[1] != 0;
            for (int side = -1; side <= 1; side += 2) {
                int cx = gate[0] + (alongX ? side * 4 : 0);
                int cz = gate[1] + (alongX ? 0 : side * 4);
                box(cx - 1, 1, cz - 1, cx + 1, 9, cz + 1, brick);
                box(cx - 1, 10, cz - 1, cx + 1, 10, cz + 1, trim);
                flag(cx, 11, cz, 2);
            }
            put(gate[0], 4, gate[1], hanging());
        }
    }

    private void towers() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Corner towers, 7x7 and twelve high.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * WALL;
                int cz = sz * WALL;
                ring(cx - 3, 1, cz - 3, cx + 3, 10, cz + 3, brick);
                box(cx - 2, 1, cz - 2, cx + 2, 10, cz + 2, air);
                box(cx - 3, 11, cz - 3, cx + 3, 11, cz + 3, brick);
                for (int i = -3; i <= 3; i += 2) {
                    put(cx + i, 12, cz - 3, brick);
                    put(cx + i, 12, cz + 3, brick);
                    put(cx - 3, 12, cz + i, brick);
                    put(cx + 3, 12, cz + i, brick);
                }
                for (int y = 2; y <= 8; y += 3) {
                    put(cx, y, cz - 3, Blocks.IRON_BARS.defaultBlockState());
                    put(cx - 3, y, cz, Blocks.IRON_BARS.defaultBlockState());
                    put(cx, y, cz + 3, Blocks.IRON_BARS.defaultBlockState());
                    put(cx + 3, y, cz, Blocks.IRON_BARS.defaultBlockState());
                }
                put(cx, 10, cz, hanging());
                flag(cx, 12, cz, 3);
            }
        }
        // Mid-wall towers half way between each corner and gate.
        for (int s = -1; s <= 1; s += 2) {
            for (int[] at : new int[][]{{s * 20, -WALL}, {s * 20, WALL}, {-WALL, s * 20}, {WALL, s * 20}}) {
                box(at[0] - 2, 1, at[1] - 2, at[0] + 2, 8, at[1] + 2, brick);
                for (int i = -2; i <= 2; i += 2) {
                    put(at[0] + i, 9, at[1] - 2, brick);
                    put(at[0] + i, 9, at[1] + 2, brick);
                    put(at[0] - 2, 9, at[1] + i, brick);
                    put(at[0] + 2, 9, at[1] + i, brick);
                }
                put(at[0], 9, at[1], Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    /**
     * The palace compound (north): a low wall with its own gate, a courtyard with stone lanterns, the palace on a
     * two-high podium - dark timber walls between coloured pillars, a three-wide red carpet up to the throne on a
     * quartz dais, gold either side - under a stepped tiled roof.
     */
    private void palace() {
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wood = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Compound wall (x -18..18, z -38..-16), gate in the middle of the south side.
        ring(-18, 1, -38, 18, 3, -16, brick);
        for (int x = -18; x <= 18; x += 2) {
            put(x, 4, -16, trim);
        }
        box(-2, 1, -16, 2, 3, -16, air);
        put(-3, 4, -16, banner);
        put(3, 4, -16, banner);
        // Courtyard lanterns and a pair of pines.
        for (int x : new int[]{-7, 7}) {
            for (int z : new int[]{-19, -25}) {
                put(x, 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                put(x, 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                put(x, 3, z, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int x : new int[]{-15, 15}) {
            for (int y = 1; y <= 5; y++) {
                put(x, y, -20, Blocks.SPRUCE_LOG.defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(x + dx, 4, -20 + dz, Blocks.SPRUCE_LEAVES.defaultBlockState());
                    put(x + dx, 5, -20 + dz, dx == 0 || dz == 0 ? Blocks.SPRUCE_LEAVES.defaultBlockState() : air);
                }
            }
            put(x, 6, -20, Blocks.SPRUCE_LEAVES.defaultBlockState());
        }
        // Podium (x -13..13, z -36..-22) with steps up the middle.
        box(-13, 1, -36, 13, 2, -22, brick);
        for (int x = -3; x <= 3; x++) {
            put(x, 1, -21, stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.NORTH));
            put(x, 2, -22, stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.NORTH));
        }
        // The hall: x -11..11, z -34..-24, walls y 3..8.
        int x0 = -11;
        int x1 = 11;
        int z0 = -34;
        int z1 = -24;
        ring(x0, 3, z0, x1, 8, z1, wood);
        for (int x : new int[]{x0, -7, -3, 3, 7, x1}) {
            box(x, 3, z1, x, 8, z1, trim);
            box(x, 3, z0, x, 8, z0, trim);
        }
        for (int z : new int[]{-31, -27}) {
            box(x0, 3, z, x0, 8, z, trim);
            box(x1, 3, z, x1, 8, z, trim);
        }
        box(-2, 3, z1, 2, 6, z1, air);
        for (int x : new int[]{-9, -5, 5, 9}) {
            box(x, 4, z1, x, 6, z1, Blocks.GLASS_PANE.defaultBlockState());
            box(x, 4, z0, x, 6, z0, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int z : new int[]{-29}) {
            box(x0, 4, z, x0, 6, z, Blocks.GLASS_PANE.defaultBlockState());
            box(x1, 4, z, x1, 6, z, Blocks.GLASS_PANE.defaultBlockState());
        }
        // Inside: carpet to the dais, the throne, gold and banners beside it, lanterns, pillars along the aisle.
        for (int z = -31; z <= z1 - 1; z++) {
            for (int x = -1; x <= 1; x++) {
                put(x, 3, z, Blocks.RED_CARPET.defaultBlockState());
            }
        }
        box(-4, 3, -33, 4, 3, -32, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        put(0, 4, -33, stairs(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.NORTH));
        put(-1, 4, -33, Blocks.GOLD_BLOCK.defaultBlockState());
        put(1, 4, -33, Blocks.GOLD_BLOCK.defaultBlockState());
        put(-1, 5, -33, banner);
        put(1, 5, -33, banner);
        box(-3, 4, -33, -3, 6, -33, trim);
        box(3, 4, -33, 3, 6, -33, trim);
        for (int x : new int[]{-5, 5}) {
            for (int z : new int[]{-30, -27}) {
                box(x, 3, z, x, 8, z, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            }
        }
        for (int x : new int[]{-7, 7}) {
            put(x, 8, -28, hanging());
            put(x, 8, -32, hanging());
        }
        put(0, 8, -28, hanging());
        tiledRoof(x0, z0, x1, z1, 9);
        put(-4, 8, z1 - 1, hanging());
        put(4, 8, z1 - 1, hanging());
    }

    /** The granary (north-west): stone walls, a tiled roof, the two chests of supplies (filled once built), sacks of grain. */
    private void granary() {
        int x0 = -36;
        int x1 = -24;
        int z0 = -35;
        int z1 = -25;
        box(x0, 0, z0, x1, 0, z1, Blocks.STONE_BRICKS.defaultBlockState());
        ring(x0, 1, z0, x1, 5, z1, Blocks.COBBLESTONE.defaultBlockState());
        for (int[] corner : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            box(corner[0], 1, corner[1], corner[0], 5, corner[1], Blocks.SPRUCE_LOG.defaultBlockState());
        }
        tiledRoof(x0, z0, x1, z1, 6);
        // Door facing the palace road (east).
        put(x1, 1, -30, Blocks.AIR.defaultBlockState());
        put(x1, 2, -30, Blocks.AIR.defaultBlockState());
        put(x1, 3, -30, trim);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        for (int z : new int[]{-33, -27}) {
            put(x0 + 1, 1, z, chest);
            chests.add(origin.offset(x0 + 1, 1, z));
        }
        for (int z = z0 + 1; z <= z1 - 1; z += 2) {
            if (z != -33 && z != -27) {
                put(x0 + 1, 1, z, Blocks.BARREL.defaultBlockState());
            }
            put(x1 - 1, 1, z == -30 ? z0 + 1 : z, Blocks.HAY_BLOCK.defaultBlockState());
        }
        put(-30, 1, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
        put(-30, 2, z0 + 1, Blocks.HAY_BLOCK.defaultBlockState());
        put(-30, 5, -30, hanging());
    }

    /** The stables (north-east): a fenced paddock, a long roofed stall with hay and water. */
    private void stables() {
        int x0 = 22;
        int x1 = 36;
        int z0 = -36;
        int z1 = -22;
        for (int x = x0; x <= x1; x++) {
            put(x, 1, z0, Blocks.SPRUCE_FENCE.defaultBlockState());
            put(x, 1, z1, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int z = z0; z <= z1; z++) {
            put(x0, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            put(x1, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        put(x0, 1, -28, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST));
        // The stall along the north side.
        for (int x = x0 + 1; x <= x1 - 1; x += 3) {
            box(x, 1, z0 + 1, x, 3, z0 + 1, Blocks.SPRUCE_LOG.defaultBlockState());
            box(x, 1, z0 + 5, x, 3, z0 + 5, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        box(x0 + 1, 4, z0 + 1, x1 - 1, 4, z0 + 5,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            put(x, 1, z0 + 2, Blocks.HAY_BLOCK.defaultBlockState());
            put(x + 1, 1, z0 + 2, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        }
        put(29, 3, z0 + 3, hanging());
        flag(x1 - 1, 1, z1 - 1, 3);
    }

    /** The central square: a bell pavilion over the crossroads, banners at its corners. */
    private void square() {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(sx * 4, 1, sz * 4, sx * 4, 5, sz * 4, trim);
                flag(sx * 9, 1, sz * 9, 3);
            }
        }
        BlockState slab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        box(-5, 6, -5, 5, 6, 5, Blocks.DEEPSLATE_TILES.defaultBlockState());
        box(-3, 7, -3, 3, 7, 3, Blocks.DEEPSLATE_TILES.defaultBlockState());
        box(-1, 8, -1, 1, 8, 1, slab);
        put(0, 8, 0, trim);
        put(0, 5, 0, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        for (int sx = -1; sx <= 1; sx += 2) {
            put(sx * 3, 5, 0, hanging());
            put(0, 5, sx * 3, hanging());
        }
    }

    /** A barracks hall (along the east or west wall): timber walls, a tiled roof, hay bedding, the door towards the road. */
    private void barracks(int cx, int z0, int z1) {
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        int x0 = cx - 4;
        int x1 = cx + 4;
        box(x0, 0, z0, x1, 0, z1, Blocks.COBBLESTONE.defaultBlockState());
        ring(x0, 1, z0, x1, 4, z1, wall);
        for (int z : new int[]{z0, z1}) {
            box(x0, 1, z, x0, 4, z, Blocks.SPRUCE_LOG.defaultBlockState());
            box(x1, 1, z, x1, 4, z, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        tiledRoof(x0, z0, x1, z1, 5);
        int door = cx < 0 ? x1 : x0;
        int mid = (z0 + z1) / 2;
        for (int z = mid - 1; z <= mid + 1; z++) {
            put(door, 1, z, Blocks.AIR.defaultBlockState());
            put(door, 2, z, Blocks.AIR.defaultBlockState());
        }
        put(door, 3, mid, trim);
        int bed = cx < 0 ? x0 + 1 : x1 - 1;
        for (int z = z0 + 1; z <= z1 - 1; z += 2) {
            put(bed, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
        put(cx, 1, z0 + 1, Blocks.BARREL.defaultBlockState());
        put(cx, 1, z1 - 1, Blocks.SMITHING_TABLE.defaultBlockState());
        put(cx, 4, mid, hanging());
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            put(bed - (cx < 0 ? 1 : -1), 3, z, Blocks.GLASS_PANE.defaultBlockState());
        }
    }

    /** West of the square: straw dummies in rows, weapon racks (fences with an armour stand-like log). */
    private void trainingYard() {
        for (int x = -22; x <= -14; x += 4) {
            for (int z = -9; z <= 9; z += 6) {
                if (Math.abs(z) <= 2) {
                    continue;
                }
                put(x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
                put(x, 2, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
            }
        }
        for (int z = -12; z <= 12; z++) {
            if (Math.abs(z) > 2) {
                put(-24, 1, z, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
    }

    /** East of the square: targets on a bank of hay at the end of the range. */
    private void archeryRange() {
        for (int z = -12; z <= 12; z++) {
            if (Math.abs(z) <= 2) {
                continue;
            }
            put(23, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            put(23, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
            if (z % 4 == 0) {
                put(22, 2, z, Blocks.TARGET.defaultBlockState());
            }
        }
        for (int z : new int[]{-10, -6, 6, 10}) {
            put(13, 1, z, Blocks.OAK_FENCE.defaultBlockState());
        }
    }

    /** A family house (south-west): timber and plaster on a stone base, tiled roof, door to the lane, a lit window. */
    private void house(int cx, int cz) {
        int x0 = cx - 3;
        int x1 = cx + 3;
        int z0 = cz - 2;
        int z1 = cz + 3;
        boolean lane = cz < 25;
        box(x0, 0, z0, x1, 0, z1, Blocks.COBBLESTONE.defaultBlockState());
        ring(x0, 1, z0, x1, 1, z1, Blocks.STONE_BRICKS.defaultBlockState());
        ring(x0, 2, z0, x1, 3, z1, Blocks.WHITE_TERRACOTTA.defaultBlockState());
        for (int[] corner : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            box(corner[0], 1, corner[1], corner[0], 3, corner[1], Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        tiledRoof(x0, z0, x1, z1, 4);
        int doorZ = lane ? z1 : z0;
        put(cx, 1, doorZ, Blocks.AIR.defaultBlockState());
        put(cx, 2, doorZ, Blocks.AIR.defaultBlockState());
        put(cx - 2, 2, doorZ, Blocks.GLASS_PANE.defaultBlockState());
        put(cx + 2, 2, doorZ, Blocks.GLASS_PANE.defaultBlockState());
        put(x0, 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        put(x1, 2, cz, Blocks.GLASS_PANE.defaultBlockState());
        put(cx - 2, 1, lane ? z0 + 1 : z1 - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(cx + 2, 1, lane ? z0 + 1 : z1 - 1, Blocks.BARREL.defaultBlockState());
        put(cx, 3, cz, hanging());
    }

    /** Wheat fields behind the houses, along the south wall, a water channel every few rows. */
    private void fields() {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState();
        BlockState wheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        for (int x = -36; x <= -6; x++) {
            for (int z = 35; z <= 37; z++) {
                if (x % 6 == 0) {
                    put(x, 0, z, Blocks.WATER.defaultBlockState());
                } else {
                    put(x, 0, z, farmland);
                    put(x, 1, z, wheat);
                }
            }
        }
    }

    /** A market stall (south-east): fence posts, a striped wool awning, goods on the counter. */
    private void stall(int cx, int cz) {
        BlockState[] awnings = {Blocks.RED_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState(), trim};
        BlockState awning = awnings[Math.floorMod(cx / 8 + cz, awnings.length)];
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -1; dz <= 1; dz += 2) {
                box(cx + dx, 1, cz + dz, cx + dx, 2, cz + dz, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                put(cx + dx, 3, cz + dz, (dx & 1) == 0 ? awning : Blocks.WHITE_WOOL.defaultBlockState());
            }
            put(cx + dx, 1, cz - 1, dx == 0 ? Blocks.COMPOSTER.defaultBlockState() : Blocks.BARREL.defaultBlockState());
        }
        put(cx - 1, 2, cz - 1, Blocks.MELON.defaultBlockState());
        put(cx + 1, 2, cz - 1, Blocks.PUMPKIN.defaultBlockState());
    }

    /** Lamp posts along the roads (clear of the square, the gates, the barracks and the palace compound). */
    private void lamps() {
        for (int s = -1; s <= 1; s += 2) {
            for (int i : new int[]{14, 24}) {
                lampPost(s * i, -4);
                lampPost(s * i, 4);
            }
            for (int z : new int[]{14, 24, 34}) {
                lampPost(s * 4, z);
            }
            lampPost(s * 4, -12);
        }
    }
}
