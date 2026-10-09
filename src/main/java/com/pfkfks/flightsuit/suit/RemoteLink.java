package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.RemoteLinkS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Remote piloting (DESIGN.md 4-7, A안 - Iron Man 3's remote suit): from the suit wheel, take direct control
 * of a companion suit or a suit docked at any of your stations, however far away.
 *
 * The player really moves into the suit - teleported to it, wearing its pieces - so flight, repulsors, the
 * shield, the cloak and chunk loading all work exactly as when wearing it. What stays behind is the body
 * (RemoteBodyEntity): standing defenceless where the link was opened, its chunk kept loaded so monsters can
 * still reach it. While linked, hits on the suit wear its pieces instead of the player's health; the first hit
 * on the body snaps the player back into it and lands on them.
 *
 * When the link drops, the player is back at the body and the suit stays out as a companion if it's close
 * to the body and fit to fight, otherwise it heads back to its station (or a capsule).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class RemoteLink {
    /** Persistent tag: where the body was, so a crash mid-link still brings the player home on next login. */
    private static final String TAG = "flightsuit_remote";
    /** A suit dropped within this range of the body stays out as a companion; beyond it, it flies home. */
    private static final double STAY_RANGE = 96.0D;
    private static final TicketType<ChunkPos> BODY_TICKET =
            TicketType.create("flightsuit_remote_body", Comparator.comparingLong(ChunkPos::toLong), 100);

    public enum End {
        /** G tap. */
        DISCONNECT,
        /** G hold: the suit goes home along with everyone else. */
        RECALL,
        /** A piece wore out - straight home for repairs. */
        BROKEN,
        NO_POWER,
        /** Something hit the body. */
        BODY_HIT,
        LOGOUT,
        /** Body gone, suit pieces taken off, killed - anything else that breaks the link. */
        LOST
    }

    private static final class Session {
        final RemoteBodyEntity body;
        final Vec3 bodyPos;
        final float yaw;
        final float pitch;

        Session(RemoteBodyEntity body, Vec3 bodyPos, float yaw, float pitch) {
            this.body = body;
            this.bodyPos = bodyPos;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /** A block a pilot just broke: its drops spawn this same tick, around its centre. */
    private record BreakCapture(ResourceKey<Level> dimension, Vec3 center, long gameTime, UUID player) {
    }

    private static final List<BreakCapture> BREAK_DROPS = new CopyOnWriteArrayList<>();

    /** By player UUID. Concurrent: in single player the client thread can read this too (interaction events). */
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RemoteLink() {
    }

    public static boolean isActive(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** The body the player left behind, while they're remote-piloting (else null). */
    public static RemoteBodyEntity bodyOf(Player player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.body.isRemoved() || session.body.level() != player.level() ? null : session.body;
    }

    public static boolean isBodyOf(RemoteBodyEntity body, UUID ownerId) {
        Session session = ownerId == null ? null : SESSIONS.get(ownerId);
        return session != null && session.body == body;
    }

    // ---------------------------------------------------------------- connect

    /** Whether the player can open a link right now (tells them why not). */
    public static boolean canConnect(ServerPlayer player) {
        String refusal = null;
        if (isActive(player)) {
            refusal = "message.flightsuit.remote_busy";
        } else if (SuitUpManager.isSuitingUp(player) || !player.isAlive() || player.isSpectator()) {
            return false;
        } else if (WornSuit.of(player).any()) {
            refusal = "message.flightsuit.remote_take_off_suit";
        } else if (player.isPassenger()) {
            refusal = "message.flightsuit.remote_dismount";
        }
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal), true);
            return false;
        }
        return true;
    }

    /** Only a complete suit with some charge in its reactor can be flown remotely. */
    public static boolean canPilot(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts) {
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (!(parts.getOrDefault(slot, ItemStack.EMPTY).getItem() instanceof SuitArmorItem)) {
                player.displayClientMessage(Component.translatable("message.flightsuit.remote_full_set"), true);
                return false;
            }
        }
        if (SuitEnergy.get(parts.get(EquipmentSlot.CHEST)) <= 0) {
            player.displayClientMessage(Component.translatable("message.flightsuit.remote_no_charge"), true);
            return false;
        }
        return true;
    }

    /**
     * Opens the link: the body stays here, the player moves into the suit at {@code spot}.
     * @param airborne the suit was in the air (a hovering companion) - keep it flying instead of dropping
     */
    public static void start(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts, Vec3 spot, float yaw, boolean airborne) {
        ServerLevel level = player.serverLevel();
        Vec3 bodyPos = player.position();
        RemoteBodyEntity body = RemoteBodyEntity.spawn(player);
        Session session = new Session(body, bodyPos, player.getYRot(), player.getXRot());
        SESSIONS.put(player.getUUID(), session);
        keepBodyLoaded(level, session);

        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", level.dimension().location().toString());
        tag.putDouble("X", bodyPos.x);
        tag.putDouble("Y", bodyPos.y);
        tag.putDouble("Z", bodyPos.z);
        tag.putFloat("Yaw", session.yaw);
        tag.putFloat("Pitch", session.pitch);
        player.getPersistentData().put(TAG, tag);

        // The body: EDITH lenses flicker as the link opens.
        Vec3 eyes = player.getEyePosition();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eyes.x, eyes.y, eyes.z, 8, 0.15D, 0.05D, 0.15D, 0.05D);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);

        player.teleportTo(level, spot.x, spot.y, spot.z, yaw, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : parts.entrySet()) {
            ItemStack previous = player.getItemBySlot(entry.getKey());
            if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                player.drop(previous, false);
            }
            player.setItemSlot(entry.getKey(), entry.getValue());
        }
        if (airborne) {
            SuitServerEvents.grantFlightNow(player);
        }
        SuitUpManager.eyesOn(level, player);

        String name = ((SuitArmorItem) parts.get(EquipmentSlot.CHEST).getItem()).getSuitType().hudName();
        send(player, true, session, name);
        player.displayClientMessage(Component.translatable("message.flightsuit.remote_connected", name), true);
    }

    // ---------------------------------------------------------------- disconnect

    public static void end(ServerPlayer player, End reason) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 suitPos = player.position();
        float suitYaw = player.getYRot();
        Map<EquipmentSlot, ItemStack> parts = SuitUpManager.stripSuit(player);

        boolean near = suitPos.distanceTo(session.bodyPos) <= STAY_RANGE && suitPos.y > level.getMinBuildHeight();
        boolean broken = parts.values().stream().anyMatch(SuitArmorItem::isBroken);
        boolean stays = near && !broken && reason != End.RECALL && reason != End.BROKEN;
        boolean flyHome = near && !stays;
        if (!parts.isEmpty()) {
            if (stays || flyHome) {
                // Close enough that its chunk keeps ticking: it's a companion again (and maybe flies off home).
                SuitCompanionEntity suit = SuitCompanionEntity.spawn(player, parts, suitPos, suitYaw);
                if (flyHome) {
                    suit.goHome();
                }
            } else {
                // Far off: it lifts away under its own power; it's docked by the time anyone could check.
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, suitPos.x, suitPos.y, suitPos.z, 30, 0.3D, 0.2D, 0.3D, 0.15D);
                level.sendParticles(ParticleTypes.CLOUD, suitPos.x, suitPos.y, suitPos.z, 12, 0.4D, 0.1D, 0.4D, 0.05D);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
        }

        player.teleportTo(level, session.bodyPos.x, session.bodyPos.y, session.bodyPos.z, session.yaw, session.pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        session.body.discard();
        SuitServerEvents.revokeSuitFlightNow(player);
        EdithGlassesItem.reequip(player);
        if (!parts.isEmpty() && !stays && !flyHome) {
            SuitUpManager.storeReturningSuit(player, null, parts);
        }
        player.getPersistentData().remove(TAG);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
        send(player, false, session, "");

        String message = switch (reason) {
            case DISCONNECT -> stays ? "message.flightsuit.remote_disconnected" : "message.flightsuit.remote_disconnected_home";
            case RECALL -> "message.flightsuit.remote_recalled";
            case BROKEN -> "message.flightsuit.remote_broken";
            case NO_POWER -> "message.flightsuit.remote_no_power";
            case BODY_HIT -> "message.flightsuit.remote_body_hit";
            case LOGOUT -> null;
            case LOST -> "message.flightsuit.remote_lost";
        };
        if (message != null) {
            player.displayClientMessage(Component.translatable(message), true);
        }
    }

    /** A crash mid-link left the player in the suit: put the suit away and bring them back to their body. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(TAG)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        player.getPersistentData().remove(TAG);
        Map<EquipmentSlot, ItemStack> parts = SuitUpManager.stripSuit(player);
        if (tag.getString("Dim").equals(player.level().dimension().location().toString())) {
            player.teleportTo(player.serverLevel(), tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                    tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        }
        SuitServerEvents.revokeSuitFlightNow(player);
        EdithGlassesItem.reequip(player);
        if (!parts.isEmpty()) {
            SuitUpManager.storeReturningSuit(player, null, parts);
        }
        player.displayClientMessage(Component.translatable("message.flightsuit.remote_recovered"), true);
    }

    // ---------------------------------------------------------------- ticking

    public static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.body.isRemoved()) {
            end(player, End.LOST);
            return;
        }
        if (player.tickCount % 20 == 0) {
            keepBodyLoaded(player.serverLevel(), session);
        }
        WornSuit worn = WornSuit.of(player);
        if (!worn.fullSet()) {
            // A piece was taken off through the inventory: there's no suit left to fly.
            end(player, End.LOST);
        } else if (SuitEnergy.available(player, EquipmentSlot.CHEST) <= 0) {
            end(player, End.NO_POWER);
        }
    }

    /** Short self-expiring ticket, refreshed while linked: the body's chunks keep ticking, nothing leaks after. */
    private static void keepBodyLoaded(ServerLevel level, Session session) {
        ChunkPos chunk = new ChunkPos(session.body.blockPosition());
        level.getChunkSource().addRegionTicket(BODY_TICKET, chunk, 3, chunk);
    }

    private static void send(ServerPlayer player, boolean active, Session session, String suitName) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RemoteLinkS2CPacket(active, session.bodyPos, suitName));
    }

    // ---------------------------------------------------------------- damage

    /** Something hit the body: the pilot snaps back into it, and takes the hit. */
    public static void onBodyHit(RemoteBodyEntity body, DamageSource source, float amount) {
        if (!(body.getOwner() instanceof ServerPlayer owner) || !isBodyOf(body, owner.getUUID())) {
            body.discard();
            return;
        }
        end(owner, End.BODY_HIT);
        owner.hurt(source, amount);
    }

    /** /kill and the like go through: drop the link first so it lands on the body. The void just costs the suit. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isActive(player)
                || !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
        }
        end(player, End.LOST);
    }

    /**
     * Hits on the remote suit wear its pieces instead of the pilot's health. Runs last, so a parry or the
     * shield has already had its say; the flash and knockback still happen, it's the suit taking them.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isActive(player)) {
            return;
        }
        event.setCanceled(true);
        if (event.getAmount() <= 0.0F || event.getSource().is(DamageTypes.STARVE)) {
            return;
        }
        // Same wear as worn armor; a piece reaching its limit requests the ejection, which ends the link.
        int wear = Math.max(1, (int) (event.getAmount() / 4.0F));
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem) {
                stack.hurtAndBreak(wear, player, wearer -> wearer.broadcastBreakEvent(slot));
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    // ---------------------------------------------------------------- the body can't act from inside the suit

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        refuse(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        refuse(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        refuse(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        refuse(event.getEntity(), event);
    }

    /**
     * Digging is allowed (the suit's own hands - unlike placing, which would need the body's inventory). What the
     * block drops is caught as it spawns, this same tick, and sent to the station storage (RemoteStorage).
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isActive(player) && player.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            BREAK_DROPS.removeIf(capture -> capture.gameTime() != now);
            BREAK_DROPS.add(new BreakCapture(level.dimension(), Vec3.atCenterOf(event.getPos()), now, player.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (BREAK_DROPS.isEmpty() || event.loadedFromDisk() || !(event.getEntity() instanceof ItemEntity item)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        for (BreakCapture capture : BREAK_DROPS) {
            if (capture.gameTime() != now || !capture.dimension().equals(level.dimension())
                    || item.position().distanceToSqr(capture.center()) > 1.5D * 1.5D) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(capture.player());
            if (player == null || !isActive(player)) {
                return;
            }
            ItemStack left = RemoteStorage.deliver(player, item.getItem().copy());
            if (left.isEmpty()) {
                event.setCanceled(true);
            } else {
                item.setItem(left);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            refuse(player, event);
        }
    }

    /** What the suit walks over goes to the station storage, never into the body's inventory; no storage = stays put. */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!isActive(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        ItemEntity item = event.getItem();
        if (event.getEntity() instanceof ServerPlayer player && item.isAlive() && !item.getItem().isEmpty()) {
            ItemStack left = RemoteStorage.deliver(player, item.getItem().copy());
            if (left.isEmpty()) {
                item.discard();
            } else {
                item.setItem(left);
            }
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Player player && isActive(player)) {
            event.setCanceled(true);
        }
    }

    private static void refuse(Player player, Event event) {
        if (player == null || !isActive(player)) {
            return;
        }
        event.setCanceled(true);
        // Either side: in single player the client already cancels it (shared state), so the server never hears of it.
        player.displayClientMessage(Component.translatable("message.flightsuit.remote_blocked"), true);
    }
}
