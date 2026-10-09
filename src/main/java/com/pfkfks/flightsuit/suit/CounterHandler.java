package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.ShieldStateS2CPacket;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Counter key (DESIGN.md 4-6), needs the suit chestplate (it carries the arms):
 * - press: a short parry window. A hit inside it lands at a fifth of its damage and the attacker gets
 *   countered - thrown back, hurt and briefly stunned;
 * - hold: the Endgame nano shield. Blocks everything coming from the front half (melee, arrows,
 *   explosions) while it drains power.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class CounterHandler {
    public static final byte PRESS = 0;
    public static final byte SHIELD_ON = 1;
    public static final byte RELEASE = 2;

    private static final Map<UUID, Long> PARRY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> PARRY_READY_AT = new HashMap<>();
    private static final Set<UUID> SHIELDING = new HashSet<>();

    private CounterHandler() {
    }

    public static void handleInput(ServerPlayer player, byte action) {
        switch (action) {
            case PRESS -> openParry(player);
            case SHIELD_ON -> setShield(player, true);
            default -> setShield(player, false);
        }
    }

    private static boolean canUse(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem && !SuitUpManager.isSuitingUp(player);
    }

    private static void openParry(ServerPlayer player) {
        if (!canUse(player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now < PARRY_READY_AT.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.PARRY_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        PARRY_UNTIL.put(player.getUUID(), now + SuitTuning.PARRY_WINDOW_TICKS);
        PARRY_READY_AT.put(player.getUUID(), now + SuitTuning.PARRY_COOLDOWN_TICKS);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.PARRY, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    public static boolean isShielding(ServerPlayer player) {
        return SHIELDING.contains(player.getUUID());
    }

    private static void setShield(ServerPlayer player, boolean on) {
        boolean was = SHIELDING.contains(player.getUUID());
        if (on && (!canUse(player) || SuitEnergy.available(player, EquipmentSlot.CHEST) < SuitTuning.SHIELD_DRAIN)) {
            on = false;
        }
        if (on == was) {
            return;
        }
        if (on) {
            SHIELDING.add(player.getUUID());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 2.0F);
        } else {
            SHIELDING.remove(player.getUUID());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);
        }
        ModNetwork.sendToTrackingAndSelf(player, new ShieldStateS2CPacket(player.getId(), on));
    }

    /** Per server tick: the shield costs power and drops when the chestplate or the power is gone. */
    public static void tick(ServerPlayer player) {
        if (SHIELDING.contains(player.getUUID())
                && (!canUse(player) || !SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SHIELD_DRAIN))) {
            setShield(player, false);
        }
    }

    /** True if the damage comes from the front half of where the player is looking. */
    private static boolean fromFront(ServerPlayer player, DamageSource source) {
        Vec3 origin = source.getSourcePosition();
        if (origin == null && source.getEntity() != null) {
            origin = source.getEntity().position();
        }
        if (origin == null) {
            return false;
        }
        Vec3 toSource = origin.subtract(player.getEyePosition());
        Vec3 look = player.getLookAngle();
        return toSource.x * look.x + toSource.z * look.z > 0.0D;
    }

    /** Shield: cancels frontal hits outright (arrows then bounce off). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SHIELDING.contains(player.getUUID())) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || !fromFront(player, source)) {
            return;
        }
        event.setCanceled(true);
        SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SHIELD_HIT_COST);
        ServerLevel level = player.serverLevel();
        Vec3 impact = player.getEyePosition().add(player.getLookAngle().scale(0.9D)).add(0.0D, -0.3D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 12, 0.3D, 0.3D, 0.3D, 0.2D);
        level.sendParticles(ParticleTypes.WAX_ON, impact.x, impact.y, impact.z, 6, 0.4D, 0.4D, 0.4D, 0.1D);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && source.getDirectEntity() == attacker && living.distanceTo(player) < 4.0F) {
            Vec3 push = living.position().subtract(player.position());
            living.knockback(0.6D, -push.x, -push.z);
        }
    }

    /** Parry: a hit inside the window mostly bounces off and the attacker eats a counter. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long until = PARRY_UNTIL.get(player.getUUID());
        long now = player.level().getGameTime();
        DamageSource source = event.getSource();
        if (until == null || now > until || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        PARRY_UNTIL.remove(player.getUUID());
        // Reward: the next parry is ready almost at once.
        PARRY_READY_AT.put(player.getUUID(), now + 4);
        event.setAmount(event.getAmount() * SuitTuning.PARRY_DAMAGE_TAKEN);

        Vec3 push = attacker.position().subtract(player.position());
        attacker.hurt(player.damageSources().playerAttack(player), SuitTuning.COUNTER_DAMAGE);
        attacker.knockback(1.6D, -push.x, -push.z);
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SuitTuning.COUNTER_STUN_TICKS, 4));
        attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SuitTuning.COUNTER_STUN_TICKS, 2));

        ServerLevel level = player.serverLevel();
        Vec3 clash = player.getEyePosition().add(player.getLookAngle().scale(0.8D));
        level.sendParticles(ParticleTypes.FLASH, clash.x, clash.y, clash.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, clash.x, clash.y, clash.z, 20, 0.3D, 0.3D, 0.3D, 0.5D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, clash.x, clash.y, clash.z, 16, 0.3D, 0.3D, 0.3D, 0.3D);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.COUNTER, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.parried"), true);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher
                && SHIELDING.contains(target.getUUID())) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new ShieldStateS2CPacket(target.getId(), true));
        }
    }

    public static void forget(UUID id) {
        PARRY_UNTIL.remove(id);
        PARRY_READY_AT.remove(id);
        SHIELDING.remove(id);
    }
}
