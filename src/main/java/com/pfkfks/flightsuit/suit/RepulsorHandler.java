package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Palm repulsor blast: an instant beam. Used by the worn chestplate and by companion suits. */
public final class RepulsorHandler {
    private static final Map<UUID, Long> LAST_FIRE = new HashMap<>();

    private RepulsorHandler() {
    }

    /** Player fire (empty-hand right click with a suit chestplate on). */
    public static void fire(ServerPlayer player) {
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem)) {
            return;
        }
        if (SuitUpManager.isSuitingUp(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_FIRE.get(player.getUUID());
        if (last != null && now - last < SuitTuning.REPULSOR_COOLDOWN_TICKS) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.REPULSOR_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        LAST_FIRE.put(player.getUUID(), now);

        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        // The beam is aimed from the eye (so it goes where the crosshair is) but drawn from the right palm.
        Vec3 palm = player.getEyePosition().add(look.scale(0.8D)).add(right.scale(0.35D)).add(0, -0.3D, 0);
        boolean freeze = ((SuitArmorItem) player.getItemBySlot(EquipmentSlot.CHEST).getItem()).getSuitType().suitClass().hasFreezeBeam();
        Predicate<Entity> canHit = entity -> entity != player
                && !(entity instanceof SuitCompanionEntity companion && companion.isOwnedBy(player));
        if (freeze) {
            Vec3 end = traceEnd(player.serverLevel(), player, player.getEyePosition(), look, SuitTuning.REPULSOR_RANGE,
                    SuitTuning.FREEZE_BEAM_DAMAGE, canHit, RepulsorHandler::freeze);
            freezeEffects(player.serverLevel(), palm, end);
        } else {
            Vec3 end = traceEnd(player.serverLevel(), player, player.getEyePosition(), look, SuitTuning.REPULSOR_RANGE,
                    SuitTuning.REPULSOR_DAMAGE, canHit, null);
            effects(player.serverLevel(), palm, end);
        }
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.REPULSOR_RIGHT, 0));
    }

    /** Stealth suits' cryo beam: freezes the target solid (vanilla powder-snow freeze) and slows it to a crawl. */
    private static void freeze(LivingEntity target) {
        target.setTicksFrozen(target.getTicksRequiredToFreeze() + SuitTuning.FREEZE_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SuitTuning.FREEZE_TICKS, 3));
    }

    private static void freezeEffects(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        for (double d = 0; d < length; d += 0.35D) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, to.x, to.y, to.z, 24, 0.3D, 0.3D, 0.3D, 0.05D);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, to.x, to.y, to.z, 10, 0.2D, 0.2D, 0.2D, 0.1D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    /** Fire from an arbitrary shooter and muzzle point (companion suits). */
    public static void blast(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double range, float damage,
                             Predicate<Entity> canHit) {
        Vec3 end = traceEnd(level, shooter, from, direction, range, damage, canHit, null);
        effects(level, from, end);
    }

    /** Traces the beam, damages (and optionally affects) the first hit entity, and returns where the beam stops. */
    private static Vec3 traceEnd(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double range, float damage,
                                 Predicate<Entity> canHit, java.util.function.Consumer<LivingEntity> onHit) {
        Vec3 end = from.add(direction.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 beamEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = new AABB(from, beamEnd).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, shooter, from, beamEnd, sweep,
                entity -> !entity.isSpectator() && entity.isPickable() && canHit.test(entity));
        if (entityHit == null) {
            return beamEnd;
        }
        Entity target = entityHit.getEntity();
        DamageSource source = shooter instanceof Player player
                ? shooter.damageSources().playerAttack(player)
                : shooter.damageSources().mobAttack(shooter);
        target.hurt(source, damage);
        if (target instanceof LivingEntity living) {
            living.knockback(0.8D, -direction.x, -direction.z);
            if (onHit != null) {
                onHit.accept(living);
            }
        }
        return entityHit.getLocation();
    }

    private static void effects(ServerLevel level, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        Vec3 dir = to.subtract(from).normalize();
        for (double d = 0; d < length; d += 0.4D) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 16, 0.2D, 0.2D, 0.2D, 0.3D);
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 6, 0.15D, 0.15D, 0.15D, 0.02D);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3F, 1.9F);
    }

    public static void forget(UUID playerId) {
        LAST_FIRE.remove(playerId);
    }
}
