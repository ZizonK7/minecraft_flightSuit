package com.pfkfks.flightsuit.doom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * The multiverse gate (DESIGN.md 4-17): the shimmering sheet that fills an amethyst frame once Reed's bridge key
 * touches it - like a Nether portal, but it goes to Battleworld (and from Battleworld, back where you came from).
 * Stand in it for three seconds. Break the frame and it goes out.
 */
public class MultiverseGateBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_AXIS = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape Z_AXIS = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35F, 1.0F, 0.55F), 1.0F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.45F, 1.0F), 1.0F);

    public MultiverseGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS : X_AXIS;
    }

    /** Goes out when what holds it in its plane (frame or more gate) is gone - the whole sheet follows, block by block. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis along = direction.getAxis();
        boolean inPlane = along == Direction.Axis.Y || along == state.getValue(AXIS);
        if (inPlane && !neighbor.is(this) && !Multiverse.isFrame(neighbor)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            Multiverse.inGate(player, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.6F, 0.5F + random.nextFloat() * 0.4F, false);
        }
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 2; i++) {
            double px = pos.getX() + (x ? random.nextDouble() : 0.5D + (random.nextDouble() - 0.5D) * 0.2D);
            double py = pos.getY() + random.nextDouble();
            double pz = pos.getZ() + (x ? 0.5D + (random.nextDouble() - 0.5D) * 0.2D : random.nextDouble());
            level.addParticle(random.nextBoolean() ? GREEN : VIOLET, px, py, pz, 0.0D, 0.02D, 0.0D);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }
}
