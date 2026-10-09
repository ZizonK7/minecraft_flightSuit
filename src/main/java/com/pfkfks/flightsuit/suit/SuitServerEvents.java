package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.EdithStatusS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-tick suit effects on the server: helmet night vision, full-set flight permission, boots thrust
 * drain, energy accounting, and the broadcast flight pose.
 *
 * Flight piggybacks on vanilla's creative-style flying (mayfly), which already handles the double-tap
 * toggle, up/down, landing and fall-damage immunity; SuitFlightClient adds the Iron Man boost on top.
 * We only revoke flight we granted ourselves (persistent tag), so creative players are untouched.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class SuitServerEvents {
    private static final String GRANTED_FLIGHT_TAG = "flightsuit_granted_flight";
    private static final String GRANTED_NIGHT_VISION_TAG = "flightsuit_night_vision";

    private static final Set<UUID> THRUSTING = new HashSet<>();
    private static final Map<UUID, FlightPose> POSES = new HashMap<>();

    private SuitServerEvents() {
    }

    public static void setThrusting(ServerPlayer player, boolean thrusting) {
        if (thrusting) {
            THRUSTING.add(player.getUUID());
        } else {
            THRUSTING.remove(player.getUUID());
        }
    }

    public static FlightPose poseOf(ServerPlayer player) {
        return POSES.getOrDefault(player.getUUID(), FlightPose.NONE);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        SuitUpManager.tick(player);
        RemoteLink.tick(player);
        SuitWeapons.tick(player);
        CounterHandler.tick(player);

        WornSuit worn = WornSuit.of(player);
        CompoundTag data = player.getPersistentData();
        tickNightVision(player, worn, data);
        tickFlight(player, worn, data);
        tickThrust(player, worn);
        StealthHandler.tick(player, worn);
        updatePose(player, worn);
        if (player.tickCount % 20 == 0 && EdithGlassesItem.has(player)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), EdithStatusS2CPacket.of(player));
        }
    }

    private static void tickNightVision(ServerPlayer player, WornSuit worn, CompoundTag data) {
        boolean active = worn.helmet() && SuitEnergy.tryDrain(player, EquipmentSlot.HEAD, SuitTuning.NIGHT_VISION_COST);
        if (active) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            // Vanilla starts flickering night vision under 200 ticks, so keep it topped up well above that.
            if (current == null || current.getDuration() < 300) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
            data.putBoolean(GRANTED_NIGHT_VISION_TAG, true);
        } else if (data.getBoolean(GRANTED_NIGHT_VISION_TAG)) {
            data.remove(GRANTED_NIGHT_VISION_TAG);
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current != null && current.getDuration() <= 400) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
    }

    private static void tickFlight(ServerPlayer player, WornSuit worn, CompoundTag data) {
        Abilities abilities = player.getAbilities();
        // No "not suiting up" gate: the mid-air suit-up hands over to flight before its sequence ends.
        boolean canFly = worn.canFly() && SuitEnergy.available(player, EquipmentSlot.CHEST) > 0;
        boolean granted = data.getBoolean(GRANTED_FLIGHT_TAG);

        if (canFly && !granted && !abilities.mayfly) {
            abilities.mayfly = true;
            abilities.setFlyingSpeed(SuitTuning.SUIT_FLYING_SPEED);
            data.putBoolean(GRANTED_FLIGHT_TAG, true);
            player.onUpdateAbilities();
        } else if (!canFly && granted) {
            revokeFlight(player, data);
        }

        // Creative players already have mayfly (so nothing was granted) but still fly "in the suit";
        // tryDrain is free for them, so this only ever revokes flight we granted.
        if (worn.canFly() && abilities.flying) {
            int cost = player.isSprinting() ? SuitTuning.BOOST_COST : SuitTuning.HOVER_COST;
            if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, cost) && granted) {
                revokeFlight(player, data);
            }
        }
    }

    /** Mid-air suit-up brake: grant suit flight right away and switch it on, without waiting for the next tick. */
    public static void grantFlightNow(ServerPlayer player) {
        WornSuit worn = WornSuit.of(player);
        if (worn.fullSet() && !worn.canFly()) {
            // A grounded suit just drops you; the full set takes the landing (see onFall).
            player.fallDistance = 0.0F;
            return;
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            abilities.setFlyingSpeed(SuitTuning.SUIT_FLYING_SPEED);
            player.getPersistentData().putBoolean(GRANTED_FLIGHT_TAG, true);
        }
        abilities.flying = true;
        player.fallDistance = 0.0F;
        player.onUpdateAbilities();
    }

    /** Drops suit-granted flight immediately (instead of on the next tick) - used when stepping out mid-air. */
    public static void revokeSuitFlightNow(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(GRANTED_FLIGHT_TAG)) {
            revokeFlight(player, data);
        }
    }

    private static void revokeFlight(ServerPlayer player, CompoundTag data) {
        data.remove(GRANTED_FLIGHT_TAG);
        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(SuitTuning.VANILLA_FLYING_SPEED);
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    private static void tickThrust(ServerPlayer player, WornSuit worn) {
        if (!THRUSTING.contains(player.getUUID())) {
            return;
        }
        if (!worn.boots() || player.onGround() || !SuitEnergy.tryDrain(player, EquipmentSlot.FEET, SuitTuning.THRUST_COST)) {
            THRUSTING.remove(player.getUUID());
            return;
        }
        player.fallDistance = 0.0F;
    }

    private static void updatePose(ServerPlayer player, WornSuit worn) {
        FlightPose pose;
        // Based on wearing the full set, not on who granted flight - creative players fly in the suit too.
        if (worn.fullSet() && player.getAbilities().flying) {
            pose = player.isSprinting() ? FlightPose.BOOST : FlightPose.HOVER;
        } else if (THRUSTING.contains(player.getUUID())) {
            pose = FlightPose.THRUST;
        } else {
            pose = FlightPose.NONE;
        }
        FlightPose previous = POSES.put(player.getUUID(), pose);
        if (previous != pose) {
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.pose(player, pose));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            FlightPose pose = poseOf(target);
            if (pose != FlightPose.NONE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), SuitAnimS2CPacket.pose(target, pose));
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        WornSuit worn = WornSuit.of(player);
        if (worn.fullSet() || SuitUpManager.isSuitingUp(player)) {
            event.setCanceled(true);
        } else if (worn.legs()) {
            event.setDistance(event.getDistance() * SuitTuning.LEGS_FALL_MULTIPLIER);
        }
    }

    /** Short invulnerability while the suit is assembling (DESIGN.md 4-4 game rules). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SuitUpManager.isSuitingUp(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Equip in-flight pieces before the inventory drops so nothing vanishes.
            SuitUpManager.finishNow(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Back into the body before the player is saved, so they log in where they left it.
            RemoteLink.end(player, RemoteLink.End.LOGOUT);
            CardDuel.forget(player);
            SuitUpManager.finishNow(player);
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SuitUpManager.finishNow(player);
        }
    }

    private static void forget(UUID id) {
        THRUSTING.remove(id);
        POSES.remove(id);
        SuitUpManager.forget(id);
        SuitWeapons.forget(id);
        CounterHandler.forget(id);
        StealthHandler.forget(id);
    }
}
