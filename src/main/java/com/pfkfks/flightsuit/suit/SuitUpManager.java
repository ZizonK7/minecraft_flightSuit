package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side suit-up / suit-off choreography (DESIGN.md 4-4).
 *
 * Suit-up modes, picked from the player's situation when the suit arrives:
 * - GROUND (Mark 42): arms thrown out, pieces fly in one by one - from wherever the suit comes from
 *   (main station direction, the station itself, or around the player for a capsule);
 * - FALL (Avengers Mark VII): the player is falling, so they spread-eagle and the whole suit dives onto
 *   their back, then the repulsors brake them into a hover.
 *
 * Pieces leave their storage up front and are held here until they land, so an interrupted sequence
 * (death, logout, dimension change) just equips whatever is still in flight - nothing is lost.
 */
public final class SuitUpManager {
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int[] LAUNCH_TICK = {2, 7, 12, 18};
    private static final int FINALE_TICKS = 8;

    private static final int FALL_FLIGHT_TICKS = 12;
    private static final int FALL_FINALE_TICKS = 8;
    private static final int RETURN_FLIGHT_TICKS = 16;

    private static final int[] STATION_LAUNCH_TICK = {0, 5, 10, 15};
    private static final int STATION_PRESS_TICKS = 7;

    private enum Mode {
        GROUND,
        FALL
    }

    private static final Map<UUID, Sequence> ACTIVE = new HashMap<>();

    private SuitUpManager() {
    }

    private static final class Step {
        final EquipmentSlot slot;
        final ItemStack stack;
        final int launchTick;
        final int flightTicks;
        final Vec3 offset;
        /** Pressed on in place by the station rig instead of flying in. */
        final boolean clamp;
        boolean launched;
        boolean equipped;

        Step(EquipmentSlot slot, ItemStack stack, int launchTick, int flightTicks, Vec3 offset) {
            this(slot, stack, launchTick, flightTicks, offset, false);
        }

        Step(EquipmentSlot slot, ItemStack stack, int launchTick, int flightTicks, Vec3 offset, boolean clamp) {
            this.slot = slot;
            this.stack = stack;
            this.launchTick = launchTick;
            this.flightTicks = flightTicks;
            this.offset = offset;
            this.clamp = clamp;
        }

        int landTick() {
            return launchTick + flightTicks;
        }
    }

    private static final class Sequence {
        final Mode mode;
        final List<Step> steps = new ArrayList<>();
        final int endTick;
        int age;

        Sequence(Mode mode, List<Step> steps, int finaleTicks) {
            this.mode = mode;
            this.steps.addAll(steps);
            int lastLand = 0;
            for (Step step : steps) {
                lastLand = Math.max(lastLand, step.landTick());
            }
            this.endTick = lastLand + finaleTicks;
        }

        boolean allEquipped() {
            return steps.stream().allMatch(step -> step.equipped);
        }
    }

    public static boolean isSuitingUp(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- entry points

    /**
     * G key. Wearing suit pieces: send them home (EDITH + main station) or pack them into a capsule.
     * Not wearing any: call the main suit (EDITH + main station) or suit up from a filled capsule.
     */
    public static void toggle(ServerPlayer player) {
        if (isSuitingUp(player)) {
            return;
        }
        boolean edith = EdithGlassesItem.has(player);
        if (WornSuit.of(player).any()) {
            if (edith) {
                SuitStationBlockEntity station = MainStation.resolve(player);
                if (station != null && station.canDock(wornPieces(player))) {
                    sendHome(player, station);
                    return;
                }
            }
            pack(player);
            return;
        }
        if (edith) {
            SuitStationBlockEntity station = MainStation.resolve(player);
            if (station != null && station.hasSuit()) {
                callFromStation(player, station);
                return;
            }
        }
        ItemStack capsule = findFilledCapsule(player);
        if (!capsule.isEmpty()) {
            startFromCapsule(player, capsule);
            return;
        }
        player.displayClientMessage(Component.translatable(edith ? noStationMessage(player) : "message.flightsuit.no_suit"), true);
    }

    private static String noStationMessage(ServerPlayer player) {
        MainStation.Link link = MainStation.get(player);
        if (link == null) {
            return "message.flightsuit.no_main_station";
        }
        if (!MainStation.isInPlayerDimension(player, link)) {
            return "message.flightsuit.station_other_dimension";
        }
        return "message.flightsuit.station_empty";
    }

    public static void startFromCapsule(ServerPlayer player, ItemStack capsule) {
        if (isSuitingUp(player)) {
            return;
        }
        if (WornSuit.of(player).any()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.already_wearing"), true);
            return;
        }
        Map<EquipmentSlot, ItemStack> parts = SuitCapsuleItem.getParts(capsule);
        if (parts.isEmpty()) {
            return;
        }
        SuitCapsuleItem.clearParts(capsule);
        begin(player, parts, null, 6.0D, 9.0D, 10);
    }

    /** EDITH call: the main suit leaves its station and flies to the player from that direction. */
    public static void callFromStation(ServerPlayer player, SuitStationBlockEntity station) {
        Map<EquipmentSlot, ItemStack> parts = station.takeAll();
        Vec3 toStation = station.dockPoint().subtract(player.position());
        player.displayClientMessage(Component.translatable("message.flightsuit.suit_incoming",
                (int) toStation.length()), true);
        begin(player, parts, toStation, 12.0D, 16.0D, 14);
    }

    /**
     * Right-click the station empty-handed with a suit docked (Iron Man 2 / Avengers catwalk): you step onto
     * the platform into the waiting suit and it is assembled around you in place - boots rise from the
     * platform, legs and chest press on from behind, the helmet lowers last. No flying pieces.
     */
    public static boolean suitUpAtStation(ServerPlayer player, SuitStationBlockEntity station) {
        if (isSuitingUp(player) || WornSuit.of(player).any()) {
            return false;
        }
        Map<EquipmentSlot, ItemStack> parts = station.takeAll();
        if (parts.isEmpty()) {
            return false;
        }
        // Step into the suit: stand where it stood, facing the way it faced.
        float yaw = station.getBlockState().getValue(SuitStationBlock.FACING).toYRot();
        Vec3 spot = Vec3.atBottomCenterOf(station.getBlockPos()).add(0.0D, 0.25D, 0.0D);
        player.connection.teleport(spot.x, spot.y, spot.z, yaw, 0.0F);
        player.setYBodyRot(yaw);
        player.setYHeadRot(yaw);

        Vec3 back = Vec3.directionFromRotation(0.0F, yaw).scale(-1.0D);
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < ORDER.length; i++) {
            ItemStack stack = parts.get(ORDER[i]);
            if (stack == null) {
                continue;
            }
            Vec3 offset = switch (ORDER[i]) {
                case FEET -> new Vec3(0.0D, -0.6D, 0.0D);
                case LEGS -> back.scale(0.7D).add(0.0D, -0.2D, 0.0D);
                case CHEST -> back.scale(0.8D).add(0.0D, 0.15D, 0.0D);
                default -> back.scale(0.3D).add(0.0D, 0.9D, 0.0D);
            };
            steps.add(new Step(ORDER[i], stack, STATION_LAUNCH_TICK[i], STATION_PRESS_TICKS, offset, true));
        }
        startGround(player, steps, SuitAnim.SUIT_UP_STATION);
        return true;
    }

    /** Right-click the station empty-handed while wearing suit pieces: they come off and dock. */
    public static boolean dockAtStation(ServerPlayer player, SuitStationBlockEntity station) {
        if (isSuitingUp(player) || !WornSuit.of(player).any() || !station.canDock(wornPieces(player))) {
            return false;
        }
        sendHome(player, station);
        return true;
    }

    // ---------------------------------------------------------------- suit-up

    /**
     * @param fromDirection where the suit comes from (null = all around the player)
     */
    private static void begin(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts, Vec3 fromDirection,
                              double minDistance, double maxDistance, int flightTicks) {
        if (isFalling(player)) {
            startFall(player, parts);
            return;
        }
        RandomSource random = player.getRandom();
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < ORDER.length; i++) {
            ItemStack stack = parts.get(ORDER[i]);
            if (stack == null) {
                continue;
            }
            double yaw;
            if (fromDirection != null && fromDirection.horizontalDistanceSqr() > 1.0D) {
                // Spread around the station's bearing so the pieces arrive as a loose formation.
                yaw = Math.atan2(-fromDirection.x, fromDirection.z) + (random.nextDouble() - 0.5D) * Math.toRadians(70.0D);
            } else {
                // Behind/beside the player so the pieces swing into view around them.
                yaw = Math.toRadians(player.yBodyRot + 180.0F + (random.nextFloat() - 0.5F) * 200.0F);
            }
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            double height = ORDER[i] == EquipmentSlot.FEET ? 0.5D + random.nextDouble() : 1.5D + random.nextDouble() * 3.0D;
            Vec3 offset = new Vec3(-Math.sin(yaw) * distance, height, Math.cos(yaw) * distance);
            steps.add(new Step(ORDER[i], stack, LAUNCH_TICK[i], flightTicks, offset));
        }
        startGround(player, steps, SuitAnim.SUIT_UP_GROUND);
    }

    private static boolean isFalling(ServerPlayer player) {
        return !player.onGround() && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isInWater() && !player.isPassenger()
                && (player.getDeltaMovement().y < -0.35D || player.fallDistance > 2.5F);
    }

    private static void startGround(ServerPlayer player, List<Step> steps, SuitAnim pose) {
        Sequence sequence = new Sequence(Mode.GROUND, steps, FINALE_TICKS);
        ACTIVE.put(player.getUUID(), sequence);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, pose, sequence.endTick));
    }

    private static void startFall(ServerPlayer player, Map<EquipmentSlot, ItemStack> parts) {
        // The whole suit dives in from above and behind, along the player's back.
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 offset = look.scale(-3.0D).add(0.0D, 7.0D, 0.0D);
        List<Step> steps = new ArrayList<>();
        for (EquipmentSlot slot : ORDER) {
            ItemStack stack = parts.get(slot);
            if (stack != null) {
                steps.add(new Step(slot, stack, 0, FALL_FLIGHT_TICKS, offset));
            }
        }
        Sequence sequence = new Sequence(Mode.FALL, steps, FALL_FINALE_TICKS);
        ACTIVE.put(player.getUUID(), sequence);

        ServerLevel level = player.serverLevel();
        String suitId = steps.isEmpty() || !(steps.get(0).stack.getItem() instanceof SuitArmorItem armor) ? "" : armor.getSuitType().id();
        level.addFreshEntity(SuitPartEntity.createWhole(level, player, suitId, offset, FALL_FLIGHT_TICKS));
        for (Step step : steps) {
            step.launched = true;
        }
        Vec3 from = player.position().add(offset);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SUIT_UP_FALL, sequence.endTick));
    }

    /** Called every server tick for every player. */
    public static void tick(ServerPlayer player) {
        Sequence sequence = ACTIVE.get(player.getUUID());
        if (sequence == null) {
            return;
        }
        sequence.age++;
        ServerLevel level = player.serverLevel();

        if (sequence.mode == Mode.FALL) {
            tickFall(level, player, sequence);
            return;
        }
        for (Step step : sequence.steps) {
            if (!step.launched && sequence.age >= step.launchTick) {
                step.launched = true;
                launch(level, player, step);
            }
            if (!step.equipped && sequence.age >= step.landTick()) {
                equip(player, step, true);
            }
        }
        if (sequence.age >= sequence.endTick) {
            ACTIVE.remove(player.getUUID());
            groundFinale(level, player);
        }
    }

    private static void tickFall(ServerLevel level, ServerPlayer player, Sequence sequence) {
        boolean landedEarly = !sequence.allEquipped() && sequence.age > 2 && player.onGround();
        if (!sequence.allEquipped() && (sequence.age >= FALL_FLIGHT_TICKS || landedEarly)) {
            for (Step step : sequence.steps) {
                equip(player, step, false);
            }
            Vec3 body = player.position().add(0.0D, 1.0D, 0.0D);
            level.sendParticles(ParticleTypes.FLASH, body.x, body.y, body.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, body.x, body.y, body.z, 30, 0.4D, 0.7D, 0.4D, 0.25D);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.2F, 0.7F);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (landedEarly) {
                // Hit the ground mid-assembly: superhero landing instead of a brake.
                level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.4F);
            } else {
                // Repulsor brake: kill the fall and hand over to hover flight.
                SuitServerEvents.grantFlightNow(player);
                player.setDeltaMovement(0.0D, 0.15D, 0.0D);
                player.hurtMarked = true;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(), player.getZ(), 40, 0.3D, 0.1D, 0.3D, 0.15D);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.3D, player.getZ(), 20, 0.6D, 0.1D, 0.6D, 0.1D);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
        if (sequence.age >= sequence.endTick) {
            ACTIVE.remove(player.getUUID());
            eyesOn(level, player);
        }
    }

    /** Ends a sequence immediately, equipping everything still in flight. */
    public static void finishNow(ServerPlayer player) {
        Sequence sequence = ACTIVE.remove(player.getUUID());
        if (sequence == null) {
            return;
        }
        for (Step step : sequence.steps) {
            if (!step.equipped) {
                equip(player, step, false);
            }
        }
    }

    private static void launch(ServerLevel level, ServerPlayer player, Step step) {
        String suitId = step.stack.getItem() instanceof SuitArmorItem armor ? armor.getSuitType().id() : "";
        Vec3 from = player.position().add(step.offset);
        if (step.clamp) {
            level.addFreshEntity(SuitPartEntity.createClamp(level, player, step.slot, suitId, step.offset, step.flightTicks));
            level.playSound(null, from.x, from.y, from.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.5F, 1.4F);
            return;
        }
        level.addFreshEntity(SuitPartEntity.create(level, player, step.slot, suitId, step.offset, step.flightTicks));
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.9F);
    }

    private static void equip(ServerPlayer player, Step step, boolean effects) {
        if (step.equipped) {
            return;
        }
        step.equipped = true;
        ItemStack previous = player.getItemBySlot(step.slot);
        if (!previous.isEmpty() && !player.getInventory().add(previous)) {
            player.drop(previous, false);
        }
        player.setItemSlot(step.slot, step.stack);
        if (!effects) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double y = player.getY() + switch (step.slot) {
            case HEAD -> 1.6D;
            case CHEST -> 1.1D;
            case LEGS -> 0.6D;
            default -> 0.15D;
        };
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), y, player.getZ(), 14, 0.3D, 0.2D, 0.3D, 0.15D);
        level.sendParticles(ParticleTypes.CRIT, player.getX(), y, player.getZ(), 6, 0.25D, 0.15D, 0.25D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    private static void groundFinale(ServerLevel level, ServerPlayer player) {
        eyesOn(level, player);
        player.displayClientMessage(Component.translatable("message.flightsuit.suit_online"), true);
    }

    /** Faceplate shut + eyes light up. */
    private static void eyesOn(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition().add(player.getLookAngle().scale(0.35D));
        level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 6, 0.12D, 0.04D, 0.12D, 0.01D);
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    // ---------------------------------------------------------------- suit-off

    private static Map<EquipmentSlot, ItemStack> wornPieces(ServerPlayer player) {
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem) {
                worn.put(slot, stack);
            }
        }
        return worn;
    }

    private static Map<EquipmentSlot, ItemStack> stripSuit(ServerPlayer player) {
        Map<EquipmentSlot, ItemStack> stripped = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : wornPieces(player).entrySet()) {
            stripped.put(entry.getKey(), entry.getValue().copy());
            player.setItemSlot(entry.getKey(), ItemStack.EMPTY);
        }
        return stripped;
    }

    /** The worn pieces detach and fly off toward the station (EDITH "go home"); glasses go back on. */
    public static void sendHome(ServerPlayer player, SuitStationBlockEntity station) {
        Map<EquipmentSlot, ItemStack> pieces = stripSuit(player);
        station.dock(pieces);
        EdithGlassesItem.reequip(player);

        ServerLevel level = player.serverLevel();
        Vec3 toStation = station.dockPoint().subtract(player.position());
        double distance = toStation.length();
        Vec3 away = distance < 1.0D ? new Vec3(0.0D, 6.0D, 0.0D)
                : toStation.normalize().scale(Math.min(distance, 20.0D)).add(0.0D, distance < 20.0D ? 0.0D : 4.0D, 0.0D);
        int i = 0;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : pieces.entrySet()) {
            String suitId = entry.getValue().getItem() instanceof SuitArmorItem armor ? armor.getSuitType().id() : "";
            Vec3 spread = away.add((i - 1.5D) * 0.6D, 0.0D, 0.0D);
            level.addFreshEntity(SuitPartEntity.createLeaving(level, player, entry.getKey(), suitId, spread, RETURN_FLIGHT_TICKS));
            i++;
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D, player.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
        player.displayClientMessage(Component.translatable("message.flightsuit.suit_returning"), true);
    }

    private static ItemStack findFilledCapsule(ServerPlayer player) {
        for (ItemStack held : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof SuitCapsuleItem && SuitCapsuleItem.hasParts(held)) {
                return held;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SuitCapsuleItem && SuitCapsuleItem.hasParts(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Puts every worn suit piece back into a capsule of its suit type (reusing one with room, or a new capsule). */
    public static void pack(ServerPlayer player) {
        Map<SuitType, Map<EquipmentSlot, ItemStack>> bySuit = new EnumMap<>(SuitType.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : stripSuit(player).entrySet()) {
            SuitType type = ((SuitArmorItem) entry.getValue().getItem()).getSuitType();
            bySuit.computeIfAbsent(type, k -> new EnumMap<>(EquipmentSlot.class)).put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<SuitType, Map<EquipmentSlot, ItemStack>> entry : bySuit.entrySet()) {
            ItemStack capsule = findCapsuleWithRoom(player, entry.getKey(), entry.getValue().keySet());
            boolean fresh = capsule.isEmpty();
            if (fresh) {
                capsule = new ItemStack(ModItems.capsuleFor(entry.getKey()));
            }
            for (Map.Entry<EquipmentSlot, ItemStack> part : entry.getValue().entrySet()) {
                SuitCapsuleItem.setPart(capsule, part.getKey(), part.getValue());
            }
            if (fresh && !player.getInventory().add(capsule)) {
                player.drop(capsule, false);
            }
        }
        EdithGlassesItem.reequip(player);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0D, player.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
        player.displayClientMessage(Component.translatable("message.flightsuit.suit_packed"), true);
    }

    private static ItemStack findCapsuleWithRoom(ServerPlayer player, SuitType type, Iterable<EquipmentSlot> slots) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SuitCapsuleItem capsule && capsule.getSuitType() == type) {
                boolean fits = true;
                for (EquipmentSlot slot : slots) {
                    if (SuitCapsuleItem.hasPart(stack, slot)) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void forget(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
