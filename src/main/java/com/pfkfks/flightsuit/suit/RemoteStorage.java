package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.StationStorageBlockEntity;
import com.pfkfks.flightsuit.energy.PowerGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a remote-piloted suit sends what it picks up or digs out: the station storage paired with the suit's
 * own station (within the power-grid radius of it), else the one by the main station. The pilot's real
 * inventory is never touched - the body back home can't reach into the suit.
 */
public final class RemoteStorage {
    private record Found(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** Last storage found per pilot, so walking over a pile of drops doesn't rescan every tick. */
    private static final Map<UUID, Found> CACHE = new ConcurrentHashMap<>();
    /** Game time of the last "no storage / full" notice per pilot: standing on a pile fires a pickup every tick. */
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private RemoteStorage() {
    }

    /** The storage this pilot's suit delivers to, or null if its station has none nearby. */
    public static StationStorageBlockEntity find(ServerPlayer player) {
        MainStation.Link home = home(player);
        if (home == null) {
            return null;
        }
        ServerLevel level = player.server.getLevel(home.dimension());
        if (level == null) {
            return null;
        }
        Found cached = CACHE.get(player.getUUID());
        if (cached != null && cached.dimension().equals(home.dimension()) && near(cached.pos(), home.pos())) {
            level.getChunkAt(cached.pos());
            if (level.getBlockEntity(cached.pos()) instanceof StationStorageBlockEntity storage) {
                return storage;
            }
        }
        StationStorageBlockEntity best = scan(level, home.pos());
        if (best != null) {
            CACHE.put(player.getUUID(), new Found(home.dimension(), best.getBlockPos()));
        } else {
            CACHE.remove(player.getUUID());
        }
        return best;
    }

    /**
     * Sends a stack to the storage. Returns what didn't fit (the whole stack if there is no storage).
     * Tells the pilot on the action bar either way.
     */
    public static ItemStack deliver(ServerPlayer player, ItemStack stack) {
        StationStorageBlockEntity storage = find(player);
        if (storage == null) {
            warn(player, "message.flightsuit.remote_no_storage");
            return stack;
        }
        int count = stack.getCount();
        Component name = stack.getHoverName();
        ItemStack left = storage.insert(stack);
        int sent = count - left.getCount();
        if (sent > 0) {
            player.displayClientMessage(Component.translatable("message.flightsuit.remote_stored", name, sent), true);
        }
        if (!left.isEmpty()) {
            warn(player, "message.flightsuit.remote_storage_full");
        }
        return left;
    }

    public static void forget(UUID playerId) {
        CACHE.remove(playerId);
        LAST_WARNING.remove(playerId);
    }

    private static void warn(ServerPlayer player, String key) {
        long now = player.level().getGameTime();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last == null || now - last >= 40L || now < last) {
            LAST_WARNING.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    /** The station of the suit being piloted (stamped on its pieces), else the main station. */
    private static MainStation.Link home(ServerPlayer player) {
        List<ItemStack> pieces = new ArrayList<>();
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem) {
                pieces.add(stack);
            }
        }
        MainStation.Link home = SuitHome.of(pieces);
        return home != null ? home : MainStation.get(player);
    }

    private static boolean near(BlockPos storage, BlockPos station) {
        return storage.closerThan(station, PowerGrid.RADIUS + 0.5D);
    }

    /**
     * Loads the chunks around the station (it is usually far from the pilot) and picks the closest storage
     * in range. Reads the chunks' block entities directly: freshly loaded ones aren't on the power grid yet.
     */
    private static StationStorageBlockEntity scan(ServerLevel level, BlockPos station) {
        int r = PowerGrid.RADIUS;
        StationStorageBlockEntity best = null;
        for (int cx = (station.getX() - r) >> 4; cx <= (station.getX() + r) >> 4; cx++) {
            for (int cz = (station.getZ() - r) >> 4; cz <= (station.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof StationStorageBlockEntity storage && !storage.isRemoved() && near(storage.getBlockPos(), station)
                            && (best == null || storage.getBlockPos().distSqr(station) < best.getBlockPos().distSqr(station))) {
                        best = storage;
                    }
                }
            }
        }
        return best;
    }
}
