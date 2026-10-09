package com.pfkfks.flightsuit.planet.dbz;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The sights of Dragon Ball Earth's first chapter (DESIGN.md 4-16: 볼거리 몇 곳), set around the landing site:
 * Capsule Corp's dome (Bulma), Kame House on its patch of sand with palm trees (Goku), and the crater where
 * Raditz's pod came down (where the Saiyans land). Each is built the first time someone comes near.
 */
public enum DbzLandmarks {
    CAPSULE_CORP(36, -6),
    KAME_HOUSE(-150, 110),
    CRATER(170, -140);

    private final int dx;
    private final int dz;

    DbzLandmarks(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Ground level is fixed when it's built; before that this is just (x, 0, z). */
    public BlockPos around(BlockPos site) {
        return site.offset(dx, 0, dz);
    }

    /** Builds it on the ground at {@code center}'s column; returns the centre at ground level. */
    public BlockPos build(ServerLevel level, BlockPos site) {
        BlockPos column = around(site);
        BlockPos ground = DragonBalls.firmGround(level, column.getX(), column.getZ());
        int y = ground != null ? ground.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos center = new BlockPos(column.getX(), y, column.getZ());
        switch (this) {
            case CAPSULE_CORP -> capsuleCorp(level, center);
            case KAME_HOUSE -> kameHouse(level, center);
            case CRATER -> crater(level, center);
        }
        return center;
    }

    /** Where the person who lives there stands. */
    public BlockPos post(BlockPos center) {
        return switch (this) {
            case CAPSULE_CORP -> center.offset(0, 0, -4);
            case KAME_HOUSE -> center.offset(0, 0, -6);
            case CRATER -> center.offset(0, -4, 0);
        };
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void flatten(ServerLevel level, BlockPos center, int radius, BlockState top) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                for (int y = 0; y <= 12; y++) {
                    set(level, center.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                set(level, center.offset(x, -1, z), top);
                // Fill down to solid ground, so nothing stands on a thin disc in the air.
                for (int y = -2; y >= -16; y--) {
                    BlockPos below = center.offset(x, y, z);
                    BlockState there = level.getBlockState(below);
                    if (!there.isAir() && there.getFluidState().isEmpty() && !there.canBeReplaced()) {
                        break;
                    }
                    set(level, below, Blocks.DIRT.defaultBlockState());
                }
            }
        }
    }

    /** A white dome with a yellow band, round windows, a wide door to the north (-Z) and a little lab inside. */
    private static void capsuleCorp(ServerLevel level, BlockPos c) {
        flatten(level, c, 12, Blocks.SMOOTH_STONE.defaultBlockState());
        int r = 8;
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState band = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= r; y++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= r && d > r - 1.2D) {
                        boolean window = y == 4 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
                        set(level, c.offset(x, y, z), window ? glass : (y == 2 ? band : white));
                    }
                }
            }
        }
        // The door, facing north (towards the landing site side), and lights inside.
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                set(level, c.offset(x, y, -r + 1), Blocks.AIR.defaultBlockState());
                set(level, c.offset(x, y, -r), Blocks.AIR.defaultBlockState());
            }
        }
        set(level, c.offset(0, r - 1, 0), Blocks.SEA_LANTERN.defaultBlockState());
        set(level, c.offset(-4, 0, 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, c.offset(-3, 0, 3), Blocks.SMITHING_TABLE.defaultBlockState());
        set(level, c.offset(3, 0, 3), Blocks.BREWING_STAND.defaultBlockState());
        set(level, c.offset(4, 0, 2), Blocks.BLUE_WOOL.defaultBlockState());
        set(level, c.offset(2, 0, 4), Blocks.LANTERN.defaultBlockState());
        // The CC mark on the roof.
        set(level, c.offset(0, r, 0), Blocks.BLUE_CONCRETE.defaultBlockState());
    }

    /** Kame House: a pink house with a red roof on a round patch of sand, two palms. */
    private static void kameHouse(ServerLevel level, BlockPos c) {
        flatten(level, c, 11, Blocks.SAND.defaultBlockState());
        BlockState wall = Blocks.PINK_TERRACOTTA.defaultBlockState();
        BlockState roof = Blocks.RED_CONCRETE.defaultBlockState();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                set(level, c.offset(x, -1, z), Blocks.OAK_PLANKS.defaultBlockState());
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                for (int y = 0; y <= 3; y++) {
                    set(level, c.offset(x, y, z), edge ? wall : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int step = 0; step <= 3; step++) {
            int rr = 4 - step;
            for (int x = -rr; x <= rr; x++) {
                for (int z = -rr; z <= rr; z++) {
                    set(level, c.offset(x, 4 + step, z), roof);
                }
            }
        }
        // Windows and the door (north side, -Z).
        set(level, c.offset(-3, 2, 0), Blocks.GLASS_PANE.defaultBlockState());
        set(level, c.offset(3, 2, 0), Blocks.GLASS_PANE.defaultBlockState());
        set(level, c.offset(0, 2, 3), Blocks.GLASS_PANE.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(level, c.offset(0, 0, -3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, c.offset(0, 1, -3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, c.offset(-2, 0, 2), Blocks.RED_WOOL.defaultBlockState());
        set(level, c.offset(2, 0, 2), Blocks.BOOKSHELF.defaultBlockState());
        set(level, c.offset(0, 3, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        for (int[] palm : new int[][]{{-7, -5}, {7, 4}}) {
            BlockPos base = c.offset(palm[0], 0, palm[1]);
            for (int y = 0; y < 6; y++) {
                set(level, base.above(y).offset(y / 3, 0, 0), Blocks.JUNGLE_LOG.defaultBlockState());
            }
            BlockPos crown = base.offset(1, 6, 0);
            BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            set(level, crown, leaves);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int k = 1; k <= 3; k++) {
                    set(level, crown.relative(dir, k).below(k == 3 ? 1 : 0), leaves);
                }
            }
        }
    }

    /** A scorched bowl with the round Saiyan pod at the bottom. */
    private static void crater(ServerLevel level, BlockPos c) {
        int r = 9;
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 1) {
                    continue;
                }
                if (d > r) {
                    set(level, c.offset(x, -1, z), Blocks.COARSE_DIRT.defaultBlockState());
                    continue;
                }
                int depth = (int) Math.round(4.0D * (1.0D - (d / r) * (d / r)));
                for (int y = 8; y >= -depth; y--) {
                    set(level, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                set(level, c.offset(x, -depth - 1, z), (x + z) % 3 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
            }
        }
        BlockPos pod = c.offset(0, -4, 0);
        BlockState shell = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    boolean corner = Math.abs(x) + Math.abs(z) + Math.abs(y - 1) == 3;
                    if (!corner) {
                        set(level, pod.offset(x + 3, y, z), shell);
                    }
                }
            }
        }
        set(level, pod.offset(3, 1, -1), Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
        set(level, pod.offset(3, 2, 0), Blocks.REDSTONE_LAMP.defaultBlockState());
    }
}
