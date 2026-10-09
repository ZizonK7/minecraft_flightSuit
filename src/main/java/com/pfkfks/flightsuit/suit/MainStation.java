package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The player's main station (DESIGN.md 4-1 "메인 슈트를 지정"): the last station they used. EDITH calls the
 * suit from here and sends it back here. Stored in the player's persistent NBT so it survives relogs.
 */
public final class MainStation {
    private static final String TAG = "flightsuit_main_station";

    private MainStation() {
    }

    public record Link(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public static void set(ServerPlayer player, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", player.level().dimension().location().toString());
        tag.putLong("Pos", pos.asLong());
        player.getPersistentData().put(TAG, tag);
        OwnedStations.add(player, pos);
    }

    public static Link get(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG)) {
            return null;
        }
        CompoundTag tag = data.getCompound(TAG);
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        if (dim == null) {
            return null;
        }
        return new Link(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("Pos")));
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    public static boolean isInPlayerDimension(ServerPlayer player, Link link) {
        return link != null && link.dimension().equals(player.level().dimension());
    }

    /**
     * The main station in the player's current dimension, force-loading its chunk if needed (a one-off
     * load when the suit is called or sent home). Clears a link whose station block is gone.
     */
    public static SuitStationBlockEntity resolve(ServerPlayer player) {
        Link link = get(player);
        if (!isInPlayerDimension(player, link)) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        level.getChunkAt(link.pos());
        if (level.getBlockEntity(link.pos()) instanceof SuitStationBlockEntity station) {
            return station;
        }
        clear(player);
        return null;
    }

    /** Same as {@link #resolve} but never loads chunks - for periodic HUD status. */
    public static SuitStationBlockEntity peek(ServerPlayer player) {
        Link link = get(player);
        if (!isInPlayerDimension(player, link) || !player.level().isLoaded(link.pos())) {
            return null;
        }
        return player.level().getBlockEntity(link.pos()) instanceof SuitStationBlockEntity station ? station : null;
    }
}
