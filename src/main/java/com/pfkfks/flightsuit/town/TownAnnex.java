package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.war.FortressBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.List;

/**
 * M18 small additions to the towns that go in without rebuilding them (DESIGN.md 5차 피드백 "살아 있는 마을"): only
 * air, grass and the like are replaced, so whatever a player built there stays. Applied once per version
 * (FortRecord.annex, HeroData.annex) when someone is near.
 * <ul>
 *   <li>fortress: signal fires on the four corner towers (hay under a campfire, whose smoke rises high), and the
 *       tavern (주막) in the market - an awning on posts, a barrel counter, benches, a cauldron over a fire;</li>
 *   <li>Hero City: the diner by the park - a glass kiosk with a counter and stools.</li>
 * </ul>
 */
public final class TownAnnex {
    /** Bumped when the additions change: towns at a lower number get them again. */
    public static final int VERSION = 1;

    private TownAnnex() {
    }

    /** The tavern's keeper stands here (fortress coordinates, x east, z south, y the ground). */
    public static final int[] FORT_TAVERN = {20, 0, 26};
    /** Hero City's diner keeper. */
    public static final int[] CITY_DINER = {-19, 0, 21};

    public static void fortress(ServerLevel level, BlockPos origin) {
        List<FortressBuilder.Placement> out = new ArrayList<>();
        // Signal fires on the corner towers (FortressBuilder.towers: 7x7 tops at y 11, crenellations at 12).
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * FortressBuilder.WALL;
                int cz = sz * FortressBuilder.WALL;
                add(out, origin, cx, 12, cz, Blocks.HAY_BLOCK.defaultBlockState(), false);
                add(out, origin, cx, 13, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), false);
            }
        }
        // The tavern: four posts, a slab awning, a barrel counter along the north side, benches, the stew pot.
        int tx = FORT_TAVERN[0];
        int tz = FORT_TAVERN[2];
        BlockState post = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState awning = Blocks.SPRUCE_SLAB.defaultBlockState();
        BlockState awningTrim = Blocks.RED_WOOL.defaultBlockState();
        for (int[] corner : new int[][]{{tx - 3, tz - 2}, {tx + 3, tz - 2}, {tx - 3, tz + 2}, {tx + 3, tz + 2}}) {
            for (int y = 1; y <= 3; y++) {
                add(out, origin, corner[0], y, corner[1], post, true);
            }
        }
        for (int x = tx - 3; x <= tx + 3; x++) {
            for (int z = tz - 2; z <= tz + 2; z++) {
                boolean edge = x == tx - 3 || x == tx + 3 || z == tz - 2 || z == tz + 2;
                add(out, origin, x, 4, z, edge && (x + z) % 2 == 0 ? awningTrim : awning, true);
            }
        }
        for (int x = tx - 2; x <= tx + 2; x++) {
            add(out, origin, x, 1, tz - 2, Blocks.BARREL.defaultBlockState(), true);
        }
        add(out, origin, tx, 1, tz - 1, Blocks.CAMPFIRE.defaultBlockState(), true);
        add(out, origin, tx, 2, tz - 1, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), true);
        add(out, origin, tx - 2, 1, tz + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH), true);
        add(out, origin, tx + 2, 1, tz + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH), true);
        add(out, origin, tx - 1, 1, tz + 1, Blocks.SPRUCE_SLAB.defaultBlockState(), true);
        add(out, origin, tx + 1, 1, tz + 1, Blocks.SPRUCE_SLAB.defaultBlockState(), true);
        add(out, origin, tx, 3, tz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), true);
        apply(level, out);
    }

    public static void heroCity(ServerLevel level, BlockPos origin) {
        List<FortressBuilder.Placement> out = new ArrayList<>();
        int dx = CITY_DINER[0];
        int dz = CITY_DINER[2];
        BlockState pillar = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
        BlockState roof = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
        BlockState trim = Blocks.RED_CONCRETE.defaultBlockState();
        // A 7x5 kiosk: the keeper's side (north) walled in glass, the open south side with a counter and stools.
        for (int[] corner : new int[][]{{dx - 3, dz - 2}, {dx + 3, dz - 2}, {dx - 3, dz + 2}, {dx + 3, dz + 2}}) {
            for (int y = 1; y <= 3; y++) {
                add(out, origin, corner[0], y, corner[1], pillar, true);
            }
        }
        for (int x = dx - 2; x <= dx + 2; x++) {
            for (int y = 1; y <= 3; y++) {
                add(out, origin, x, y, dz - 2, y == 1 ? pillar : glass, true);
            }
        }
        for (int z = dz - 1; z <= dz + 1; z++) {
            for (int y = 1; y <= 3; y++) {
                add(out, origin, dx - 3, y, z, y == 1 ? pillar : glass, true);
                add(out, origin, dx + 3, y, z, y == 1 ? pillar : glass, true);
            }
        }
        for (int x = dx - 3; x <= dx + 3; x++) {
            for (int z = dz - 2; z <= dz + 2; z++) {
                add(out, origin, x, 4, z, (x == dx - 3 || x == dx + 3 || z == dz - 2 || z == dz + 2) ? trim : roof, true);
            }
        }
        for (int x = dx - 2; x <= dx + 2; x++) {
            add(out, origin, x, 1, dz + 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), true);
            if (x != dx) {
                add(out, origin, x, 1, dz + 2, stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH), true);
            }
        }
        add(out, origin, dx, 1, dz - 1, Blocks.SMOKER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.SOUTH), true);
        add(out, origin, dx - 2, 1, dz - 1, Blocks.BARREL.defaultBlockState(), true);
        add(out, origin, dx + 2, 1, dz - 1, Blocks.BARREL.defaultBlockState(), true);
        add(out, origin, dx, 3, dz, Blocks.SEA_LANTERN.defaultBlockState(), true);
        apply(level, out);
    }

    private static BlockState stairs(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static void add(List<FortressBuilder.Placement> out, BlockPos origin, int x, int y, int z, BlockState state, boolean fillOnly) {
        out.add(new FortressBuilder.Placement(origin.offset(x, y, z), state, fillOnly));
    }

    /** Lays the placements (fillOnly ones only where there's nothing to keep). */
    private static void apply(ServerLevel level, List<FortressBuilder.Placement> placements) {
        for (FortressBuilder.Placement placement : placements) {
            if (!level.isLoaded(placement.pos())) {
                continue;
            }
            if (placement.fillOnly()) {
                BlockState current = level.getBlockState(placement.pos());
                if (!current.isAir() && !current.canBeReplaced() && current.getFluidState().isEmpty()) {
                    continue;
                }
            }
            level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL);
        }
    }
}
