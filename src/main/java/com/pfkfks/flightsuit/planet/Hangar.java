package com.pfkfks.flightsuit.planet;

import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.suit.MainStation;
import com.pfkfks.flightsuit.suit.OwnedStations;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.SuitUpManager;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The spaceship's hangar (2026-10-12, user approved): two suit bays that go wherever the player's ship goes. Loaded
 * at the home launch pad from the player's stations (and unloaded there back onto a station); on a planet the bays
 * are the ship's stations - the suit wheel lists them to send out or swap into, and suits sent home there come
 * back into the bays (SuitUpManager.storeReturningSuit). Kept per player in PlanetData (Traveller.hangar).
 */
public final class Hangar {
    public static final int BAYS = 2;

    private Hangar() {
    }

    private static List<CompoundTag> raw(ServerPlayer player) {
        return PlanetData.get(player.server).traveller(player.getUUID()).hangar;
    }

    private static Map<EquipmentSlot, ItemStack> read(CompoundTag tag) {
        Map<EquipmentSlot, ItemStack> parts = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (tag.contains(slot.getName())) {
                ItemStack stack = ItemStack.of(tag.getCompound(slot.getName()));
                if (!stack.isEmpty()) {
                    parts.put(slot, stack);
                }
            }
        }
        return parts;
    }

    private static CompoundTag write(Map<EquipmentSlot, ItemStack> parts) {
        CompoundTag tag = new CompoundTag();
        parts.forEach((slot, stack) -> tag.put(slot.getName(), stack.save(new CompoundTag())));
        return tag;
    }

    /** What's in the bays, in order. */
    public static List<Map<EquipmentSlot, ItemStack>> bays(ServerPlayer player) {
        List<Map<EquipmentSlot, ItemStack>> bays = new ArrayList<>();
        for (CompoundTag tag : raw(player)) {
            bays.add(read(tag));
        }
        return bays;
    }

    /** Takes bay {@code index} out (empty if there's none). */
    public static Map<EquipmentSlot, ItemStack> take(ServerPlayer player, int index) {
        List<CompoundTag> bays = raw(player);
        if (index < 0 || index >= bays.size()) {
            return new EnumMap<>(EquipmentSlot.class);
        }
        Map<EquipmentSlot, ItemStack> parts = read(bays.remove(index));
        PlanetData.get(player.server).setDirty();
        return parts;
    }

    /** Into a free bay; false if both are full. */
    public static boolean store(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts) {
        List<CompoundTag> bays = raw(player);
        if (parts.isEmpty() || bays.size() >= BAYS) {
            return false;
        }
        bays.add(write(parts));
        PlanetData.get(player.server).setDirty();
        return true;
    }

    /** The bays work as the ship's stations where the ship is the base: on a planet. */
    public static boolean usable(ServerPlayer player) {
        return Planet.of(player.level().dimension()) != null;
    }

    /** Where a suit leaves the ship from: the player's landed ship if it's about, else just in front of them. */
    public static Vec3 launchPoint(ServerPlayer player) {
        List<SpaceshipEntity> ships = player.serverLevel().getEntitiesOfClass(SpaceshipEntity.class, player.getBoundingBox().inflate(128.0D),
                ship -> !ship.inFlight() && player.getUUID().equals(ship.owner()));
        if (!ships.isEmpty()) {
            SpaceshipEntity ship = ships.get(0);
            return ship.position().add(0.0D, ship.getBbHeight() + 0.5D, 0.0D);
        }
        return player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0D));
    }

    public static @Nullable SuitType typeOf(Map<EquipmentSlot, ItemStack> parts) {
        for (ItemStack stack : parts.values()) {
            if (stack.getItem() instanceof SuitArmorItem armor) {
                return armor.getSuitType();
            }
        }
        return null;
    }

    private static Component name(Map<EquipmentSlot, ItemStack> parts) {
        SuitType type = typeOf(parts);
        return Component.literal(type == null ? "?" : type.hudName());
    }

    // ---------------------------------------------------------------- the pad and ship menus

    /** The hangar's lines under the pad menu (home: load and unload) or the ship menu (a planet: what's aboard). */
    public static void menu(ServerPlayer player, boolean atPad) {
        List<Map<EquipmentSlot, ItemStack>> bays = bays(player);
        MutableComponent line = Component.translatable("space.flightsuit.hangar", bays.size(), BAYS).withStyle(ChatFormatting.AQUA).append(" ");
        for (int i = 0; i < bays.size(); i++) {
            line.append(Component.literal(" ").append(name(bays.get(i))).withStyle(ChatFormatting.WHITE)).append(" ");
            if (atPad) {
                line.append(button(Component.translatable("space.flightsuit.button_unload"), "/spaceship hangar unload " + i,
                        Component.translatable("space.flightsuit.button_unload_hint"), ChatFormatting.YELLOW)).append(" ");
            }
        }
        player.sendSystemMessage(line);
        if (!atPad) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.hangar_hint").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (bays.size() >= BAYS) {
            return;
        }
        MutableComponent load = Component.translatable("space.flightsuit.hangar_load").withStyle(ChatFormatting.GRAY).append(" ");
        boolean any = false;
        for (MainStation.Link link : OwnedStations.all(player)) {
            if (!MainStation.isInPlayerDimension(player, link)) {
                continue;
            }
            SuitStationBlockEntity station = station(player, link.pos());
            if (station == null || !station.hasSuit() || station.getSuitType() == null) {
                continue;
            }
            load.append(button(Component.literal(station.getSuitType().hudName()), "/spaceship hangar load " + link.pos().asLong(),
                    Component.translatable("space.flightsuit.button_load_hint"), ChatFormatting.GREEN)).append(" ");
            any = true;
        }
        if (any) {
            player.sendSystemMessage(load);
        }
    }

    private static @Nullable SuitStationBlockEntity station(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        level.getChunkAt(pos);
        return level.getBlockEntity(pos) instanceof SuitStationBlockEntity station ? station : null;
    }

    /** From the pad: a station's suit into a bay. */
    static boolean load(ServerPlayer player, long key) {
        if (SpaceTravel.padNearPlayer(player) == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_pad").withStyle(ChatFormatting.GRAY));
            return false;
        }
        BlockPos pos = BlockPos.of(key);
        if (!OwnedStations.contains(player, pos)) {
            return false;
        }
        SuitStationBlockEntity station = station(player, pos);
        if (station == null || !station.hasSuit()) {
            return false;
        }
        if (bays(player).size() >= BAYS) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.hangar_full").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        Map<EquipmentSlot, ItemStack> parts = station.takeAll();
        store(player, parts);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 0.8F);
        player.sendSystemMessage(Component.translatable("space.flightsuit.hangar_loaded", name(parts)).withStyle(ChatFormatting.GREEN));
        menu(player, true);
        return true;
    }

    /** From the pad: a bay's suit back onto its station (else the main one, else into a capsule). */
    static boolean unload(ServerPlayer player, int index) {
        if (SpaceTravel.padNearPlayer(player) == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_pad").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Map<EquipmentSlot, ItemStack> parts = take(player, index);
        if (parts.isEmpty()) {
            return false;
        }
        SuitUpManager.storeReturningSuit(player, null, parts);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.8F, 0.8F);
        player.sendSystemMessage(Component.translatable("space.flightsuit.hangar_unloaded", name(parts)).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static MutableComponent button(Component label, String command, Component hint, ChatFormatting color) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hint)));
    }
}
