package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.EntityFxS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Majin Buu's candy beam, stolen by the phantom (M17): what it hits is shrunk to the size of a dropped item for a
 * while - a third of its hitbox and drawn as small - it can't hurt anyone, and it takes double damage. The time is
 * kept on the mob itself (like Stasis); clients get it through EntityFxS2CPacket (CANDY).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class CandyShrink {
    public static final float SCALE = 0.3F;
    private static final String UNTIL = "flightsuit_candy_until";
    /** Client side: entity ids that are candy-small right now. */
    public static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();

    private CandyShrink() {
    }

    public static boolean isShrunk(Entity entity) {
        if (entity.level().isClientSide) {
            return CLIENT.contains(entity.getId());
        }
        return entity.getPersistentData().contains(UNTIL);
    }

    public static void shrink(ServerLevel level, LivingEntity target, int ticks) {
        boolean was = isShrunk(target);
        target.getPersistentData().putLong(UNTIL, level.getGameTime() + ticks);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        if (!was) {
            target.refreshDimensions();
            ModNetwork.sendToTracking(target, new EntityFxS2CPacket(target.getId(), EntityFxS2CPacket.CANDY, true));
        }
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 0.5D, target.getZ(), 6, 0.4D, 0.4D, 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.3D, target.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
        level.playSound(null, target.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    private static void restore(ServerLevel level, LivingEntity target) {
        target.getPersistentData().remove(UNTIL);
        target.refreshDimensions();
        ModNetwork.sendToTracking(target, new EntityFxS2CPacket(target.getId(), EntityFxS2CPacket.CANDY, false));
        level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.5D, target.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity && isShrunk(entity)) {
            event.setNewSize(event.getNewSize().scale(SCALE), true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(UNTIL)) {
            return;
        }
        if (level.getGameTime() >= data.getLong(UNTIL) || !entity.isAlive()) {
            restore(level, entity);
            return;
        }
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            mob.setTarget(null);
        }
        if (entity.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.2D, entity.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    /** A candy can't hurt anyone. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && !attacker.level().isClientSide && isShrunk(attacker)) {
            event.setCanceled(true);
        }
    }

    /** ...and anything hurts it twice as much. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide && isShrunk(event.getEntity())) {
            event.setAmount(event.getAmount() * 2.0F);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof LivingEntity target && isShrunk(target)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new EntityFxS2CPacket(target.getId(), EntityFxS2CPacket.CANDY, true));
        }
    }
}
