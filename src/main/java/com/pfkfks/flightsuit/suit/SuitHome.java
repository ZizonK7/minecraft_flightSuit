package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;

/**
 * Each suit remembers the station it last docked at (stamped on its pieces), so "go home" means *its*
 * station - not whichever station happens to be the player's main one. Falls back to the main station.
 */
public final class SuitHome {
    private static final String TAG = "flightsuit_home";

    private SuitHome() {
    }

    public static void stamp(ItemStack piece, Level level, BlockPos stationPos) {
        CompoundTag home = new CompoundTag();
        home.putString("Dim", level.dimension().location().toString());
        home.putLong("Pos", stationPos.asLong());
        piece.getOrCreateTag().put(TAG, home);
    }

    public static MainStation.Link of(Collection<ItemStack> pieces) {
        for (ItemStack piece : pieces) {
            CompoundTag tag = piece.getTag();
            if (tag != null && tag.contains(TAG)) {
                CompoundTag home = tag.getCompound(TAG);
                ResourceLocation dim = ResourceLocation.tryParse(home.getString("Dim"));
                if (dim != null) {
                    return new MainStation.Link(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(home.getLong("Pos")));
                }
            }
        }
        return null;
    }

    /**
     * Where these pieces should go when sent home: their own station if it still exists in this dimension
     * and has room for them, otherwise the main station (if that has room), otherwise null.
     */
    public static SuitStationBlockEntity resolve(ServerPlayer player, Map<net.minecraft.world.entity.EquipmentSlot, ItemStack> pieces) {
        MainStation.Link home = of(pieces.values());
        if (home != null && MainStation.isInPlayerDimension(player, home)) {
            ServerLevel level = player.serverLevel();
            level.getChunkAt(home.pos());
            if (level.getBlockEntity(home.pos()) instanceof SuitStationBlockEntity station && station.canDock(pieces)) {
                return station;
            }
        }
        SuitStationBlockEntity main = MainStation.resolve(player);
        return main != null && main.canDock(pieces) ? main : null;
    }
}
