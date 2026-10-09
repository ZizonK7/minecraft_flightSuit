package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suiting up and down at a station with its robot arms (DESIGN.md 4-4 "스테이션 위", Iron Man 2 / Avengers
 * catwalk). The choreography lives in StationRigTimeline; this side walks the player onto the platform,
 * holds them there, and moves the real items at the right ticks. Pieces in the arms' hands are held here,
 * so an interrupted run (logout, death, station broken) can always put every piece somewhere.
 */
public final class StationRig {
    private static final class Run {
        final byte mode;
        final BlockPos stationPos;
        final Vec3 spot;
        final float yaw;
        /** SUIT_UP: pieces not on the player yet. SUIT_OFF: pieces taken off so far. */
        final Map<EquipmentSlot, ItemStack> pieces = new EnumMap<>(EquipmentSlot.class);
        int age;

        Run(byte mode, BlockPos stationPos, Vec3 spot, float yaw) {
            this.mode = mode;
            this.stationPos = stationPos;
            this.spot = spot;
            this.yaw = yaw;
        }
    }

    private static final Map<UUID, Run> ACTIVE = new HashMap<>();

    private StationRig() {
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Where the docked suit stands, and so where the wearer stands on the rig. */
    private static Vec3 spotOf(SuitStationBlockEntity station) {
        return Vec3.atBottomCenterOf(station.getBlockPos()).add(0.0D, 0.25D, 0.0D);
    }

    private static float yawOf(SuitStationBlockEntity station) {
        return station.getBlockState().getValue(SuitStationBlock.FACING).toYRot();
    }

    private static String suitIdOf(Map<EquipmentSlot, ItemStack> pieces) {
        for (ItemStack stack : pieces.values()) {
            if (stack.getItem() instanceof SuitArmorItem armor) {
                return armor.getSuitType().id();
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- start

    public static void startSuitUp(ServerPlayer player, SuitStationBlockEntity station) {
        Map<EquipmentSlot, ItemStack> parts = station.takeAll();
        Run run = new Run(StationRigTimeline.SUIT_UP, station.getBlockPos(), spotOf(station), yawOf(station));
        run.pieces.putAll(parts);
        begin(player, station, run, parts.keySet(), suitIdOf(parts), SuitAnim.STATION_RIG);
    }

    public static void startSuitOff(ServerPlayer player, SuitStationBlockEntity station, Map<EquipmentSlot, ItemStack> worn) {
        Run run = new Run(StationRigTimeline.SUIT_OFF, station.getBlockPos(), spotOf(station), yawOf(station));
        begin(player, station, run, worn.keySet(), suitIdOf(worn), SuitAnim.STATION_UNRIG);
    }

    private static void begin(ServerPlayer player, SuitStationBlockEntity station, Run run, Iterable<EquipmentSlot> slots,
                              String suitId, SuitAnim anim) {
        ACTIVE.put(player.getUUID(), run);
        station.startRig(run.mode, suitId, slots);
        // Walk onto the platform, not fly: drop out of flight (creative flight too).
        SuitServerEvents.revokeSuitFlightNow(player);
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        level.playSound(null, run.stationPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.6F);
        level.playSound(null, run.stationPos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, anim, StationRigTimeline.end(run.mode)));
    }

    // ---------------------------------------------------------------- tick

    public static void tick(ServerPlayer player) {
        Run run = ACTIVE.get(player.getUUID());
        if (run == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(run.stationPos) instanceof SuitStationBlockEntity station)) {
            // Station gone mid-run: nothing to hold the pieces any more.
            finishNow(player);
            return;
        }
        int t = ++run.age;
        if (run.mode == StationRigTimeline.SUIT_UP) {
            tickSuitUp(level, player, run, t);
        } else {
            tickSuitOff(level, player, station, run, t);
        }
        if (t >= StationRigTimeline.end(run.mode)) {
            ACTIVE.remove(player.getUUID());
            station.endRig();
            if (run.mode == StationRigTimeline.SUIT_UP) {
                player.displayClientMessage(Component.translatable("message.flightsuit.suit_online"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.flightsuit.station_docked"), true);
            }
        }
    }

    private static void tickSuitUp(ServerLevel level, ServerPlayer player, Run run, int t) {
        if (t == StationRigTimeline.UP_REACH) {
            // The arms grab the standing suit and pull it apart.
            level.playSound(null, run.stationPos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8F, 1.1F);
            level.playSound(null, run.stationPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.3F);
        }
        if (t >= StationRigTimeline.UP_WALK_START && t < StationRigTimeline.UP_WALK_END) {
            walkTo(player, run.spot);
        } else if (t >= StationRigTimeline.UP_WALK_END) {
            pin(player, run);
        }
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack piece = run.pieces.get(slot);
            if (piece == null) {
                continue;
            }
            if (t == StationRigTimeline.attachStart(slot)) {
                level.playSound(null, run.stationPos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            if (t > StationRigTimeline.attachStart(slot) && t < StationRigTimeline.attachEnd(slot)) {
                weld(level, run, slot);
            }
            if (t >= StationRigTimeline.attachEnd(slot)) {
                run.pieces.remove(slot);
                SuitUpManager.equipPiece(player, slot, piece, true);
            }
        }
        if (t == StationRigTimeline.UP_EYES) {
            SuitUpManager.eyesOn(level, player);
        }
    }

    private static void tickSuitOff(ServerLevel level, ServerPlayer player, SuitStationBlockEntity station, Run run, int t) {
        if (t < StationRigTimeline.OFF_WALK_END) {
            walkTo(player, run.spot);
        } else if (t < StationRigTimeline.OFF_STEP_OUT_START) {
            pin(player, run);
        } else if (t < StationRigTimeline.OFF_STEP_OUT_END) {
            // Step off the platform, forward, while the arms keep the suit.
            Vec3 forward = Vec3.directionFromRotation(0.0F, run.yaw);
            player.setDeltaMovement(forward.x * 0.16D, player.getDeltaMovement().y, forward.z * 0.16D);
            player.hurtMarked = true;
        }
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (t != StationRigTimeline.detach(slot) || !(player.getItemBySlot(slot).getItem() instanceof SuitArmorItem)) {
                continue;
            }
            run.pieces.put(slot, player.getItemBySlot(slot).copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (slot == EquipmentSlot.CHEST || slot == EquipmentSlot.FEET) {
                SuitServerEvents.revokeSuitFlightNow(player);
            }
            Vec3 seam = run.spot.add(0.0D, pieceHeight(slot), 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, seam.x, seam.y, seam.z, 12, 0.3D, 0.2D, 0.3D, 0.15D);
            level.playSound(null, run.stationPos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6F, 1.3F);
            level.playSound(null, run.stationPos, SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        if (t == StationRigTimeline.OFF_STEP_OUT_START) {
            EdithGlassesItem.reequip(player);
        }
        if (t == StationRigTimeline.OFF_DOCK) {
            // The arms have stood the empty suit back up: it's docked.
            store(player, station, run);
            level.playSound(null, run.stationPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.2F);
            level.playSound(null, run.stationPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
        }
    }

    /** Ease the player toward the platform; they arrive by the end of the walk window or get placed there. */
    private static void walkTo(ServerPlayer player, Vec3 spot) {
        Vec3 to = spot.subtract(player.position());
        if (to.horizontalDistance() > 0.15D) {
            Vec3 step = new Vec3(to.x, 0.0D, to.z).normalize().scale(Math.min(0.2D, to.horizontalDistance() * 0.5D));
            player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
            player.hurtMarked = true;
        }
    }

    /** On the platform, facing out: keep them exactly where the arms expect the body. */
    private static void pin(ServerPlayer player, Run run) {
        if (player.position().distanceToSqr(run.spot) > 0.0025D || Mth.degreesDifferenceAbs(player.getYRot(), run.yaw) > 1.0F) {
            player.connection.teleport(run.spot.x, run.spot.y, run.spot.z, run.yaw, player.getXRot());
            player.setYBodyRot(run.yaw);
            player.setYHeadRot(run.yaw);
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void weld(ServerLevel level, Run run, EquipmentSlot slot) {
        Vec3 seam = run.spot.add(0.0D, pieceHeight(slot), 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, seam.x, seam.y, seam.z, 3, 0.3D, 0.15D, 0.3D, 0.12D);
        if (run.age % 3 == 0) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, seam.x, seam.y, seam.z, 1, 0.25D, 0.1D, 0.25D, 0.0D);
        }
    }

    private static double pieceHeight(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 1.65D;
            case CHEST -> 1.2D;
            case LEGS -> 0.6D;
            default -> 0.15D;
        };
    }

    /** Puts the run's loose pieces into the station, or back with the player if it can't take them. */
    private static void store(ServerPlayer player, SuitStationBlockEntity station, Run run) {
        if (run.pieces.isEmpty()) {
            return;
        }
        Map<EquipmentSlot, ItemStack> pieces = new EnumMap<>(run.pieces);
        run.pieces.clear();
        if (station != null && station.canDock(pieces)) {
            station.dock(pieces);
            return;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> entry : pieces.entrySet()) {
            if (player.getItemBySlot(entry.getKey()).isEmpty()) {
                player.setItemSlot(entry.getKey(), entry.getValue());
            } else if (!player.getInventory().add(entry.getValue())) {
                player.drop(entry.getValue(), false);
            }
        }
    }

    // ---------------------------------------------------------------- interruption

    /** Ends a run right away: suiting up puts every remaining piece on; suiting down stores what came off. */
    public static void finishNow(ServerPlayer player) {
        Run run = ACTIVE.remove(player.getUUID());
        if (run == null) {
            return;
        }
        SuitStationBlockEntity station = player.serverLevel().getBlockEntity(run.stationPos) instanceof SuitStationBlockEntity found ? found : null;
        if (run.mode == StationRigTimeline.SUIT_UP) {
            for (Map.Entry<EquipmentSlot, ItemStack> entry : run.pieces.entrySet()) {
                SuitUpManager.equipPiece(player, entry.getKey(), entry.getValue(), false);
            }
            run.pieces.clear();
        } else {
            store(player, station, run);
        }
        if (station != null) {
            station.endRig();
        }
    }

    public static void forget(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
