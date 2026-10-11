package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.EnumMap;
import java.util.Map;

/**
 * M18 power cable (DESIGN.md 5차 피드백 "공장식 전력"): a thin insulated copper line that joins power blocks across
 * any distance. A cable reaches toward each neighbour that is another cable or a block with an energy capability
 * (ours or another mod's); the grid follows runs of cables from each producer or battery (PowerGrid.nodesLinked).
 * No block entity, no state of its own: the run is traced when it's needed and remembered for a few seconds.
 */
public class CableBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final Map<Direction, BooleanProperty> SIDES = new EnumMap<>(Direction.class);
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        SIDES.put(Direction.NORTH, NORTH);
        SIDES.put(Direction.EAST, EAST);
        SIDES.put(Direction.SOUTH, SOUTH);
        SIDES.put(Direction.WEST, WEST);
        SIDES.put(Direction.UP, UP);
        SIDES.put(Direction.DOWN, DOWN);
        ARMS.put(Direction.NORTH, Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D));
        ARMS.put(Direction.SOUTH, Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D));
        ARMS.put(Direction.WEST, Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D));
        ARMS.put(Direction.EAST, Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D));
        ARMS.put(Direction.DOWN, Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D));
        ARMS.put(Direction.UP, Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D));
    }

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Another cable, or anything that takes or gives energy. */
    public static boolean connects(BlockGetter level, BlockPos pos, Direction toward) {
        BlockPos next = pos.relative(toward);
        BlockState state = level.getBlockState(next);
        if (state.getBlock() instanceof CableBlock) {
            return true;
        }
        var be = level.getBlockEntity(next);
        return be != null && be.getCapability(ForgeCapabilities.ENERGY, toward.getOpposite()).isPresent();
    }

    private BlockState connected(BlockGetter level, BlockPos pos, BlockState state) {
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), connects(level, pos, side.getKey()));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(SIDES.get(direction), connects(level, pos, direction));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        if (!old.is(this)) {
            PowerGrid.cablesChanged();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            PowerGrid.cablesChanged();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            if (state.getValue(side.getValue())) {
                shape = Shapes.or(shape, ARMS.get(side.getKey()));
            }
        }
        return shape;
    }
}
