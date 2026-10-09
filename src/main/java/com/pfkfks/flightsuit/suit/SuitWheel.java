package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitRosterS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The suit wheel (DESIGN.md 4-1, R key, needs EDITH): lists every suit the player can reach - docked at
 * any of their stations, out as a companion, or packed in a capsule - and acts on the pick:
 * - SUMMON: send it out as a companion;
 * - WEAR: swap into it (the suit you're wearing stays out as a companion, or goes home if worn out);
 * - SET_MAIN: make that station the one G calls;
 * - ALL: House Party Protocol - every docked suit deploys, up to EDITH's control capacity;
 * - REMOTE: remote-pilot it (DESIGN.md 4-7) - a companion, or a docked suit straight off its station.
 */
public final class SuitWheel {
    public static final byte STATION = 0;
    public static final byte COMPANION = 1;
    public static final byte CAPSULE = 2;

    public static final byte SUMMON = 0;
    public static final byte WEAR = 1;
    public static final byte SET_MAIN = 2;
    public static final byte ALL = 3;
    public static final byte REMOTE = 4;

    /** How many suits EDITH can fly at once (DESIGN.md "이디스 제어 용량"; upgrades come later). */
    public static final int CONTROL_CAPACITY = 4;
    private static final double COMPANION_RANGE = 96.0D;
    /** Pause between jumping out of the old suit and the new one starting to assemble. */
    private static final int SWAP_DELAY_TICKS = 8;

    private SuitWheel() {
    }

    public record Entry(byte kind, long key, String name, int charge, int durability, int distance, boolean main, boolean broken) {
    }

    public static void open(ServerPlayer player) {
        if (!EdithGlassesItem.has(player)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.wheel_needs_edith"), true);
            return;
        }
        if (RemoteLink.isActive(player)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.remote_busy"), true);
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SuitRosterS2CPacket(roster(player)));
    }

    public static List<Entry> roster(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        MainStation.Link main = MainStation.get(player);
        for (MainStation.Link link : OwnedStations.all(player)) {
            if (!MainStation.isInPlayerDimension(player, link)) {
                continue;
            }
            SuitStationBlockEntity station = loadStation(player, link);
            if (station == null || !station.hasSuit() || station.getSuitType() == null) {
                continue;
            }
            entries.add(new Entry(STATION, link.pos().asLong(), station.getSuitType().hudName(),
                    chargePercent(station.getParts().values()), durabilityPercent(station.getParts().values()),
                    (int) Math.sqrt(link.pos().distToCenterSqr(player.position())), main != null && main.pos().equals(link.pos()),
                    anyBroken(station.getParts().values())));
        }
        for (SuitCompanionEntity suit : Companions.owned(player, COMPANION_RANGE)) {
            List<ItemStack> parts = new ArrayList<>();
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                parts.add(suit.getItemBySlot(slot));
            }
            SuitType type = typeOf(parts);
            entries.add(new Entry(COMPANION, suit.getId(), type == null ? "?" : type.hudName(), chargePercent(parts),
                    durabilityPercent(parts), (int) suit.distanceTo(player), false, anyBroken(parts)));
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.getItem() instanceof SuitCapsuleItem capsule && SuitCapsuleItem.hasParts(stack)) {
                Collection<ItemStack> parts = SuitCapsuleItem.getParts(stack).values();
                entries.add(new Entry(CAPSULE, slot, capsule.getSuitType().hudName(), chargePercent(parts),
                        durabilityPercent(parts), 0, false, anyBroken(parts)));
            }
        }
        return entries;
    }

    public static void act(ServerPlayer player, byte kind, long key, byte action) {
        if (!EdithGlassesItem.has(player) || SuitUpManager.isSuitingUp(player) || RemoteLink.isActive(player)) {
            return;
        }
        if (action == REMOTE) {
            remote(player, kind, key);
            return;
        }
        if (action == ALL) {
            houseParty(player);
            return;
        }
        if (action == SET_MAIN) {
            BlockPos pos = BlockPos.of(key);
            if (kind == STATION && OwnedStations.contains(player, pos)) {
                MainStation.set(player, pos);
                player.displayClientMessage(Component.translatable("message.flightsuit.main_station_set"), true);
            }
            return;
        }
        switch (kind) {
            case STATION -> {
                SuitStationBlockEntity station = loadOwnedStation(player, BlockPos.of(key));
                if (station == null || !station.hasSuit() || refuseBroken(player, station.getParts().values())) {
                    return;
                }
                if (action == WEAR) {
                    BlockPos stationPos = station.getBlockPos();
                    if (leaveCurrentSuit(player)) {
                        // Give the jump out a moment so the two suits don't overlap.
                        SuitUpManager.schedule(player, SWAP_DELAY_TICKS, () -> {
                            SuitStationBlockEntity again = loadOwnedStation(player, stationPos);
                            if (again != null && again.hasSuit()) {
                                SuitUpManager.callFromStation(player, again);
                            }
                        });
                    } else {
                        SuitUpManager.callFromStation(player, station);
                    }
                } else {
                    deployCompanion(player, station.takeAll(), station.dockPoint());
                }
            }
            case CAPSULE -> {
                int slot = (int) key;
                if (slot < 0 || slot >= player.getInventory().items.size()) {
                    return;
                }
                ItemStack capsule = player.getInventory().items.get(slot);
                if (!(capsule.getItem() instanceof SuitCapsuleItem) || !SuitCapsuleItem.hasParts(capsule)
                        || refuseBroken(player, SuitCapsuleItem.getParts(capsule).values())) {
                    return;
                }
                if (action == WEAR) {
                    if (leaveCurrentSuit(player)) {
                        SuitUpManager.schedule(player, SWAP_DELAY_TICKS, () -> {
                            ItemStack again = player.getInventory().items.get(slot);
                            if (again.getItem() instanceof SuitCapsuleItem && SuitCapsuleItem.hasParts(again)) {
                                SuitUpManager.startFromCapsule(player, again);
                            }
                        });
                    } else {
                        SuitUpManager.startFromCapsule(player, capsule);
                    }
                } else {
                    Map<EquipmentSlot, ItemStack> parts = SuitCapsuleItem.getParts(capsule);
                    SuitCapsuleItem.clearParts(capsule);
                    // Mark V suitcase: it unfolds right in front of you.
                    Vec3 front = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0D));
                    deployCompanion(player, parts, front);
                    player.serverLevel().sendParticles(ParticleTypes.POOF, front.x, front.y + 1.0D, front.z, 20, 0.4D, 0.8D, 0.4D, 0.05D);
                }
            }
            case COMPANION -> {
                if (!(player.serverLevel().getEntity((int) key) instanceof SuitCompanionEntity suit) || !suit.isOwnedBy(player)) {
                    return;
                }
                if (action == WEAR) {
                    leaveCurrentSuit(player);
                    suit.startBoarding();
                } else {
                    player.displayClientMessage(Component.translatable("message.flightsuit.companion_already_out"), true);
                }
            }
            default -> {
            }
        }
    }

    /** Open a remote link to a suit: a docked one powers up on its station platform, a companion right where it is. */
    private static void remote(ServerPlayer player, byte kind, long key) {
        if (!RemoteLink.canConnect(player)) {
            return;
        }
        switch (kind) {
            case STATION -> {
                SuitStationBlockEntity station = loadOwnedStation(player, BlockPos.of(key));
                if (station == null || !station.hasSuit() || refuseBroken(player, station.getParts().values())
                        || !RemoteLink.canPilot(player, station.getParts())) {
                    return;
                }
                float yaw = station.getBlockState().getValue(SuitStationBlock.FACING).toYRot();
                // Where the docked suit stands - the same spot as stepping into it at the station.
                Vec3 spot = Vec3.atBottomCenterOf(station.getBlockPos()).add(0.0D, 0.25D, 0.0D);
                RemoteLink.start(player, station.takeAll(), spot, yaw, false);
            }
            case COMPANION -> {
                if (!(player.serverLevel().getEntity((int) key) instanceof SuitCompanionEntity suit) || !suit.isOwnedBy(player)
                        || refuseBroken(player, suit.partsView().values()) || !RemoteLink.canPilot(player, suit.partsView())) {
                    return;
                }
                Vec3 spot = suit.position();
                float yaw = suit.getYRot();
                boolean airborne = !suit.onGround();
                Map<EquipmentSlot, ItemStack> parts = suit.takeParts();
                suit.discard();
                RemoteLink.start(player, parts, spot, yaw, airborne);
            }
            default -> player.displayClientMessage(Component.translatable("message.flightsuit.remote_capsule"), true);
        }
    }

    /**
     * Iron Man 3 swap: get out of the current suit. A healthy full suit stays out as a companion; a partial or
     * worn-out one goes home (or into a capsule). If you were flying you're now falling - the next suit
     * catches you mid-air.
     */
    private static boolean leaveCurrentSuit(ServerPlayer player) {
        WornSuit worn = WornSuit.of(player);
        if (!worn.any()) {
            return false;
        }
        boolean broken = false;
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            broken |= SuitArmorItem.isBroken(player.getItemBySlot(slot));
        }
        if (worn.fullSet() && !broken) {
            SuitUpManager.stepOut(player, false);
        } else {
            SuitUpManager.putAwayWorn(player);
        }
        // Creative flight too: a mid-air swap means dropping out of the old suit into the new one.
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        // Jump clear of the old suit so the two never overlap.
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        player.setDeltaMovement(forward.scale(0.7D).add(0.0D, player.onGround() ? 0.45D : 0.1D, 0.0D));
        player.hurtMarked = true;
        return true;
    }

    /** House Party Protocol: every healthy docked suit in this dimension flies out to you, up to capacity. */
    private static void houseParty(ServerPlayer player) {
        int out = Companions.owned(player, COMPANION_RANGE).size();
        int launched = 0;
        for (MainStation.Link link : OwnedStations.all(player)) {
            if (out + launched >= CONTROL_CAPACITY) {
                break;
            }
            if (!MainStation.isInPlayerDimension(player, link)) {
                continue;
            }
            SuitStationBlockEntity station = loadStation(player, link);
            if (station == null || !station.hasSuit() || anyBroken(station.getParts().values())) {
                continue;
            }
            deployCompanion(player, station.takeAll(), station.dockPoint());
            launched++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
        player.displayClientMessage(Component.translatable(launched > 0 ? "message.flightsuit.house_party" : "message.flightsuit.house_party_none",
                launched, CONTROL_CAPACITY), true);
    }

    /**
     * A suit flies out as a companion: from its station if that's close, otherwise it "arrives" from that
     * direction - spawned high up a short way off and flying in, since the station itself may be unloaded.
     */
    private static void deployCompanion(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts, Vec3 origin) {
        if (parts.isEmpty()) {
            return;
        }
        Vec3 toOrigin = origin.subtract(player.position());
        double distance = toOrigin.length();
        ServerLevel level = player.serverLevel();
        if (distance <= 40.0D) {
            // Close by: lift off from the station / unfold right there and fly over.
            SuitCompanionEntity.spawn(player, parts, origin, player.getYRot());
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 20, 0.3D, 0.2D, 0.3D, 0.1D);
            level.playSound(null, BlockPos.containing(origin), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
            return;
        }
        // Far: it shows up on the horizon in the station's direction, high up, and streaks in under its own power
        // (the station itself may be unloaded, so it can't literally take off from there).
        Vec3 spawn = player.position().add(toOrigin.normalize().scale(Math.min(distance, 80.0D))).add(0.0D, 16.0D, 0.0D);
        SuitCompanionEntity suit = SuitCompanionEntity.spawn(player, parts, spawn, player.getYRot());
        suit.startArrival();
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 0.6F);
    }

    private static boolean refuseBroken(ServerPlayer player, Collection<ItemStack> parts) {
        if (anyBroken(parts)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.suit_needs_repair"), true);
            return true;
        }
        return false;
    }

    private static SuitStationBlockEntity loadOwnedStation(ServerPlayer player, BlockPos pos) {
        return OwnedStations.contains(player, pos) ? loadStation(player, new MainStation.Link(player.level().dimension(), pos)) : null;
    }

    /** Loads the station's chunk if needed; forgets stations whose block is gone. */
    private static SuitStationBlockEntity loadStation(ServerPlayer player, MainStation.Link link) {
        ServerLevel level = player.serverLevel();
        level.getChunkAt(link.pos());
        if (level.getBlockEntity(link.pos()) instanceof SuitStationBlockEntity station) {
            return station;
        }
        OwnedStations.remove(player, link);
        return null;
    }

    private static SuitType typeOf(Collection<ItemStack> parts) {
        for (ItemStack stack : parts) {
            if (stack.getItem() instanceof SuitArmorItem armor) {
                return armor.getSuitType();
            }
        }
        return null;
    }

    private static int chargePercent(Collection<ItemStack> parts) {
        long energy = 0;
        long capacity = 0;
        for (ItemStack stack : parts) {
            energy += SuitEnergy.get(stack);
            capacity += SuitEnergy.capacity(stack);
        }
        return capacity <= 0 ? 0 : (int) (energy * 100 / capacity);
    }

    private static int durabilityPercent(Collection<ItemStack> parts) {
        int worst = 100;
        for (ItemStack stack : parts) {
            if (stack.isDamageableItem()) {
                worst = Math.min(worst, (stack.getMaxDamage() - stack.getDamageValue()) * 100 / stack.getMaxDamage());
            }
        }
        return worst;
    }

    private static boolean anyBroken(Collection<ItemStack> parts) {
        for (ItemStack stack : parts) {
            if (SuitArmorItem.isBroken(stack)) {
                return true;
            }
        }
        return false;
    }
}
