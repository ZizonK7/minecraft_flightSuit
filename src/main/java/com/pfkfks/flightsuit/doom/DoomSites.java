package com.pfkfks.flightsuit.doom;

import com.pfkfks.flightsuit.planet.dbz.DbzEarth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Battleworld's sights (DESIGN.md 4-17), set out from the hub - the first firm ground near the middle of the world -
 * and built the first time someone comes near (the refuge at once, being where every gate opens):
 * - REFUGE: the Fantastic Four's outpost from Earth-828 - a white-and-blue retro tower, lamp posts, the gate home;
 * - GENOSHA: Magneto's domain - a ring of iron, magnetised pillars hung with chains, the X in the floor;
 * - TALOKAN: Namor's - a sunken stepped temple, a moat of sea water round a prismarine dais;
 * - DEADLANDS: the zombies' - dead ground beyond "the Shield", Battleworld's great wall;
 * - CASTLE: Castle Doom over Doomstadt - curtain wall, four green-roofed towers, Doom's statue in the courtyard,
 *   the keep with the throne at the north end.
 * Each build returns the fight's centre (the castle: its courtyard).
 */
public enum DoomSites {
    REFUGE(0, 0),
    GENOSHA(240, -40),
    TALOKAN(-220, -130),
    DEADLANDS(60, 250),
    CASTLE(0, -360);

    /** Players arriving face north (towards Castle Doom on the skyline). */
    public static final float ARRIVAL_YAW = 180.0F;
    public static final String HUB = "hub";

    private final int dx;
    private final int dz;

    DoomSites(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The hub (found once: firm ground near the middle of Battleworld). */
    public static BlockPos hub(ServerLevel level) {
        DoomData data = DoomData.get(level.getServer());
        BlockPos hub = data.site(HUB);
        if (hub == null) {
            hub = com.pfkfks.flightsuit.planet.SpaceTravel.firmGround(level, 0, 0);
            data.setSite(HUB, hub);
        }
        return hub;
    }

    public BlockPos around(BlockPos hub) {
        return hub.offset(dx, 0, dz);
    }

    /** Where it was built, or null. */
    public @Nullable BlockPos center(DoomData data) {
        return data.site(id());
    }

    /** Where the gate sets you down at the refuge (building it first if it isn't there). */
    public static Vec3 arrival(ServerLevel level) {
        DoomData data = DoomData.get(level.getServer());
        BlockPos refuge = REFUGE.center(data);
        if (refuge == null) {
            BlockPos column = REFUGE.around(hub(level));
            DbzEarth.load(level, column, 2);
            refuge = REFUGE.build(level, column);
            data.setSite(REFUGE.id(), refuge);
        }
        level.getChunkAt(refuge);
        return new Vec3(refuge.getX() + 0.5D, refuge.getY(), refuge.getZ() - 5.5D);
    }

    /** Builds it round {@code column} on the ground there; returns the centre. */
    public BlockPos build(ServerLevel level, BlockPos column) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (y <= level.getSeaLevel()) {
            y = level.getSeaLevel() + 1;
        }
        BlockPos c = new BlockPos(column.getX(), y, column.getZ());
        switch (this) {
            case REFUGE -> refuge(level, c);
            case GENOSHA -> genosha(level, c);
            case TALOKAN -> talokan(level, c);
            case DEADLANDS -> deadlands(level, c);
            case CASTLE -> castle(level, c);
        }
        return c;
    }

    /** Where the castle's throne stands (north end of the keep), from the courtyard centre. */
    public static BlockPos throne(BlockPos courtyard) {
        return courtyard.offset(0, 1, -17);
    }

    // ---------------------------------------------------------------- tools

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void box(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            set(level, pos, state);
        }
    }

    /** A round floor of radius {@code r}: air cleared above, the floor itself, and ground filled in below. */
    private static void ground(ServerLevel level, BlockPos c, int r, int clear, BlockState a, BlockState b, BlockState fill) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r + r) {
                    continue;
                }
                for (int y = 0; y <= clear; y++) {
                    set(level, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                set(level, c.offset(x, -1, z), Math.floorMod(x * 7 + z * 13, 5) == 0 ? b : a);
                for (int y = -6; y <= -2; y++) {
                    BlockPos below = c.offset(x, y, z);
                    if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
                        set(level, below, fill);
                    }
                }
            }
        }
    }

    private static BlockState stair(Block block, Direction facing, boolean top) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private static void ring(ServerLevel level, BlockPos c, int r, BlockState state) {
        for (int i = 0; i < r * 8; i++) {
            double angle = i * Math.PI * 2.0D / (r * 8);
            set(level, c.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)), state);
        }
    }

    private static void lamp(ServerLevel level, BlockPos base, int height) {
        for (int y = 0; y < height; y++) {
            set(level, base.above(y), Blocks.IRON_BARS.defaultBlockState());
        }
        set(level, base.above(height), Blocks.SEA_LANTERN.defaultBlockState());
    }

    // ---------------------------------------------------------------- the refuge (Earth-828)

    /**
     * The Fantastic Four's refuge: a round white plaza with a blue ring, their retro tower (white, blue glass bands,
     * a "4" on its face), lamp posts, and the gate home on the north side - always lit.
     */
    private static void refuge(ServerLevel level, BlockPos c) {
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState blue = Blocks.BLUE_CONCRETE.defaultBlockState();
        ground(level, c, 14, 16, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.STONE.defaultBlockState());
        ring(level, c.below(), 12, blue);
        ring(level, c.below(), 11, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        // The tower, east of the plaza: 7 wide, 15 high, banded with blue glass, a beacon of a roof.
        BlockPos t = c.offset(8, 0, 4);
        for (int y = 0; y < 15; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
                    boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                    if (!wall) {
                        set(level, t.offset(x, y, z), y == 0 || y == 7 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    BlockState state = corner ? blue : y % 4 == 2 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : white;
                    set(level, t.offset(x, y, z), state);
                }
            }
        }
        box(level, t.offset(-3, 15, -3), t.offset(3, 15, 3), white);
        set(level, t.offset(0, 16, 0), Blocks.BEACON.defaultBlockState());
        set(level, t.offset(0, 15, 0), Blocks.IRON_BLOCK.defaultBlockState());
        // A door facing the plaza (west), and a "4" in blue on the north face.
        set(level, t.offset(-3, 1, 0), Blocks.AIR.defaultBlockState());
        set(level, t.offset(-3, 2, 0), Blocks.AIR.defaultBlockState());
        int[][] four = {{-1, 12}, {-1, 11}, {-1, 10}, {0, 10}, {1, 10}, {1, 11}, {1, 12}, {1, 9}, {1, 8}};
        for (int[] p : four) {
            set(level, t.offset(p[0], p[1], -3), blue);
        }
        // Inside: a workbench of Reed's, lights.
        set(level, t.offset(1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, t.offset(2, 1, 1), Blocks.LECTERN.defaultBlockState());
        set(level, t.offset(0, 6, 0), Blocks.SEA_LANTERN.defaultBlockState());
        set(level, t.offset(0, 14, 0), Blocks.SEA_LANTERN.defaultBlockState());
        // Lamp posts round the plaza.
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0D + Math.PI / 6.0D;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 13), 0, (int) Math.round(Math.sin(angle) * 13));
            if (Math.abs(base.getX() - t.getX()) > 4 || Math.abs(base.getZ() - t.getZ()) > 4) {
                lamp(level, base, 3);
            }
        }
        // Benches.
        for (int x = -6; x <= -4; x++) {
            set(level, c.offset(x, 0, 6), stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
        }
        // The gate home: along x on the plaza's north side, 3 wide and 4 tall inside.
        Multiverse.buildGate(level, c.offset(-1, 0, -8), Direction.Axis.X, 3, 4);
        box(level, c.offset(-3, -1, -9), c.offset(3, -1, -7), Blocks.AMETHYST_BLOCK.defaultBlockState());
    }

    // ---------------------------------------------------------------- Genosha (Magneto)

    /** A ring of iron round a floor with the X in it, magnetised pillars hung with chains. */
    private static void genosha(ServerLevel level, BlockPos c) {
        ground(level, c, 18, 14, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState(), Blocks.STONE.defaultBlockState());
        ring(level, c.below(), 17, Blocks.IRON_BLOCK.defaultBlockState());
        ring(level, c.below(), 16, Blocks.BLACK_CONCRETE.defaultBlockState());
        // The X in its circle.
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (x * x + z * z <= 25) {
                    set(level, c.offset(x, -1, z), Blocks.BLACK_CONCRETE.defaultBlockState());
                }
            }
        }
        ring(level, c.below(), 5, Blocks.RED_CONCRETE.defaultBlockState());
        for (int i = -3; i <= 3; i++) {
            set(level, c.offset(i, -1, i), Blocks.YELLOW_CONCRETE.defaultBlockState());
            set(level, c.offset(i, -1, -i), Blocks.YELLOW_CONCRETE.defaultBlockState());
        }
        // Eight pillars, tall and short by turns, lightning rods on top and chains hanging off.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 13), 0, (int) Math.round(Math.sin(angle) * 13));
            int height = i % 2 == 0 ? 9 : 6;
            for (int y = 0; y < height; y++) {
                set(level, base.above(y), y % 3 == 2 ? Blocks.CUT_COPPER.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
            }
            set(level, base.above(height), Blocks.LIGHTNING_ROD.defaultBlockState());
            BlockPos hang = base.offset(base.getX() > c.getX() ? -1 : base.getX() < c.getX() ? 1 : 0, height - 1,
                    base.getZ() > c.getZ() ? -1 : base.getZ() < c.getZ() ? 1 : 0);
            for (int y = 0; y < 3; y++) {
                set(level, hang.below(y), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            }
            set(level, hang.below(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // Twisted girders at the edge, as if wrenched up out of the ground.
        for (int i = 0; i < 5; i++) {
            set(level, c.offset(-10 + i, i / 2, 9 + i / 2), Blocks.IRON_BARS.defaultBlockState());
            set(level, c.offset(9, i, -9 + i), Blocks.IRON_BARS.defaultBlockState());
        }
    }

    // ---------------------------------------------------------------- Talokan (Namor)

    /** A prismarine dais in a moat of sea water, pillars with sea lanterns, a stepped temple behind. */
    private static void talokan(ServerLevel level, BlockPos c) {
        ground(level, c, 18, 14, Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
                Blocks.PRISMARINE.defaultBlockState());
        // The moat: a ring of water two deep between 11 and 13 out.
        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                int d = x * x + z * z;
                if (d >= 121 && d <= 169 + 13) {
                    if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                        // Causeways across, four ways.
                        continue;
                    }
                    set(level, c.offset(x, -3, z), Blocks.PRISMARINE.defaultBlockState());
                    set(level, c.offset(x, -2, z), Blocks.WATER.defaultBlockState());
                    set(level, c.offset(x, -1, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0D;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 9), 0, (int) Math.round(Math.sin(angle) * 9));
            for (int y = 0; y < 6; y++) {
                set(level, base.above(y), Blocks.PRISMARINE_BRICKS.defaultBlockState());
            }
            set(level, base.above(6), Blocks.SEA_LANTERN.defaultBlockState());
        }
        // The stepped temple to the north: five tiers of mossy stone and prismarine, a shrine on top.
        BlockPos p = c.offset(0, 0, -21);
        for (int tier = 0; tier < 5; tier++) {
            int r = 7 - tier;
            box(level, p.offset(-r, tier * 2, -r), p.offset(r, tier * 2 + 1, r),
                    tier % 2 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
        }
        // A stair cut straight up the south face to the shrine.
        for (int s = 0; s < 10; s++) {
            set(level, p.offset(0, s, 8 - s), stair(Blocks.PRISMARINE_BRICK_STAIRS, Direction.NORTH, false));
            set(level, p.offset(0, s + 1, 8 - s), Blocks.AIR.defaultBlockState());
            set(level, p.offset(0, s + 2, 8 - s), Blocks.AIR.defaultBlockState());
        }
        box(level, p.offset(-1, 10, -1), p.offset(1, 12, 1), Blocks.AIR.defaultBlockState());
        set(level, p.offset(0, 10, 0), Blocks.CONDUIT.defaultBlockState());
        set(level, p.offset(-1, 10, -1), Blocks.SEA_LANTERN.defaultBlockState());
        set(level, p.offset(1, 10, -1), Blocks.SEA_LANTERN.defaultBlockState());
    }

    // ---------------------------------------------------------------- the Deadlands and the Shield

    /** Dead ground beyond a stretch of the Shield: a wall fourteen high with a broken gate, soul fire on the battlements. */
    private static void deadlands(ServerLevel level, BlockPos c) {
        ground(level, c, 20, 12, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        for (int i = 0; i < 40; i++) {
            int x = (i * 37) % 33 - 16;
            int z = (i * 53) % 33 - 16;
            if (x * x + z * z < 300) {
                set(level, c.offset(x, 0, z), i % 4 == 0 ? Blocks.COBWEB.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
        // The Shield, south of the field: 61 long, 14 high, 3 thick, a gap broken through the middle.
        BlockPos w = c.offset(0, 0, 21);
        for (int x = -30; x <= 30; x++) {
            int top = 14 + (x % 2 == 0 ? 1 : 0);
            for (int z = -1; z <= 1; z++) {
                int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.getX() + x, w.getZ() + z);
                for (int y = Math.min(floor - w.getY(), 0) - 2; y < (z == -1 ? top : 14); y++) {
                    boolean gap = Math.abs(x) <= 2 && y < 6 + Math.abs(x);
                    set(level, w.offset(x, y, z), gap ? Blocks.AIR.defaultBlockState()
                            : (x * 3 + y * 5 + z) % 7 == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
            }
            if (x % 6 == 0 && Math.abs(x) > 3) {
                set(level, w.offset(x, 15, 0), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        // Rubble from the breach.
        for (int i = 0; i < 12; i++) {
            set(level, w.offset((i * 5) % 9 - 4, 0, -2 - (i * 3) % 5), Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        }
        // A burnt-out watchfire in the middle.
        set(level, c.below(), Blocks.SOUL_SOIL.defaultBlockState());
        set(level, c, Blocks.SOUL_CAMPFIRE.defaultBlockState());
    }

    // ---------------------------------------------------------------- Castle Doom

    /**
     * Castle Doom: a 45-wide curtain wall of dark brick with a gatehouse on the south side, four round towers with
     * green roofs, a courtyard with Doom's statue, and the keep along the north wall - its hall runs to the throne
     * (green and gold) under green glass. The fight's centre is the middle of the courtyard.
     */
    private static void castle(ServerLevel level, BlockPos c) {
        BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState stone = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState roof = Blocks.OXIDIZED_COPPER.defaultBlockState();
        int half = 22;
        // Ground: cleared and floored all the way out to the wall.
        for (int x = -half - 4; x <= half + 4; x++) {
            for (int z = -half - 4; z <= half + 4; z++) {
                for (int y = 0; y <= 24; y++) {
                    set(level, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                set(level, c.offset(x, -1, z), Math.abs(x) <= half && Math.abs(z) <= half
                        ? (Math.floorMod(x + z, 4) == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : stone)
                        : Blocks.COBBLESTONE.defaultBlockState());
                for (int y = -6; y <= -2; y++) {
                    BlockPos below = c.offset(x, y, z);
                    if (level.getBlockState(below).isAir() || !level.getFluidState(below).isEmpty()) {
                        set(level, below, Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        // Curtain wall, 10 high, crenellated, a walkway inside.
        for (int i = -half; i <= half; i++) {
            for (int[] side : new int[][]{{i, -half}, {i, half}, {-half, i}, {half, i}}) {
                for (int y = 0; y < 10; y++) {
                    set(level, c.offset(side[0], y, side[1]), (side[0] * 3 + y * 7 + side[1]) % 11 == 0 ? cracked : brick);
                }
                if (Math.floorMod(i, 2) == 0) {
                    set(level, c.offset(side[0], 10, side[1]), brick);
                }
            }
        }
        // The gatehouse: a 5-wide arch in the south wall, an iron portcullis drawn up.
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 6; y++) {
                set(level, c.offset(x, y, half), Blocks.AIR.defaultBlockState());
            }
            set(level, c.offset(x, 6, half), Blocks.IRON_BARS.defaultBlockState());
        }
        box(level, c.offset(-4, 10, half - 1), c.offset(4, 13, half + 1), brick);
        set(level, c.offset(-3, 14, half + 1), Blocks.GREEN_BANNER.defaultBlockState());
        set(level, c.offset(3, 14, half + 1), Blocks.GREEN_BANNER.defaultBlockState());
        // Four round towers at the corners with green copper caps.
        for (int[] corner : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
            BlockPos t = c.offset(corner[0], 0, corner[1]);
            for (int y = 0; y < 18; y++) {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        int d = x * x + z * z;
                        if (d <= 10) {
                            set(level, t.offset(x, y, z), d >= 5 ? (y % 6 == 3 && (x == 0 || z == 0) ? Blocks.GREEN_STAINED_GLASS.defaultBlockState() : brick)
                                    : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            for (int y = 0; y < 5; y++) {
                int r = 4 - y;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (x * x + z * z <= r * r + 1) {
                            set(level, t.offset(x, 18 + y, z), roof);
                        }
                    }
                }
            }
            set(level, t.offset(0, 23, 0), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        // The keep along the north wall: 25 wide, 9 deep, 14 high, its hall open to the courtyard by a great door.
        int kz0 = -half + 1;
        int kz1 = -half + 10;
        for (int x = -12; x <= 12; x++) {
            for (int z = kz0; z <= kz1; z++) {
                for (int y = 0; y < 14; y++) {
                    boolean wall = Math.abs(x) == 12 || z == kz0 || z == kz1;
                    BlockState state = !wall ? Blocks.AIR.defaultBlockState()
                            : y >= 4 && y <= 9 && Math.floorMod(x, 4) == 2 && z == kz1 ? Blocks.GREEN_STAINED_GLASS.defaultBlockState() : brick;
                    set(level, c.offset(x, y, z), state);
                }
                set(level, c.offset(x, 14, z), (Math.abs(x) + z) % 2 == 0 ? roof : Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState());
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 6; y++) {
                set(level, c.offset(x, y, kz1), Blocks.AIR.defaultBlockState());
            }
        }
        // The hall: a green carpet up to the throne, pillars, green banners, lights.
        for (int z = kz0 + 1; z < kz1 + 1; z++) {
            set(level, c.offset(0, 0, z), Blocks.GREEN_CARPET.defaultBlockState());
            set(level, c.offset(-1, 0, z), Blocks.GREEN_CARPET.defaultBlockState());
            set(level, c.offset(1, 0, z), Blocks.GREEN_CARPET.defaultBlockState());
        }
        for (int x : new int[]{-8, -4, 4, 8}) {
            for (int y = 0; y < 13; y++) {
                set(level, c.offset(x, y, kz0 + 4), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
            set(level, c.offset(x, 6, kz0 + 5), Blocks.GREEN_WALL_BANNER.defaultBlockState());
            set(level, c.offset(x, 12, kz0 + 5), Blocks.SHROOMLIGHT.defaultBlockState());
        }
        // The throne: on a dais at the north end.
        BlockPos th = throne(c);
        box(level, th.offset(-2, -1, -1), th.offset(2, -1, 1), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, th, stair(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH, false));
        set(level, th.offset(0, 1, -1), Blocks.EMERALD_BLOCK.defaultBlockState());
        set(level, th.offset(0, 2, -1), Blocks.EMERALD_BLOCK.defaultBlockState());
        set(level, th.offset(0, 3, -1), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, th.offset(-1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, th.offset(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, th.offset(-2, 0, -1), Blocks.SOUL_LANTERN.defaultBlockState());
        set(level, th.offset(2, 0, -1), Blocks.SOUL_LANTERN.defaultBlockState());
        // Doom's statue in the courtyard, south of the centre: iron armour, a green cloak, arms folded.
        statue(level, c.offset(0, 0, 12));
        // Braziers round the courtyard.
        for (int[] at : new int[][]{{-14, -6}, {14, -6}, {-14, 8}, {14, 8}}) {
            set(level, c.offset(at[0], 0, at[1]), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            set(level, c.offset(at[0], 1, at[1]), Blocks.SOUL_CAMPFIRE.defaultBlockState());
        }
    }

    /** Doom's likeness, three times life size: a plinth, iron legs and body, a green cloak behind, the iron mask. */
    private static void statue(ServerLevel level, BlockPos base) {
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState cloak = Blocks.GREEN_CONCRETE.defaultBlockState();
        box(level, base.offset(-2, 0, -2), base.offset(2, 1, 2), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        BlockPos s = base.above(2);
        for (int y = 0; y < 3; y++) {
            set(level, s.offset(-1, y, 0), iron);
            set(level, s.offset(1, y, 0), iron);
        }
        box(level, s.offset(-1, 3, 0), s.offset(1, 5, 0), iron);
        box(level, s.offset(-2, 3, 1), s.offset(2, 6, 1), cloak);
        set(level, s.offset(-2, 4, 0), cloak);
        set(level, s.offset(2, 4, 0), cloak);
        set(level, s.offset(0, 6, 0), iron);
        set(level, s.offset(0, 7, 0), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        set(level, s.offset(0, 6, 1), cloak);
        set(level, s.offset(0, 7, 1), cloak);
        set(level, s.offset(0, 8, 0), cloak);
    }
}
