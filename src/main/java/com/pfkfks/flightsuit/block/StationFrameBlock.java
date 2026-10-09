package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The rest of the suit station around its core block: the platform around the center, two back pillars and
 * the overhead beam the ceiling arm hangs from (3x3 footprint, 4 high, open at the front and sides).
 * Placed and removed together with the core; clicking any of it is clicking the station, breaking any of it
 * breaks the whole station.
 */
public class StationFrameBlock extends Block {
    public enum Part implements StringRepresentable {
        PLATFORM("platform"),
        PILLAR("pillar"),
        PILLAR_TOP("pillar_top"),
        BEAM("beam");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape PLATFORM_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    private static final VoxelShape PILLAR_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    private static final VoxelShape BEAM_SHAPE = Block.box(0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape PILLAR_TOP_SHAPE = Shapes.or(Block.box(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D), BEAM_SHAPE);

    /** One frame block: offsets to the suit's right, up, and toward the back, from the core. */
    public record Placement(int right, int up, int back, Part part) {
        public BlockPos at(BlockPos core, Direction facing) {
            Direction rightDir = facing.getClockWise();
            return core.relative(rightDir, right).above(up).relative(facing.getOpposite(), back);
        }
    }

    public static final List<Placement> LAYOUT = buildLayout();

    private static List<Placement> buildLayout() {
        List<Placement> layout = new ArrayList<>();
        for (int right = -1; right <= 1; right++) {
            for (int back = -1; back <= 1; back++) {
                if (right != 0 || back != 0) {
                    layout.add(new Placement(right, 0, back, Part.PLATFORM));
                }
            }
        }
        for (int side : new int[]{-1, 1}) {
            layout.add(new Placement(side, 1, 1, Part.PILLAR));
            layout.add(new Placement(side, 2, 1, Part.PILLAR));
            layout.add(new Placement(side, 3, 1, Part.PILLAR_TOP));
        }
        layout.add(new Placement(0, 3, 1, Part.BEAM));
        layout.add(new Placement(0, 3, 0, Part.BEAM));
        return layout;
    }

    public StationFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.PLATFORM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case PLATFORM -> PLATFORM_SHAPE;
            case PILLAR -> PILLAR_SHAPE;
            case PILLAR_TOP -> PILLAR_TOP_SHAPE;
            case BEAM -> BEAM_SHAPE;
        };
    }

    // ---------------------------------------------------------------- the whole structure

    /** Whether every frame position around a core here is free (or already this core's own frame). */
    public static boolean hasRoom(Level level, BlockPos core, Direction facing) {
        for (Placement placement : LAYOUT) {
            BlockPos pos = placement.at(core, facing);
            BlockState state = level.getBlockState(pos);
            boolean ownFrame = state.getBlock() instanceof StationFrameBlock && core.equals(findCore(level, pos));
            if (!state.canBeReplaced() && !ownFrame) {
                return false;
            }
        }
        return true;
    }

    public static void build(Level level, BlockPos core, Direction facing) {
        BlockState frame = ModBlocks.STATION_FRAME.get().defaultBlockState();
        for (Placement placement : LAYOUT) {
            level.setBlock(placement.at(core, facing), frame.setValue(PART, placement.part()), Block.UPDATE_ALL);
        }
    }

    /** Removes the frame of the station at {@code core} (only blocks that really are frame). */
    public static void clear(LevelAccessor level, BlockPos core, Direction facing) {
        for (Placement placement : LAYOUT) {
            BlockPos pos = placement.at(core, facing);
            if (level.getBlockState(pos).getBlock() instanceof StationFrameBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** The station core this frame block belongs to, or null. */
    public static BlockPos findCore(BlockGetter level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 0, 2))) {
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof SuitStationBlock) {
                Direction facing = state.getValue(SuitStationBlock.FACING);
                for (Placement placement : LAYOUT) {
                    if (placement.at(candidate, facing).equals(pos)) {
                        return candidate.immutable();
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- acts as the station

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos core = findCore(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(core).use(level, player, hand, hit.withPosition(core));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = findCore(level, pos);
        if (core != null && !level.isClientSide) {
            // Breaking any part takes the whole station down; it drops once, as the station.
            if (player.isCreative()) {
                level.removeBlock(core, false);
            } else {
                level.destroyBlock(core, true, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            // Explosions and the like: lose a part, lose the station.
            BlockPos core = findCore(level, pos);
            if (core != null) {
                level.destroyBlock(core, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.SUIT_STATION.get());
    }
}
