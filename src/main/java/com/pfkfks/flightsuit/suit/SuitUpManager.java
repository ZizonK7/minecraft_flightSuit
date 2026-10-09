package com.pfkfks.flightsuit.suit;

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
 * Server-side ground suit-up (DESIGN.md 4-4, Mark 42 style): the player throws their arms out and the
 * pieces fly in one after another - boots, leggings, chestplate, then the helmet closes last.
 *
 * Pieces are taken out of the capsule up front and held here until each one lands, so an interrupted
 * sequence (death, logout, dimension change) just equips whatever is still in flight - nothing is lost.
 */
public final class SuitUpManager {
    /** Order and launch tick of each piece. Helmet last so the faceplate closing is the finale. */
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int[] LAUNCH_TICK = {2, 7, 12, 18};
    private static final int FLIGHT_TICKS = 10;
    private static final int FINALE_TICKS = 8;

    private static final Map<UUID, Sequence> ACTIVE = new HashMap<>();

    private SuitUpManager() {
    }

    private static final class Step {
        final EquipmentSlot slot;
        final ItemStack stack;
        final int launchTick;
        boolean launched;
        boolean equipped;

        Step(EquipmentSlot slot, ItemStack stack, int launchTick) {
            this.slot = slot;
            this.stack = stack;
            this.launchTick = launchTick;
        }
    }

    private static final class Sequence {
        final List<Step> steps = new ArrayList<>();
        final int endTick;
        int age;

        Sequence(List<Step> steps) {
            this.steps.addAll(steps);
            int lastLand = 0;
            for (Step step : steps) {
                lastLand = Math.max(lastLand, step.launchTick + FLIGHT_TICKS);
            }
            this.endTick = lastLand + FINALE_TICKS;
        }
    }

    public static boolean isSuitingUp(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** G key: pack the worn suit away, or suit up from the first filled capsule. */
    public static void toggle(ServerPlayer player) {
        if (isSuitingUp(player)) {
            return;
        }
        if (WornSuit.of(player).any()) {
            pack(player);
            return;
        }
        ItemStack capsule = findFilledCapsule(player);
        if (capsule.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_suit"), true);
            return;
        }
        startFromCapsule(player, capsule);
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

        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < ORDER.length; i++) {
            ItemStack stack = parts.get(ORDER[i]);
            if (stack != null) {
                steps.add(new Step(ORDER[i], stack, LAUNCH_TICK[i]));
            }
        }
        Sequence sequence = new Sequence(steps);
        ACTIVE.put(player.getUUID(), sequence);

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SUIT_UP_GROUND, sequence.endTick));
    }

    /** Called every server tick for every player. */
    public static void tick(ServerPlayer player) {
        Sequence sequence = ACTIVE.get(player.getUUID());
        if (sequence == null) {
            return;
        }
        sequence.age++;
        ServerLevel level = player.serverLevel();
        for (Step step : sequence.steps) {
            if (!step.launched && sequence.age >= step.launchTick) {
                step.launched = true;
                launch(level, player, step);
            }
            if (!step.equipped && sequence.age >= step.launchTick + FLIGHT_TICKS) {
                equip(player, step, true);
            }
        }
        if (sequence.age >= sequence.endTick) {
            ACTIVE.remove(player.getUUID());
            finale(level, player);
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
        RandomSource random = player.getRandom();
        // Launch from behind/beside the player so the pieces swing into view around them.
        double yaw = Math.toRadians(player.yBodyRot + 180.0F + (random.nextFloat() - 0.5F) * 200.0F);
        double distance = 6.0D + random.nextDouble() * 3.0D;
        double height = step.slot == EquipmentSlot.FEET ? 0.5D + random.nextDouble() : 1.5D + random.nextDouble() * 3.0D;
        Vec3 offset = new Vec3(-Math.sin(yaw) * distance, height, Math.cos(yaw) * distance);
        String suitId = step.stack.getItem() instanceof SuitArmorItem armor ? armor.getSuitType().id() : "";
        level.addFreshEntity(SuitPartEntity.create(level, player, step.slot, suitId, offset, FLIGHT_TICKS));
        Vec3 from = player.position().add(offset);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.9F);
    }

    private static void equip(ServerPlayer player, Step step, boolean effects) {
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

    private static void finale(ServerLevel level, ServerPlayer player) {
        // Faceplate shut + eyes light up.
        Vec3 eye = player.getEyePosition().add(player.getLookAngle().scale(0.35D));
        level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 6, 0.12D, 0.04D, 0.12D, 0.01D);
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(Component.translatable("message.flightsuit.suit_online"), true);
    }

    /** Puts every worn suit piece back into a capsule of its suit type (reusing an empty slot, or a new capsule). */
    public static void pack(ServerPlayer player) {
        Map<SuitType, Map<EquipmentSlot, ItemStack>> bySuit = new EnumMap<>(SuitType.class);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.getItem() instanceof SuitArmorItem armor) {
                bySuit.computeIfAbsent(armor.getSuitType(), k -> new EnumMap<>(EquipmentSlot.class)).put(slot, worn.copy());
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
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
