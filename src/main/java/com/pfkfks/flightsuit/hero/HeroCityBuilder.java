package com.pfkfks.flightsuit.hero;

import com.pfkfks.flightsuit.war.FortressBuilder;
import com.pfkfks.flightsuit.war.StructureJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out Hero City (DESIGN.md 4-13, M13; made twice as wide after the M13 test): a very advanced city on a
 * 121x121 site. Two seven-wide avenues cross at the HQ plaza, where the Hero HQ tower stands (glass bands, the
 * Avengers "A" near the top, the vault in its lobby, a landing pad on the roof); streets on a grid further out.
 * Between them: glass skyscrapers, Central Park with a pond and a fountain, a glass-domed science centre, and
 * rows of lower buildings out by the low quartz wall. Same placement format as the fortresses
 * (FortressBuilder.Placement); the site is cleared by {@link StructureJob}.
 *
 * Coordinates are relative: x east, z south, y = 0 the ground.
 */
public final class HeroCityBuilder {
    /**
     * The plan's version: 1 the first 61x61 city (saved as 0), 2 the 121x121 city (after the M13 test), 3 with
     * furnished homes and shops for its townsfolk (after the M16 test).
     */
    public static final int LAYOUT = 3;
    /** Half the site's side. */
    public static final int EDGE = 60;
    public static final int CLEAR_HEIGHT = 56;
    /** Half the HQ tower's side. */
    private static final int HQ = 7;
    private static final int HQ_TOP = 50;
    /** The grid streets (and the avenues at 0). */
    private static final int STREET = 36;

    private final BlockPos origin;
    private final List<FortressBuilder.Placement> out = new ArrayList<>();

    private HeroCityBuilder(BlockPos origin) {
        this.origin = origin;
    }

    public static HeroCityBuilder plan(BlockPos origin) {
        HeroCityBuilder builder = new HeroCityBuilder(origin);
        builder.ground();
        builder.headquarters();
        // Inner blocks (between the avenues and the grid streets).
        builder.skyscraper(24, -24, 11, 44, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        builder.skyscraper(-26, -26, 9, 34, Blocks.GRAY_STAINED_GLASS.defaultBlockState());
        builder.skyscraper(-14, -27, 7, 22, Blocks.CYAN_STAINED_GLASS.defaultBlockState());
        builder.skyscraper(25, 25, 11, 30, Blocks.GRAY_STAINED_GLASS.defaultBlockState());
        builder.dome(12, 24, 6);
        builder.park(-19, 19);
        // Out by the wall: rows of lower buildings in each outer block.
        builder.outerRows();
        builder.lights();
        builder.wall();
        return builder;
    }

    /** The building work: clear the site, then lay it out. */
    public StructureJob job() {
        return new StructureJob(origin, EDGE, CLEAR_HEIGHT, out);
    }

    public List<FortressBuilder.Placement> placements() {
        return out;
    }

    /** Where Captain and Iron Man stand: the HQ lobby. */
    public static BlockPos lobby(BlockPos origin) {
        return origin.offset(0, 1, 0);
    }

    /** The two vault chests at the back of the HQ lobby. */
    public static List<BlockPos> vault(BlockPos origin) {
        return List.of(origin.offset(-2, 1, -HQ + 1), origin.offset(2, 1, -HQ + 1));
    }

    /** Is (x, z) (relative) on an avenue or a grid street - open road, out of the plaza? */
    public static boolean isStreet(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        boolean avenue = ax <= 3 || az <= 3;
        boolean grid = Math.abs(ax - STREET) <= 2 || Math.abs(az - STREET) <= 2;
        return Math.max(ax, az) <= EDGE - 2 && Math.max(ax, az) > 10 && (avenue || grid);
    }

    /** How far out the open roads reach (for picking spots along them). */
    public static int streetReach() {
        return EDGE - 4;
    }

    // ---------------------------------------------------------------- helpers

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

    private void tree(int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            put(x, y, z, Blocks.OAK_LOG.defaultBlockState());
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (!corner && (dx != 0 || dz != 0)) {
                    put(x + dx, height, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    put(x + dx, height + 1, z + dz, Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
        put(x, height + 2, z, Blocks.OAK_LEAVES.defaultBlockState());
    }

    // ---------------------------------------------------------------- pieces

    /** Stone under the site, the plaza, the avenues and grid streets (centre lines, pavements), lawns between. */
    private void ground() {
        BlockState base = Blocks.STONE.defaultBlockState();
        for (int y = -5; y <= -1; y++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                for (int x = -EDGE; x <= EDGE; x++) {
                    fill(x, y, z, base);
                }
            }
        }
        BlockState plaza = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState plazaInlay = Blocks.POLISHED_DIORITE.defaultBlockState();
        BlockState road = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState line = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState dash = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState pavement = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState lawn = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int z = -EDGE; z <= EDGE; z++) {
            for (int x = -EDGE; x <= EDGE; x++) {
                int ax = Math.abs(x);
                int az = Math.abs(z);
                BlockState state = lawn;
                if (ax <= 10 && az <= 10) {
                    state = (ax == 10 || az == 10 || (ax + az) % 6 == 0) ? plazaInlay : plaza;
                } else if (ax <= 3 || az <= 3) {
                    // Avenue: yellow centre line (dashed), pavement on its edges.
                    boolean edge = ax == 3 && az > 3 || az == 3 && ax > 3;
                    if (edge) {
                        state = pavement;
                    } else if (ax == 0 && az > 10 && az % 4 != 0 || az == 0 && ax > 10 && ax % 4 != 0) {
                        state = line;
                    } else {
                        state = road;
                    }
                } else if (Math.abs(ax - STREET) <= 2 || Math.abs(az - STREET) <= 2) {
                    boolean onX = Math.abs(ax - STREET) <= 2;
                    int across = onX ? ax - STREET : az - STREET;
                    int along = onX ? az : ax;
                    state = Math.abs(across) == 2 ? pavement : across == 0 && along % 6 < 3 ? dash : road;
                }
                put(x, 0, z, state);
            }
        }
        // Fountains at the four corners of the plaza.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * 9;
                int cz = sz * 9;
                put(cx, 1, cz, Blocks.QUARTZ_PILLAR.defaultBlockState());
                put(cx, 2, cz, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }

    /**
     * The HQ tower: 15x15 and fifty high, glass bands every other storey, the Avengers "A" in gold near the top of
     * the south face, the lobby (wide glass entrance, the vault at the back), a landing pad and antenna on the roof.
     */
    private void headquarters() {
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        int r = HQ;
        int top = HQ_TOP;
        for (int y = 1; y <= top; y++) {
            int storey = (y - 1) / 5;
            boolean band = storey % 2 == 1 && (y - 1) % 5 != 0;
            for (int i = -r; i <= r; i++) {
                boolean corner = Math.abs(i) >= r - 1;
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
        // The "A" on the south face, eight rows tall, in gold with a white surround.
        String[] logo = {
                "...#...",
                "..#.#..",
                "..#.#..",
                ".#...#.",
                ".#####.",
                "#.....#",
                "#.....#",
                "#.....#"};
        int logoTop = top - 3;
        for (int row = 0; row < logo.length; row++) {
            for (int col = 0; col < 7; col++) {
                put(col - 3, logoTop - row, r, logo[row].charAt(col) == '#' ? Blocks.GOLD_BLOCK.defaultBlockState() : white);
            }
        }
        // Lobby: a wide glass entrance facing south, sea lanterns overhead, the vault at the back.
        box(-3, 1, r, 3, 4, r, Blocks.AIR.defaultBlockState());
        box(-3, 5, r, 3, 5, r, glass);
        box(-r + 1, 1, -r + 1, r - 1, 4, r - 1, Blocks.AIR.defaultBlockState());
        for (int x : new int[]{-4, 0, 4}) {
            for (int z : new int[]{-4, 0, 4}) {
                put(x, 5, z, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        for (int x : new int[]{-2, 2}) {
            put(x, 1, -r + 1, chest);
        }
        put(0, 1, -r + 1, Blocks.IRON_BLOCK.defaultBlockState());
        put(0, 2, -r + 1, Blocks.BEACON.defaultBlockState());
        // A reception desk and benches.
        for (int x = -2; x <= 2; x++) {
            put(x, 1, 2, Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        for (int z = -3; z <= 3; z += 3) {
            put(-r + 1, 1, z, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            put(r - 1, 1, z, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
        // Roof: landing pad with an "H", lit edges, an antenna.
        box(-r - 1, top + 1, -r - 1, r + 1, top + 1, r + 1, Blocks.IRON_BLOCK.defaultBlockState());
        for (int i = -r - 1; i <= r + 1; i += 2) {
            put(i, top + 2, -r - 1, Blocks.SEA_LANTERN.defaultBlockState());
            put(i, top + 2, r + 1, Blocks.SEA_LANTERN.defaultBlockState());
            put(-r - 1, top + 2, i, Blocks.SEA_LANTERN.defaultBlockState());
            put(r + 1, top + 2, i, Blocks.SEA_LANTERN.defaultBlockState());
        }
        BlockState mark = Blocks.YELLOW_CONCRETE.defaultBlockState();
        for (int z = -3; z <= 3; z++) {
            put(-3, top + 1, z, mark);
            put(3, top + 1, z, mark);
        }
        for (int x = -2; x <= 2; x++) {
            put(x, top + 1, 0, mark);
        }
        for (int y = top + 2; y <= top + 9; y++) {
            put(r, y, r, Blocks.IRON_BARS.defaultBlockState());
        }
        put(r, top + 10, r, Blocks.REDSTONE_LAMP.defaultBlockState());
    }

    /** A glass tower with white corners and floor bands, doors on all sides, a crown of lanterns on the roof. */
    private void skyscraper(int cx, int cz, int size, int height, BlockState glass) {
        int r = size / 2;
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
            if (slab) {
                box(cx - r + 1, y, cz - r + 1, cx + r - 1, y, cz + r - 1, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        }
        box(cx - r, height + 1, cz - r, cx + r, height + 1, cz + r, white);
        for (int i = -r; i <= r; i += 2) {
            put(cx + i, height + 2, cz - r, Blocks.SEA_LANTERN.defaultBlockState());
            put(cx + i, height + 2, cz + r, Blocks.SEA_LANTERN.defaultBlockState());
            put(cx - r, height + 2, cz + i, Blocks.SEA_LANTERN.defaultBlockState());
            put(cx + r, height + 2, cz + i, Blocks.SEA_LANTERN.defaultBlockState());
        }
        put(cx, height + 2, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
        // Ground-floor doors on all sides.
        for (int d = -1; d <= 1; d++) {
            for (int y = 1; y <= 2; y++) {
                put(cx + d, y, cz - r, Blocks.AIR.defaultBlockState());
                put(cx + d, y, cz + r, Blocks.AIR.defaultBlockState());
                put(cx - r, y, cz + d, Blocks.AIR.defaultBlockState());
                put(cx + r, y, cz + d, Blocks.AIR.defaultBlockState());
            }
        }
        put(cx, 3, cz, Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** A glass dome (the science centre) on a quartz ring, lit from inside. */
    private void dome(int cx, int cz, int radius) {
        box(cx - radius - 1, 0, cz - radius - 1, cx + radius + 1, 0, cz + radius + 1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int y = 0; y <= radius; y++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double d = Math.sqrt(dx * dx + y * y + dz * dz);
                    if (d <= radius + 0.5D && d > radius - 0.6D) {
                        boolean rib = dx == 0 || dz == 0;
                        put(cx + dx, y + 1, cz + dz, rib ? Blocks.WHITE_CONCRETE.defaultBlockState()
                                : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 1; y <= 2; y++) {
            put(cx, y, cz - radius, Blocks.AIR.defaultBlockState());
            put(cx - 1, y, cz - radius, Blocks.AIR.defaultBlockState());
            put(cx + 1, y, cz - radius, Blocks.AIR.defaultBlockState());
        }
        put(cx, 1, cz, Blocks.BEACON.defaultBlockState());
        put(cx, radius - 1, cz, Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** Central Park: lawns, trees, a pond with a quartz fountain, paths and benches. */
    private void park(int cx, int cz) {
        int r = 13;
        box(cx - r, 0, cz - r, cx + r, 0, cz + r, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int i = -r; i <= r; i++) {
            put(cx + i, 0, cz, Blocks.GRAVEL.defaultBlockState());
            put(cx, 0, cz + i, Blocks.GRAVEL.defaultBlockState());
        }
        // Pond with the fountain in the middle.
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 4.3D) {
                    put(cx + dx, 0, cz + dz, d > 3.4D ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
                    if (d > 3.4D) {
                        put(cx + dx, 1, cz + dz, Blocks.QUARTZ_SLAB.defaultBlockState());
                    }
                }
            }
        }
        put(cx, 0, cz, Blocks.QUARTZ_BLOCK.defaultBlockState());
        put(cx, 1, cz, Blocks.QUARTZ_PILLAR.defaultBlockState());
        put(cx, 2, cz, Blocks.QUARTZ_PILLAR.defaultBlockState());
        put(cx, 3, cz, Blocks.SEA_LANTERN.defaultBlockState());
        for (int[] at : new int[][]{{-9, -9}, {-9, 9}, {9, -9}, {9, 9}, {-11, 0}, {11, 0}, {0, -11}, {0, 11}, {-6, -11}, {6, 11}}) {
            if (at[0] == 0 || at[1] == 0) {
                // Benches along the paths instead of trees on them.
                put(cx + at[0] + (at[0] == 0 ? 1 : 0), 1, cz + at[1] + (at[1] == 0 ? 1 : 0),
                        Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, at[0] == 0 ? Direction.EAST : Direction.SOUTH));
            } else {
                tree(cx + at[0], cz + at[1], 5);
            }
        }
    }

    /**
     * The outer blocks, between the grid streets and the wall: a row of lower buildings (eight to sixteen high,
     * the heights and glass varied by place) along each side, set back from the street.
     */
    private void outerRows() {
        BlockState[] glasses = {Blocks.GRAY_STAINED_GLASS.defaultBlockState(), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
                Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Blocks.CYAN_STAINED_GLASS.defaultBlockState()};
        int[] spots = {-48, -20, 20, 48};
        for (int along : spots) {
            for (int side = -1; side <= 1; side += 2) {
                int depth = side * 48;
                int seed = Math.floorMod(along * 31 + depth * 17, 97);
                int height = 8 + seed % 9;
                BlockState glass = glasses[seed % glasses.length];
                building(along, depth, height, glass);
                furnish(along, depth);
                if (Math.abs(along) != Math.abs(depth)) {
                    // The same corner block from the other side - built once.
                    building(depth, along, 8 + (seed * 7) % 9, glasses[(seed + 1) % glasses.length]);
                    furnish(depth, along);
                }
            }
        }
    }

    /** A 9x9 office or apartment block: concrete frame, glass floors, a flat roof with a water tank. */
    private void building(int cx, int cz, int height, BlockState glass) {
        int r = 4;
        BlockState frame = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        for (int y = 1; y <= height; y++) {
            boolean floorLine = y % 4 == 0;
            for (int i = -r; i <= r; i++) {
                boolean corner = Math.abs(i) == r;
                BlockState wall = corner || floorLine ? frame : glass;
                put(cx + i, y, cz - r, wall);
                put(cx + i, y, cz + r, wall);
                put(cx - r, y, cz + i, wall);
                put(cx + r, y, cz + i, wall);
            }
        }
        box(cx - r, height + 1, cz - r, cx + r, height + 1, cz + r, frame);
        box(cx - 1, height + 2, cz - 1, cx, height + 3, cz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int y = 1; y <= 2; y++) {
            put(cx, y, cz - r, Blocks.AIR.defaultBlockState());
            put(cx, y, cz + r, Blocks.AIR.defaultBlockState());
            put(cx - r, y, cz, Blocks.AIR.defaultBlockState());
            put(cx + r, y, cz, Blocks.AIR.defaultBlockState());
        }
        put(cx, 3, cz, Blocks.SEA_LANTERN.defaultBlockState());
    }

    /**
     * Inside an outer block (the townsfolk live and shop here - town/TownPlan): the north and south ones at
     * x = +-20 are shops - a counter facing the middle of the city, shelves behind; the rest are homes - two beds,
     * a table and chairs, a bookshelf. The middle is left clear (where the shopkeeper or the family stands).
     */
    private void furnish(int cx, int cz) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (Math.abs(cx) == 20 && Math.abs(cz) == 48) {
            int toward = cz > 0 ? -1 : 1;
            for (int dx = -2; dx <= 2; dx++) {
                put(cx + dx, 1, cz + toward * 2, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
            put(cx - 2, 2, cz + toward * 2, Blocks.LANTERN.defaultBlockState());
            put(cx + 2, 2, cz + toward * 2, Blocks.LANTERN.defaultBlockState());
            for (int dx = -3; dx <= 3; dx++) {
                if (dx == 0) {
                    continue;
                }
                put(cx + dx, 1, cz - toward * 3, Blocks.BARREL.defaultBlockState());
                put(cx + dx, 2, cz - toward * 3, Blocks.BOOKSHELF.defaultBlockState());
            }
            return;
        }
        for (int dz : new int[]{-2, 2}) {
            put(cx - 3, 1, cz + dz, Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            put(cx - 2, 1, cz + dz, Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        }
        put(cx + 2, 1, cz - 2, Blocks.OAK_FENCE.defaultBlockState());
        put(cx + 2, 2, cz - 2, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        put(cx + 3, 1, cz - 2, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        put(cx + 2, 1, cz - 3, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        put(cx + 3, 1, cz + 2, Blocks.BOOKSHELF.defaultBlockState());
        put(cx + 3, 2, cz + 2, Blocks.BOOKSHELF.defaultBlockState());
        put(cx + 3, 1, cz + 3, Blocks.BARREL.defaultBlockState());
        put(cx, 1, cz, air);
    }

    /** Street lights along the avenues and the grid streets. */
    private void lights() {
        for (int i = 14; i <= EDGE - 6; i += 8) {
            for (int s = -1; s <= 1; s += 2) {
                int[][] spots = {{s * i, -5}, {s * i, 5}, {-5, s * i}, {5, s * i},
                        {s * i, STREET - 4}, {s * i, -STREET + 4}, {STREET - 4, s * i}, {-STREET + 4, s * i}};
                for (int[] at : spots) {
                    if (Math.abs(Math.abs(at[0]) - STREET) <= 3 || Math.abs(Math.abs(at[1]) - STREET) <= 3) {
                        continue;
                    }
                    put(at[0], 1, at[1], Blocks.IRON_BARS.defaultBlockState());
                    put(at[0], 2, at[1], Blocks.IRON_BARS.defaultBlockState());
                    put(at[0], 3, at[1], Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
    }

    /** A low quartz wall round the city, open where the avenues and the streets leave it. */
    private void wall() {
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
        for (int i = -EDGE; i <= EDGE; i++) {
            int a = Math.abs(i);
            if (a <= 4 || Math.abs(a - STREET) <= 3) {
                continue;
            }
            put(i, 1, -EDGE, quartz);
            put(i, 1, EDGE, quartz);
            put(-EDGE, 1, i, quartz);
            put(EDGE, 1, i, quartz);
            if (i % 6 == 0) {
                put(i, 2, -EDGE, Blocks.SEA_LANTERN.defaultBlockState());
                put(i, 2, EDGE, Blocks.SEA_LANTERN.defaultBlockState());
                put(-EDGE, 2, i, Blocks.SEA_LANTERN.defaultBlockState());
                put(EDGE, 2, i, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }
}
