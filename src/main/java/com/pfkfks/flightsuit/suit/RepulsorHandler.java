package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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

/** Palm repulsor blast (chestplate ability): an instant beam along the look direction. */
public final class RepulsorHandler {
    private static final Map<UUID, Long> LAST_FIRE = new HashMap<>();

    private RepulsorHandler() {
    }

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

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(SuitTuning.REPULSOR_RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 beamEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(SuitTuning.REPULSOR_RANGE)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, beamEnd, sweep,
                entity -> !entity.isSpectator() && entity.isPickable() && entity != player);
        if (entityHit != null) {
            beamEnd = entityHit.getLocation();
            Entity target = entityHit.getEntity();
            target.hurt(player.damageSources().playerAttack(player), SuitTuning.REPULSOR_DAMAGE);
            if (target instanceof LivingEntity living) {
                living.knockback(0.8D, -look.x, -look.z);
            }
        }

        // Beam from the right palm (roughly where the raised right arm ends).
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 palm = eye.add(look.scale(0.8D)).add(right.scale(0.35D)).add(0, -0.3D, 0);
        double length = palm.distanceTo(beamEnd);
        Vec3 dir = beamEnd.subtract(palm).normalize();
        for (double d = 0; d < length; d += 0.4D) {
            Vec3 p = palm.add(dir.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, palm.x, palm.y, palm.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, beamEnd.x, beamEnd.y, beamEnd.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, beamEnd.x, beamEnd.y, beamEnd.z, 16, 0.2D, 0.2D, 0.2D, 0.3D);
        level.sendParticles(ParticleTypes.SMOKE, beamEnd.x, beamEnd.y, beamEnd.z, 6, 0.15D, 0.15D, 0.15D, 0.02D);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.8F);
        level.playSound(null, beamEnd.x, beamEnd.y, beamEnd.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.3F, 1.9F);

        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.REPULSOR_RIGHT, 0));
    }

    public static void forget(UUID playerId) {
        LAST_FIRE.remove(playerId);
    }
}
