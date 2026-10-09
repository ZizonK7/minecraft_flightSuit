package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Buildings the architects can put up (DESIGN.md 4-12), drawn as text layers from the ground up. Layer 0
 * replaces the ground; '.' must be cleared to air; ' ' is left alone. The front (the way in) is the z = 0
 * row, facing north before rotation.
 *
 * Each building belongs to a village stage (1 개척 캠프: tents and wood, 2 마을: stone and timber, later 3
 * 기술 도시) and may have a bigger version in a later stage it can be rebuilt into (개축) on the same spot.
 * The hall buildings stand around the hall block itself (left alone in the middle, its board inside); the
 * next stage's hall building is what expanding the hall means.
 */
public enum Blueprint {
    /** Stage 1 lodging: a wool tent over two beds - walls two high so nothing sits right on a bed. */
    TENT("tent", 1, false, new String[][]{
            {"KKKKK", "KKKKK", "KKKKK", "KKKKK", "KKKKK"},
            {"WW.WW", "W...W", "Wb.bW", "WB.BW", "WWWWW"},
            {"WW.WW", "W...W", "W...W", "W...W", "WWWWW"},
            {" WWW ", " W.W ", " WhW ", " W.W ", " WWW "},
            {"  R  ", "  R  ", "  R  ", "  R  ", "  R  "}
    }),
    /** Stage 1 lookout: a platform on log legs with a ladder up the middle. Lets guards see the whole village. */
    WATCHTOWER("watchtower", 1, false, new String[][]{
            {"     ", " CCC ", " CCC ", " CCC ", "     "},
            {".....", ".LaL.", "..L..", ".L.L.", "....."},
            {".....", ".LaL.", "..L..", ".L.L.", "....."},
            {".....", ".LaL.", "..L..", ".L.L.", "....."},
            {".....", ".LaL.", "..L..", ".L.L.", "....."},
            {"PPPPP", "PPaPP", "PPPPP", "PPPPP", "PPPPP"},
            {"FFFFF", "F...F", "F...F", "F...F", "FFFFF"},
            {"t...t", "     ", "     ", "     ", "t...t"}
    }),
    /** 9x9 farmland around one water block (it keeps every tile wet). The farmers plant it. */
    FARM("farm", 1, false, new String[][]{
            {"mmmmmmmmm", "mmmmmmmmm", "mmmmmmmmm", "mmmmmmmmm", "mmmm~mmmm", "mmmmmmmmm", "mmmmmmmmm", "mmmmmmmmm", "mmmmmmmmm"},
            {".........", ".........", ".........", ".........", ".........", ".........", ".........", ".........", "........."}
    }),
    /** Stage 1 hall: an open-fronted command tent over the hall block, a lantern under the peak. */
    HALL_CAMP("hall_camp", 1, true, new String[][]{
            {"SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS"},
            {"X.....X", "W.....W", "W.....W", "W.. ..W", "W.....W", "Wl...lW", "XWWWWWX"},
            {"X.....X", "W.....W", "W.....W", "W.....W", "W.....W", "W.....W", "XWWWWWX"},
            {"X.....X", "W.....W", "W.....W", "W.....W", "W.....W", "W.....W", "XWWWWWX"},
            {"QQQQQQQ", "Q.....Q", "Q.....Q", "Q.....Q", "Q.....Q", "Q.....Q", "QQQQQQQ"},
            {"       ", " WWWWW ", " W...W ", " W...W ", " W...W ", " WWWWW ", "       "},
            {"       ", "       ", "  QQQ  ", "  QhQ  ", "  QQQ  ", "       ", "       "},
            {"       ", "       ", "       ", "   W   ", "       ", "       ", "       "}
    }),
    /** Stage 1 → 2: a stone hall with windows, a double-high room for the board and a crenellated roof. */
    HALL_VILLAGE("hall_village", 2, true, new String[][]{
            {"TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT"},
            {"TCCCDCCCT", "C.......C", "C.......C", "C.......C", "C... ...C", "C.......C", "C.......C", "Cl.....lC", "TCCCCCCCT"},
            {"TGCCdCCGT", "C.......C", "G.......G", "G.......G", "C.......C", "G.......G", "G.......G", "C.......C", "TCGGCGGCT"},
            {"TGCCCCCGT", "C.......C", "G.......G", "G.......G", "C.......C", "G.......G", "G.......G", "C.......C", "TCGGCGGCT"},
            {"TCCCCCCCT", "C.......C", "C.h...h.C", "C.......C", "C.......C", "C.......C", "C.h...h.C", "C.......C", "TCCCCCCCT"},
            {"TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT", "TTTTTTTTT"},
            {"T.T.T.T.T", "         ", "T       T", "         ", "T       T", "         ", "T       T", "         ", "T.T.T.T.T"}
    }),
    /** Stage 2 lodging: timber-framed house on a stone footing, four beds, windows, a gable roof. */
    HOUSE("house", 2, false, new String[][]{
            {"CCCCCCC", "CSSSSSC", "CSSSSSC", "CSSSSSC", "CSSSSSC", "CSSSSSC", "CCCCCCC"},
            {"XPPDPPX", "Pl...lP", "P.....P", "P.....P", "Pbb.bbP", "PBBcBBP", "XPPPPPX"},
            {"XGPdPGX", "P.....P", "G.....G", "G.....G", "P.....P", "P.....P", "XPGGGPX"},
            {"XGPPPGX", "P.....P", "G.....G", "G.....G", "P.....P", "P.....P", "XPGGGPX"},
            {"XPPPPPX", "P.....P", "P.....P", "P.....P", "P.....P", "P.....P", "XPPPPPX"},
            {"ePPPPPw", "e.....w", "e.....w", "e.....w", "e.....w", "e.....w", "ePPPPPw"},
            {" ePPPw ", " e...w ", " e...w ", " e...w ", " e...w ", " e...w ", " ePPPw "},
            {"  ePw  ", "  e.w  ", "  e.w  ", "  e.w  ", "  e.w  ", "  e.w  ", "  ePw  "},
            {"   P   ", "   P   ", "   P   ", "   P   ", "   P   ", "   P   ", "   P   "}
    }),
    /**
     * Stage 1 school (M11): an open pavilion - benches facing a blackboard on the back wall, the teacher's
     * lectern in front of it, a slab roof with a lantern. The children go here by day when a teacher is in.
     */
    SCHOOL("school", 1, false, new String[][]{
            {"SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS", "SSSSSSS"},
            {"X.....X", ".......", ".v.v.v.", ".......", ".v.v.v.", "...n...", "XPkkkPX"},
            {"X.....X", ".......", ".......", ".......", ".......", ".......", "XPkkkPX"},
            {"X.....X", ".......", ".......", "...h...", ".......", ".......", "XPPPPPX"},
            {"ooooooo", "ooooooo", "ooooooo", "ooooooo", "ooooooo", "ooooooo", "ooooooo"}
    });

    static {
        TENT.upgrade = HOUSE;
        HALL_CAMP.upgrade = HALL_VILLAGE;
    }

    private final String id;
    private final int stage;
    private final boolean hall;
    private final String[][] layers;
    private @Nullable Blueprint upgrade;

    Blueprint(String id, int stage, boolean hall, String[][] layers) {
        this.id = id;
        this.stage = stage;
        this.hall = hall;
        this.layers = layers;
    }

    public String translationKey() {
        return "building.flightsuit." + id;
    }

    /** The village stage that unlocks it. */
    public int stage() {
        return stage;
    }

    /** What it can be rebuilt into once the village is far enough along (null = nothing). */
    public @Nullable Blueprint upgrade() {
        return upgrade;
    }

    /** A hall building: goes up around the hall block, ordered from a suggestion (no blueprint item). */
    public boolean isHall() {
        return hall;
    }

    /** The hall building that stands for a village stage. */
    public static Blueprint hallFor(int stage) {
        return stage >= 2 ? HALL_VILLAGE : HALL_CAMP;
    }

    public boolean isLodging() {
        return this == TENT || this == HOUSE;
    }

    public int width() {
        return layers[0][0].length();
    }

    public int depth() {
        return layers[0].length;
    }

    public int height() {
        return layers.length;
    }

    /** Half the footprint's longest side: how far the building reaches from its center, any rotation. */
    public int reach() {
        return Math.max(width(), depth()) / 2;
    }

    public record Entry(BlockPos pos, BlockState state, int layer) {
    }

    /**
     * The blocks of this building around {@code center} (layer 0 at {@code center}'s y), turned by
     * {@code rotation}; in build order - layer by layer, and inside a layer: clearing, water, solid blocks,
     * then what hangs on them (doors, beds, panes, ladders, lights).
     */
    public List<Entry> entries(BlockPos center, Rotation rotation) {
        List<Entry> out = new ArrayList<>();
        int cx = (width() - 1) / 2;
        int cz = (depth() - 1) / 2;
        for (int y = 0; y < height(); y++) {
            for (int pass = 0; pass < 4; pass++) {
                for (int z = 0; z < depth(); z++) {
                    for (int x = 0; x < width(); x++) {
                        char c = layers[y][z].charAt(x);
                        if (c == ' ' || pass(c) != pass) {
                            continue;
                        }
                        BlockPos offset = rotate(x - cx, z - cz, rotation);
                        out.add(new Entry(center.offset(offset.getX(), y, offset.getZ()), state(c).rotate(rotation), y));
                    }
                }
            }
        }
        return out;
    }

    private static int pass(char c) {
        return switch (c) {
            case '.' -> 0;
            case '~' -> 1;
            case 'D', 'd', 'b', 'B', 'G', 't', 'a', 'h', 'l' -> 3;
            default -> 2;
        };
    }

    private static BlockPos rotate(int dx, int dz, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-dz, 0, dx);
            case CLOCKWISE_180 -> new BlockPos(-dx, 0, -dz);
            case COUNTERCLOCKWISE_90 -> new BlockPos(dz, 0, -dx);
            default -> new BlockPos(dx, 0, dz);
        };
    }

    /** The rotation that turns the blueprint's front (north) to face {@code direction}. */
    public static Rotation facing(Direction direction) {
        return switch (direction) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static BlockState state(char c) {
        return switch (c) {
            case 'C' -> Blocks.COBBLESTONE.defaultBlockState();
            case 'T' -> Blocks.STONE_BRICKS.defaultBlockState();
            case 'Q' -> Blocks.RED_WOOL.defaultBlockState();
            case 'L' -> Blocks.OAK_LOG.defaultBlockState();
            case 'X' -> Blocks.SPRUCE_LOG.defaultBlockState();
            case 'P' -> Blocks.OAK_PLANKS.defaultBlockState();
            case 'S' -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case 'G' -> Blocks.GLASS_PANE.defaultBlockState();
            case 'K' -> Blocks.COARSE_DIRT.defaultBlockState();
            case 'W' -> Blocks.WHITE_WOOL.defaultBlockState();
            case 'R' -> Blocks.BROWN_WOOL.defaultBlockState();
            case 'F' -> Blocks.OAK_FENCE.defaultBlockState();
            case 'c' -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case 'D' -> door(DoubleBlockHalf.LOWER);
            case 'd' -> door(DoubleBlockHalf.UPPER);
            case 'b' -> bed(BedPart.FOOT);
            case 'B' -> bed(BedPart.HEAD);
            case 't' -> Blocks.TORCH.defaultBlockState();
            case 'l' -> Blocks.LANTERN.defaultBlockState();
            case 'h' -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            // On the south face of the log behind it, climbed from the north.
            case 'a' -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
            // Roof slopes rising toward the ridge in the middle.
            case 'e' -> Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            case 'w' -> Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
            case 'm' -> Blocks.FARMLAND.defaultBlockState();
            // School: blackboard, the teacher's lectern (turned to the class), benches, slab roof.
            case 'k' -> Blocks.BLACK_WOOL.defaultBlockState();
            case 'n' -> Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH);
            case 'v' -> Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            case 'o' -> Blocks.OAK_SLAB.defaultBlockState();
            case '~' -> Blocks.WATER.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    /** Facing into the building (you walk in heading south). */
    private static BlockState door(DoubleBlockHalf half) {
        return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, half).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
    }

    /** Foot toward the front, head toward the back wall. */
    private static BlockState bed(BedPart part) {
        return Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, part);
    }
}
