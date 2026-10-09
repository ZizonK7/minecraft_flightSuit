package com.pfkfks.flightsuit.suit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Every station a player has used (one suit each), so the suit wheel and the all-suits mode can reach all of
 * them - the main station is just the one G calls by default. Stored in the player's persistent NBT.
 */
public final class OwnedStations {
    private static final String TAG = "flightsuit_stations";
    private static final int MAX = 16;

    private OwnedStations() {
    }

    public static void add(ServerPlayer player, BlockPos pos) {
        String dim = player.level().dimension().location().toString();
        ListTag list = player.getPersistentData().getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getString("Dim").equals(dim) && entry.getLong("Pos") == pos.asLong()) {
                return;
            }
        }
        if (list.size() >= MAX) {
            list.remove(0);
        }
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", dim);
        entry.putLong("Pos", pos.asLong());
        list.add(entry);
        player.getPersistentData().put(TAG, list);
    }

    public static List<MainStation.Link> all(Player player) {
        List<MainStation.Link> links = new ArrayList<>();
        ListTag list = player.getPersistentData().getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("Dim"));
            if (dim != null) {
                links.add(new MainStation.Link(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(entry.getLong("Pos"))));
            }
        }
        return links;
    }

    public static boolean contains(Player player, BlockPos pos) {
        for (MainStation.Link link : all(player)) {
            if (link.pos().equals(pos) && link.dimension().equals(player.level().dimension())) {
                return true;
            }
        }
        return false;
    }

    public static void remove(ServerPlayer player, MainStation.Link gone) {
        ListTag list = player.getPersistentData().getList(TAG, Tag.TAG_COMPOUND);
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getString("Dim").equals(gone.dimension().location().toString()) && entry.getLong("Pos") == gone.pos().asLong()) {
                list.remove(i);
            }
        }
        player.getPersistentData().put(TAG, list);
    }
}
