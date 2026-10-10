package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * West City round Capsule Corp (M17 bundle D): three avenues out from the dome - north (the shops), east and south
 * (the houses) - with street lamps, eight round dome houses (white with a yellow band), three dome shops and a
 * little park. The west side stays open: that's where the ships land. Built once, the first time someone comes
 * near after Capsule Corp; its people (town.TownPlan.westCity, TownLife) are put back every visit.
 *
 * All positions are offsets from Capsule Corp's centre at standing height (DbzEarth's CAPSULE_CORP centre).
 */
public final class WestCity {
    /** Saved in the planet's world tag once it's built (the layout's version). */
    public static final String KEY = "west_city";
    public static final int LAYOUT = 1;
    /** How far it reaches from Capsule Corp (for loading checks and the townsfolk's area). */
    public static final int REACH = 62;

    /** The houses (x, z) and the shops: their doors face the avenue beside them. */
    public static final int[][] HOUSES = {{24, -10}, {24, 10}, {40, -10}, {40, 10}, {-10, 30}, {10, 30}, {-10, 44}, {10, 44}};
    public static final int[][] SHOPS = {{-10, -26}, {10, -26}, {-10, -40}};
    public static final int[] PARK = {26, 28};
    /** Where people stand about: the plaza before Capsule Corp's door, the avenue corners. */
    public static final int[][] SQUARE = {{-4, -12}, {4, -12}, {-3, -18}, {3, -18}, {16, 3}, {3, 16}, {-3, -48}};

    private static final int HOUSE_RADIUS = 4;
    private static final int PAD = 6;

    private WestCity() {
    }

    /** Whether everything it covers is loaded (it's only built in one go). */
    public static boolean loaded(ServerLevel level, BlockPos cc) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                if (!level.isLoaded(cc.offset(sx * REACH, 0, sz * REACH))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void build(ServerLevel level, BlockPos cc) {
        BlockState road = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState line = Blocks.WHITE_CONCRETE.defaultBlockState();
        // The avenues: north from the door, east and south from the dome's sides (a white line down the middle).
        for (int z = -9; z >= -56; z--) {
            for (int x = -1; x <= 1; x++) {
                pave(level, cc.offset(x, 0, z), x == 0 && z % 4 != 0 ? line : road);
            }
        }
        for (int d = 9; d <= 50; d++) {
            for (int w = -1; w <= 1; w++) {
                pave(level, cc.offset(d, 0, w), w == 0 && d % 4 != 0 ? line : road);
                pave(level, cc.offset(w, 0, d), w == 0 && d % 4 != 0 ? line : road);
            }
        }
        for (int[] at : HOUSES) {
            BlockPos center = cc.offset(at[0], 0, at[1]);
            pad(level, center, PAD, Blocks.GRASS_BLOCK.defaultBlockState());
            Direction door = doorFacing(at);
            dome(level, center, HOUSE_RADIUS, Blocks.YELLOW_CONCRETE.defaultBlockState(), door);
            set(level, center.relative(door.getOpposite(), 2), Blocks.BARREL.defaultBlockState());
            set(level, center.relative(door.getClockWise(), 2), Blocks.CRAFTING_TABLE.defaultBlockState());
            set(level, center.relative(door.getCounterClockWise(), 2), Blocks.LANTERN.defaultBlockState());
        }
        BlockState[] signs = {Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(),
                Blocks.RED_CONCRETE.defaultBlockState()};
        for (int i = 0; i < SHOPS.length; i++) {
            int[] at = SHOPS[i];
            BlockPos center = cc.offset(at[0], 0, at[1]);
            pad(level, center, PAD, Blocks.SMOOTH_STONE.defaultBlockState());
            Direction door = doorFacing(at);
            dome(level, center, HOUSE_RADIUS, signs[i], door);
            // The counter just inside the door, the shopkeeper behind it.
            BlockPos counter = center.relative(door, 1);
            set(level, counter, Blocks.BARREL.defaultBlockState());
            set(level, counter.relative(door.getClockWise()), Blocks.BARREL.defaultBlockState());
            set(level, center.offset(0, HOUSE_RADIUS - 1, 0), Blocks.SEA_LANTERN.defaultBlockState());
        }
        park(level, cc.offset(PARK[0], 0, PARK[1]));
        // Street lamps along the avenues.
        for (int d = 15; d <= 55; d += 10) {
            int side = (d / 10) % 2 == 0 ? 3 : -3;
            lamp(level, cc.offset(side, 0, -d));
            if (d <= 48) {
                lamp(level, cc.offset(d, 0, side));
                lamp(level, cc.offset(side, 0, d));
            }
        }
    }

    /** A building's door faces the avenue it stands by: the east one (z = 0) or the north and south ones (x = 0). */
    public static Direction doorFacing(int[] at) {
        if (Math.abs(at[1]) <= 12) {
            return at[1] < 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return at[0] < 0 ? Direction.EAST : Direction.WEST;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** A road block at standing height - 1 with headroom cleared above and the ground filled in below. */
    private static void pave(ServerLevel level, BlockPos stand, BlockState top) {
        for (int y = 0; y <= 4; y++) {
            BlockState there = level.getBlockState(stand.above(y));
            if (!there.isAir() && !there.is(Blocks.LANTERN)) {
                set(level, stand.above(y), Blocks.AIR.defaultBlockState());
            }
        }
        set(level, stand.below(), top);
        fill(level, stand.below(2));
    }

    /** A round pad of {@code floor} with the air above it cleared (for a house). */
    private static void pad(ServerLevel level, BlockPos center, int radius, BlockState floor) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius + 1) {
                    continue;
                }
                BlockPos stand = center.offset(dx, 0, dz);
                for (int y = 0; y <= 8; y++) {
                    set(level, stand.above(y), Blocks.AIR.defaultBlockState());
                }
                set(level, stand.below(), floor);
                fill(level, stand.below(2));
            }
        }
    }

    /** Dirt down to solid ground, so nothing stands on a disc in the air. */
    private static void fill(ServerLevel level, BlockPos from) {
        for (int y = 0; y < 16; y++) {
            BlockPos below = from.below(y);
            BlockState there = level.getBlockState(below);
            if (!there.isAir() && there.getFluidState().isEmpty() && !there.canBeReplaced()) {
                return;
            }
            set(level, below, Blocks.DIRT.defaultBlockState());
        }
    }

    /** A Capsule-style dome: white, a band of {@code band}, round windows, a two-high doorway on {@code door}'s side. */
    private static void dome(ServerLevel level, BlockPos center, int r, BlockState band, Direction door) {
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int y = 0; y <= r; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    double d = Math.sqrt(dx * dx + y * y + dz * dz);
                    if (d <= r + 0.4D && d > r - 0.8D) {
                        boolean window = y == 2 && (dx == 0 || dz == 0);
                        set(level, center.offset(dx, y, dz), window ? glass : y == 1 ? band : white);
                    }
                }
            }
        }
        BlockPos doorway = center.relative(door, r);
        set(level, doorway, Blocks.AIR.defaultBlockState());
        set(level, doorway.above(), Blocks.AIR.defaultBlockState());
        set(level, doorway.relative(door.getOpposite()), Blocks.AIR.defaultBlockState());
        set(level, doorway.relative(door.getOpposite()).above(), Blocks.AIR.defaultBlockState());
    }

    /** A little park: grass, a round oak, flowers, two benches. */
    private static void park(ServerLevel level, BlockPos center) {
        pad(level, center, PAD, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int y = 0; y < 4; y++) {
            set(level, center.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (dx * dx + dy * dy + dz * dz <= 5 && !(dx == 0 && dz == 0 && dy < 1)) {
                        set(level, center.offset(dx, 4 + dy, dz), Blocks.OAK_LEAVES.defaultBlockState());
                    }
                }
            }
        }
        BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()};
        int[][] at = {{3, 1}, {-2, 4}, {-4, -1}, {1, -4}};
        for (int i = 0; i < at.length; i++) {
            set(level, center.offset(at[i][0], 0, at[i][1]), flowers[i]);
        }
        set(level, center.offset(-3, 0, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        set(level, center.offset(-2, 0, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        set(level, center.offset(3, 0, -3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        set(level, center.offset(2, 0, -3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
    }

    /** A street lamp: a stone post with a lantern on top. */
    private static void lamp(ServerLevel level, BlockPos stand) {
        for (int y = 0; y < 3; y++) {
            set(level, stand.above(y), Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        set(level, stand.above(3), Blocks.LANTERN.defaultBlockState());
        set(level, stand.below(), Blocks.STONE_BRICKS.defaultBlockState());
    }
}
