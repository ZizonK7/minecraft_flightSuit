package com.pfkfks.flightsuit.thanos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Titan's sights (DESIGN.md 4-16 타이탄: 블랙 오더, 스톤 단서와 수집): the ruins of Thanos's home city near the
 * landing site, four Black Order arenas out in the dust - each guarding a stone - and the Vormir spire where
 * Red Skull keeps the Soul Stone. Built the first time someone comes near.
 */
public enum TitanSites {
    RUINS(40, 40, null),
    MAW_ARENA(260, 0, ThanosForce.EBONY_MAW),
    PROXIMA_ARENA(0, 260, ThanosForce.PROXIMA_MIDNIGHT),
    CORVUS_ARENA(-260, 0, ThanosForce.CORVUS_GLAIVE),
    CULL_ARENA(0, -260, ThanosForce.CULL_OBSIDIAN),
    VORMIR(-210, 210, ThanosForce.RED_SKULL);

    public static final int SPIRE = 28;

    private final int dx;
    private final int dz;
    private final @Nullable ThanosForce keeper;

    TitanSites(int dx, int dz, @Nullable ThanosForce keeper) {
        this.dx = dx;
        this.dz = dz;
        this.keeper = keeper;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public @Nullable ThanosForce keeper() {
        return keeper;
    }

    public BlockPos around(BlockPos site) {
        return site.offset(dx, 0, dz);
    }

    /** Where the keeper stands. */
    public BlockPos post(BlockPos center) {
        return this == VORMIR ? center.above(SPIRE + 1) : center;
    }

    public BlockPos build(ServerLevel level, BlockPos site) {
        BlockPos column = around(site);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos c = new BlockPos(column.getX(), y, column.getZ());
        switch (this) {
            case RUINS -> ruins(level, c);
            case VORMIR -> vormir(level, c);
            default -> arena(level, c);
        }
        return c;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void floor(ServerLevel level, BlockPos c, int r, BlockState a, BlockState b) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = 0; y <= 10; y++) {
                    set(level, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                set(level, c.offset(x, -1, z), (x * 7 + z * 13) % 5 == 0 ? b : a);
                for (int y = -4; y <= -2; y++) {
                    if (level.getBlockState(c.offset(x, y, z)).isAir()) {
                        set(level, c.offset(x, y, z), Blocks.BLACKSTONE.defaultBlockState());
                    }
                }
            }
        }
    }

    /** Broken pillars in a ring and a fallen arch: what's left of Thanos's home. */
    private static void ruins(ServerLevel level, BlockPos c) {
        floor(level, c, 12, Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 9), 0, (int) Math.round(Math.sin(angle) * 9));
            int height = 3 + (i * 5) % 7;
            for (int y = 0; y < height; y++) {
                set(level, base.above(y), y == height - 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = -4; x <= 4; x++) {
            set(level, c.offset(x, 0, 2), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        set(level, c.offset(-4, 1, 2), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(level, c.offset(-4, 2, 2), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(level, c, Blocks.CRYING_OBSIDIAN.defaultBlockState());
    }

    /** A dark ring with four light pillars and a gold pedestal where the stone is kept. */
    private static void arena(ServerLevel level, BlockPos c) {
        floor(level, c, 13, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0D + Math.PI / 4.0D;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 10), 0, (int) Math.round(Math.sin(angle) * 10));
            for (int y = 0; y < 5; y++) {
                set(level, base.above(y), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
            set(level, base.above(5), Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
            set(level, base.above(6), Blocks.SHROOMLIGHT.defaultBlockState());
        }
        set(level, c.offset(0, 0, 5), Blocks.GOLD_BLOCK.defaultBlockState());
        set(level, c.offset(0, 1, 5), Blocks.END_ROD.defaultBlockState());
    }

    /** A tall black spire with a ladder up its north face and a stone circle on top. */
    private static void vormir(ServerLevel level, BlockPos c) {
        floor(level, c, 8, Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
        for (int y = 0; y <= SPIRE; y++) {
            int r = y < SPIRE - 4 ? 4 - y / 9 : 4;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        set(level, c.offset(x, y, z), (x + y + z) % 4 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState());
                    }
                }
            }
        }
        // A straight spine up the north face (-Z) with the ladder on it, all the way to the top.
        for (int y = 0; y <= SPIRE; y++) {
            for (int z = -4; z <= 0; z++) {
                set(level, c.offset(0, y, z), Blocks.BASALT.defaultBlockState());
            }
            set(level, c.offset(0, y, -5), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            set(level, c.offset((int) Math.round(Math.cos(angle) * 3), SPIRE + 1, (int) Math.round(Math.sin(angle) * 3)),
                    Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        set(level, c.offset(3, SPIRE + 2, 0), Blocks.SOUL_LANTERN.defaultBlockState());
        set(level, c.offset(-3, SPIRE + 2, 0), Blocks.SOUL_LANTERN.defaultBlockState());
    }
}
