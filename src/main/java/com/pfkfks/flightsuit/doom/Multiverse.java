package com.pfkfks.flightsuit.doom;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModBlocks;
import com.pfkfks.flightsuit.suit.RemoteLink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crossing the multiverse (DESIGN.md 4-17; the user: "월드 이동은 지옥이나 천국모드의 천국처럼"): a frame of amethyst
 * blocks, any shape that closes (at least 2 wide and 3 tall inside, up to 21 by 21), lit with Reed's bridge key, like
 * a Nether portal. Three seconds inside and you're through:
 * - from any other world to Battleworld - every gate there opens onto the Fantastic Four's refuge (DoomSites.REFUGE),
 *   where the way back stands lit;
 * - from Battleworld back to the gate you came in by (DoomData.Origin), or to the world's spawn if it's gone.
 * Players only (the gate has no pull on mobs or items). Not while remote-linked, riding or watching a scene.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class Multiverse {
    public static final ResourceKey<Level> BATTLEWORLD = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(FlightSuitMod.MODID, "battleworld"));
    /** Ticks to stand in a gate before it takes you (creative: at once). */
    public static final int CROSS_TICKS = 60;
    private static final int MAX_SIDE = 21;
    private static final int COOLDOWN = 100;

    /** Per player: ticks inside, the game time last counted, and the time they may cross again. */
    private static final Map<UUID, long[]> INSIDE = new HashMap<>();

    private Multiverse() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        INSIDE.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }

    public static boolean isFrame(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK);
    }

    public static boolean inBattleworld(Level level) {
        return level.dimension() == BATTLEWORLD;
    }

    // ---------------------------------------------------------------- lighting

    /**
     * Fills the frame round {@code start} (an empty block inside it) with the gate: tries the frame standing along x,
     * then along z. True if it lit.
     */
    public static boolean light(ServerLevel level, BlockPos start) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            List<BlockPos> inside = enclosed(level, start, axis);
            if (inside != null) {
                BlockState gate = ModBlocks.MULTIVERSE_GATE.get().defaultBlockState().setValue(MultiverseGateBlock.AXIS, axis);
                for (BlockPos pos : inside) {
                    // Like the Nether portal: no neighbour updates while the sheet goes in, or it would put itself out.
                    level.setBlock(pos, gate, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                Vec3 middle = Vec3.atCenterOf(inside.get(inside.size() / 2));
                level.playSound(null, BlockPos.containing(middle), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7F, 1.4F);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.x, middle.y, middle.z, 80, 1.0D, 1.5D, 1.0D, 0.2D);
                com.pfkfks.flightsuit.fx.KiFx.burst(level, middle, 0x60FF90, 3.0F, 18, com.pfkfks.flightsuit.fx.KiFx.RING);
                return true;
            }
        }
        return false;
    }

    /**
     * The empty blocks enclosed by frame in the plane through {@code start} (4-way flood fill: corners don't matter,
     * just as in a Nether portal), or null if it leaks, hits something else, or is too small or too big.
     */
    private static @Nullable List<BlockPos> enclosed(ServerLevel level, BlockPos start, Direction.Axis axis) {
        if (!level.getBlockState(start).isAir()) {
            return null;
        }
        Direction[] sides = axis == Direction.Axis.X
                ? new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}
                : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            found.add(pos);
            int h = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            if (maxH - minH + 1 > MAX_SIDE || maxY - minY + 1 > MAX_SIDE) {
                return null;
            }
            for (Direction side : sides) {
                BlockPos next = pos.relative(side);
                if (seen.contains(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (isFrame(state)) {
                    continue;
                }
                if (!state.isAir()) {
                    return null;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        if (maxH - minH + 1 < 2 || maxY - minY + 1 < 3) {
            return null;
        }
        return found;
    }

    // ---------------------------------------------------------------- crossing

    /** A player stands in a gate this tick (MultiverseGateBlock.entityInside - maybe several blocks a tick). */
    static void inGate(ServerPlayer player, BlockPos pos) {
        long now = player.level().getGameTime();
        long[] state = INSIDE.computeIfAbsent(player.getUUID(), id -> new long[]{0L, -10L, 0L});
        if (state[1] == now) {
            return;
        }
        state[0] = state[1] == now - 1 ? state[0] + 1 : 1;
        state[1] = now;
        if (now < state[2]) {
            // Just came through: step out first.
            state[2] = now + 20;
            state[0] = 0;
            return;
        }
        ServerLevel level = player.serverLevel();
        if (state[0] == 1) {
            level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        if (state[0] == 20) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        }
        if (state[0] % 10 == 0) {
            com.pfkfks.flightsuit.fx.KiFx.burst(level, player.getBoundingBox().getCenter(), 0x7CFFB0, 1.5F, 10, com.pfkfks.flightsuit.fx.KiFx.RING);
        }
        if (state[0] < CROSS_TICKS && !player.getAbilities().instabuild) {
            return;
        }
        state[0] = 0;
        state[2] = now + COOLDOWN;
        if (RemoteLink.isActive(player) || player.isPassenger()
                || com.pfkfks.flightsuit.cutscene.CutsceneRunner.isWatching(player)) {
            player.displayClientMessage(Component.translatable("doom.flightsuit.gate.busy").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        cross(player, gateBottom(level, pos));
    }

    /** The lowest gate block in this column (where the frame's sill is). */
    private static BlockPos gateBottom(ServerLevel level, BlockPos pos) {
        BlockPos at = pos;
        while (level.getBlockState(at.below()).is(ModBlocks.MULTIVERSE_GATE.get())) {
            at = at.below();
        }
        return at.immutable();
    }

    private static void cross(ServerPlayer player, BlockPos gate) {
        ServerLevel here = player.serverLevel();
        DoomData data = DoomData.get(player.server);
        DoomData.Walker walker = data.walker(player.getUUID());
        if (!inBattleworld(here)) {
            ServerLevel battleworld = player.server.getLevel(BATTLEWORLD);
            if (battleworld == null) {
                player.sendSystemMessage(Component.translatable("doom.flightsuit.gate.no_world").withStyle(ChatFormatting.RED));
                return;
            }
            walker.origin = new DoomData.Origin(here.dimension(), gate);
            data.setDirty();
            Vec3 arrive = DoomSites.arrival(battleworld);
            flash(here, player);
            player.teleportTo(battleworld, arrive.x, arrive.y, arrive.z, DoomSites.ARRIVAL_YAW, 0.0F);
            player.fallDistance = 0.0F;
            flash(battleworld, player);
            DoomSaga.onArrived(player);
            return;
        }
        // Back home: beside the gate they came in by, or the world spawn if that's gone.
        DoomData.Origin origin = walker.origin;
        ServerLevel home = origin == null ? null : player.server.getLevel(origin.dimension());
        Vec3 out = null;
        if (home != null) {
            home.getChunkAt(origin.gate());
            out = besideGate(home, origin.gate());
        }
        if (out == null) {
            home = player.server.overworld();
            BlockPos spawn = home.getSharedSpawnPos();
            home.getChunkAt(spawn);
            out = Vec3.atBottomCenterOf(new BlockPos(spawn.getX(), home.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
                    spawn.getZ()));
        }
        flash(here, player);
        player.teleportTo(home, out.x, out.y, out.z, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
        flash(home, player);
        player.sendSystemMessage(Component.translatable("doom.flightsuit.gate.home").withStyle(ChatFormatting.GREEN));
    }

    /** Somewhere to stand just out of the gate at {@code gate} (either face), or null if the gate is gone. */
    private static @Nullable Vec3 besideGate(ServerLevel level, BlockPos gate) {
        BlockState state = level.getBlockState(gate);
        if (!state.is(ModBlocks.MULTIVERSE_GATE.get())) {
            return null;
        }
        Direction.Axis axis = state.getValue(MultiverseGateBlock.AXIS);
        Direction[] out = axis == Direction.Axis.X ? new Direction[]{Direction.SOUTH, Direction.NORTH} : new Direction[]{Direction.EAST, Direction.WEST};
        for (Direction dir : out) {
            for (int step = 1; step <= 2; step++) {
                BlockPos feet = gate.relative(dir, step);
                for (int dy = 0; dy >= -2; dy--) {
                    BlockPos at = feet.above(dy);
                    if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                            && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                            && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
                        return Vec3.atBottomCenterOf(at);
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(gate.relative(out[0]));
    }

    private static void flash(ServerLevel level, ServerPlayer player) {
        Vec3 c = player.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.6D, 1.0D, 0.6D, 0.3D);
        com.pfkfks.flightsuit.fx.KiFx.burst(level, c, 0x7CFFB0, 2.5F, 14, com.pfkfks.flightsuit.fx.KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.35F, 1.3F);
    }

    /** Builds a ready-lit gate: an amethyst frame {@code width} x {@code height} inside, sill at {@code base}, along x or z. */
    static void buildGate(ServerLevel level, BlockPos base, Direction.Axis axis, int width, int height) {
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        BlockState frame = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState gate = ModBlocks.MULTIVERSE_GATE.get().defaultBlockState().setValue(MultiverseGateBlock.AXIS, axis);
        for (int i = -1; i <= width; i++) {
            for (int j = -1; j <= height; j++) {
                BlockPos pos = base.relative(along, i).above(j);
                boolean edge = i == -1 || i == width || j == -1 || j == height;
                level.setBlock(pos, edge ? frame : gate, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
