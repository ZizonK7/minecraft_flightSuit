package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.OreSightS2CPacket;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M18 moves of the two Mark suits (DESIGN.md 5차 피드백 역할표):
 * <ul>
 *   <li>Mark 1, the all-rounder: the full burst (V) - a repulsor shot at every foe around in quick turn, then the
 *       whole missile salvo - and the shoulder guns (B) that pick off whatever's ahead for a few seconds (the EDITH
 *       scan went after the first test: Mark 1 needn't mine, and it doubled Mark 2's x-ray);</li>
 *   <li>Mark 2, the explorer: the mining laser (X) that cuts a 3x3 tunnel ahead and pockets the drops, x-ray (V)
 *       that shows ores and monsters through the rock for a while, and the emergency escape (B, EscapeFlight: a
 *       flash that blinds whatever has eyes on it, then the flight home - it replaced the thrown flashbang, which
 *       had replaced the light orb).</li>
 * </ul>
 * Ores are drawn on the client (client/OreSightClient) from the positions the server scans once.
 */
public final class MarkArts {
    private MarkArts() {
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (suitClass == SuitClass.STANDARD) {
            tickBurst(player, state);
            tickGuns(player, state);
        } else {
            state.burst.clear();
            state.gunTicks = 0;
        }
        if (suitClass != SuitClass.STEALTH) {
            state.xrayUntil = 0L;
            state.flashTick = 0L;
        } else {
            tickFlashbang(player, state);
        }
    }

    // ---------------------------------------------------------------- Mark 1: full burst (V)

    static void fullBurst(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady || !state.burst.isEmpty()) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        Vec3 eye = player.getEyePosition();
        List<LivingEntity> foes = new ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SuitTuning.FULL_BURST_RANGE),
                entity -> entity.isAlive() && SuitWeapons.isHostile(player, entity) && !SuitWeapons.isFriendly(player, entity)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)
                        && entity.distanceToSqr(eye) <= SuitTuning.FULL_BURST_RANGE * SuitTuning.FULL_BURST_RANGE && player.hasLineOfSight(entity)));
        if (foes.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FULL_BURST_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        foes.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)));
        state.ultReady = now + SuitTuning.FULL_BURST_COOLDOWN_TICKS;
        state.burst.clear();
        for (int i = 0; i < Math.min(SuitTuning.FULL_BURST_TARGETS, foes.size() * 2); i++) {
            // Few foes: each takes two shots.
            state.burst.add(foes.get(i % foes.size()));
        }
        state.burstTicks = 0;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        KiFx.burst(level, player.position().add(0.0D, 1.0D, 0.0D), KiFx.UNIBEAM, 1.6F, 10, KiFx.RING);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable("message.flightsuit.full_burst", foes.size()), true);
    }

    private static void tickBurst(ServerPlayer player, SuitWeapons.State state) {
        if (state.burst.isEmpty()) {
            return;
        }
        if (++state.burstTicks % SuitTuning.FULL_BURST_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = state.burst.poll();
        boolean last = state.burst.isEmpty();
        if (target != null && target.isAlive() && target.level() == level) {
            // Right palm, left palm, right palm...
            int side = state.burstTicks / SuitTuning.FULL_BURST_INTERVAL % 2 == 0 ? 1 : -1;
            Vec3 look = player.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
            Vec3 palm = player.getEyePosition().add(look.scale(0.4D)).add(right.scale(0.35D * side)).add(0.0D, -0.3D, 0.0D);
            Vec3 dir = target.getBoundingBox().getCenter().subtract(palm).normalize();
            RepulsorHandler.blast(level, player, palm, dir, SuitTuning.FULL_BURST_RANGE * 1.5D, SuitTuning.FULL_BURST_DAMAGE,
                    entity -> entity instanceof LivingEntity && !SuitWeapons.isFriendly(player, entity));
            player.swing(side > 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
        }
        if (last) {
            // The pods empty themselves to finish.
            SuitWeapons.fireMissiles(player, state, true);
        }
    }

    // ---------------------------------------------------------------- Mark 1: shoulder guns (B)

    /** Three seconds of the shoulder pods picking off whatever's ahead - a shot at the nearest foe in the cone every few ticks. */
    static void shoulderGuns(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill3Ready || state.gunTicks > 0) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SHOULDER_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.SHOULDER_COOLDOWN_TICKS;
        state.gunTicks = SuitTuning.SHOULDER_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.shoulder_guns"), true);
    }

    private static void tickGuns(ServerPlayer player, SuitWeapons.State state) {
        if (state.gunTicks <= 0) {
            return;
        }
        state.gunTicks--;
        if (state.gunTicks % SuitTuning.SHOULDER_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(SuitTuning.SHOULDER_ARC_DEG / 2.0D));
        LivingEntity aimed = SuitWeapons.aim(player, SuitTuning.SHOULDER_RANGE).target();
        LivingEntity target = aimed != null && (SuitWeapons.isHostile(player, aimed) || aimed.getLastHurtByMob() == player) ? aimed : null;
        if (target == null) {
            double best = Double.MAX_VALUE;
            for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SuitTuning.SHOULDER_RANGE),
                    entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity) && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player)
                            && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity))) {
                Vec3 to = foe.getBoundingBox().getCenter().subtract(eye);
                double d = to.length();
                if (d > SuitTuning.SHOULDER_RANGE || to.normalize().dot(look) < cos || !player.hasLineOfSight(foe) || d >= best) {
                    continue;
                }
                best = d;
                target = foe;
            }
        }
        if (target == null) {
            return;
        }
        // Left pod, right pod, left pod... just above the shoulders.
        int side = (state.gunTicks / SuitTuning.SHOULDER_INTERVAL) % 2 == 0 ? 1 : -1;
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 pod = eye.add(right.scale(0.45D * side)).add(0.0D, 0.15D, 0.0D).add(look.scale(0.2D));
        Vec3 dir = target.getBoundingBox().getCenter().subtract(pod).normalize();
        // A round, not a ki ball (M18 feedback): hitscan, drawn as a thin hot tracer from the pod to where it struck.
        Vec3 far = pod.add(dir.scale(SuitTuning.SHOULDER_RANGE + 4.0D));
        BlockHitResult block = level.clip(new ClipContext(pod, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, pod, end,
                new AABB(pod, end).inflate(0.4D), entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator()
                        && entity.isPickable() && !SuitWeapons.isFriendly(player, entity));
        if (hit != null) {
            end = hit.getLocation();
            LivingEntity victim = (LivingEntity) hit.getEntity();
            victim.invulnerableTime = 0;
            victim.hurt(player.damageSources().playerAttack(player), SuitTuning.SHOULDER_DAMAGE);
            KiFx.burst(level, end, 0xFFE9A0, 0.5F, 4, KiFx.SPARK);
        }
        KiFx.beam(level, pod, end, 0xFFE9A0, 0.06F, 4, KiFx.BEAM_THIN, null, KiFx.AT_POS, null);
        KiFx.burst(level, pod, 0xFFE9A0, 0.35F, 3, KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.45F, 1.7F + side * 0.1F);
    }

    /** Monsters within {@code radius} glow for {@code ticks}; how many. */
    private static int reveal(ServerPlayer player, double radius, int ticks) {
        int count = 0;
        for (LivingEntity mob : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && SuitWeapons.isHostile(player, entity) && !SuitWeapons.isFriendly(player, entity)
                        && entity.distanceToSqr(player) <= radius * radius)) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false));
            count++;
        }
        return count;
    }

    /** Every ore within {@code radius} blocks goes to the player's client to draw for {@code ticks}; how many. */
    private static int sendOres(ServerPlayer player, int radius, int ticks) {
        ServerLevel level = player.serverLevel();
        BlockPos centre = player.blockPosition();
        List<BlockPos> ores = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), centre.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + radius);
        for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
            for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Tags.Blocks.ORES)) {
                        ores.add(pos.immutable());
                    }
                }
            }
        }
        ores.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
        if (ores.size() > OreSightS2CPacket.MAX) {
            ores = new ArrayList<>(ores.subList(0, OreSightS2CPacket.MAX));
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OreSightS2CPacket(ores, ticks));
        return ores.size();
    }

    // ---------------------------------------------------------------- Mark 2: mining laser (X)

    static void drill(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // The tunnel's cross-section: the two axes square to the look.
        Vec3 u = Math.abs(look.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 v = look.cross(u).normalize();
        Set<BlockPos> cut = new LinkedHashSet<>();
        for (double along = 1.0D; along <= SuitTuning.DRILL_LENGTH + 0.5D; along += 0.5D) {
            Vec3 centre = eye.add(look.scale(along));
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    cut.add(BlockPos.containing(centre.add(u.scale(a)).add(v.scale(b))));
                }
            }
        }
        int broken = 0;
        int energy = SuitEnergy.available(player, EquipmentSlot.CHEST);
        for (BlockPos pos : cut) {
            BlockState block = level.getBlockState(pos);
            float hardness = block.getDestroySpeed(level, pos);
            if (block.isAir() || hardness < 0.0F || hardness > SuitTuning.DRILL_MAX_HARDNESS || level.getBlockEntity(pos) != null
                    || !block.getFluidState().isEmpty() || !level.mayInteract(player, pos)) {
                continue;
            }
            if (energy < SuitTuning.DRILL_COST_PER_BLOCK && !player.getAbilities().instabuild) {
                player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
                break;
            }
            if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.DRILL_COST_PER_BLOCK)) {
                break;
            }
            energy -= SuitTuning.DRILL_COST_PER_BLOCK;
            for (ItemStack drop : Block.getDrops(block, level, pos, null, player, ItemStack.EMPTY)) {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    6, 0.3D, 0.3D, 0.3D, 0.05D);
            level.removeBlock(pos, false);
            broken++;
        }
        state.skill1Ready = now + SuitTuning.DRILL_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        Vec3 from = eye.add(look.scale(0.5D)).add(0.0D, -0.2D, 0.0D);
        Vec3 to = eye.add(look.scale(SuitTuning.DRILL_LENGTH + 1.0D));
        KiFx.beam(level, from, to, KiFx.KAME, 0.9F, 10, KiFx.BEAM_SPIRAL, null, KiFx.AT_POS, null);
        KiFx.burst(level, to, KiFx.KAME, 1.0F, 8, KiFx.SPARK);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 2.0F);
        level.playSound(null, player.blockPosition(), broken > 0 ? SoundEvents.STONE_BREAK : SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                1.0F, 0.6F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
        if (broken == 0) {
            player.displayClientMessage(Component.translatable("message.flightsuit.drill_nothing"), true);
        }
    }

    // ---------------------------------------------------------------- Mark 2: x-ray (V)

    static void xray(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.XRAY_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.XRAY_COOLDOWN_TICKS;
        state.xrayUntil = now + SuitTuning.XRAY_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        int mobs = reveal(player, SuitTuning.XRAY_RADIUS, SuitTuning.XRAY_TICKS);
        int ores = sendOres(player, SuitTuning.XRAY_RADIUS, SuitTuning.XRAY_TICKS);
        KiFx.burst(level, player.getEyePosition(), KiFx.KI, 2.0F, 16, KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2.0F);
        player.displayClientMessage(Component.translatable("message.flightsuit.xray", mobs, ores), true);
    }

    // ---------------------------------------------------------------- Mark 2: the escape's flash (B)

    /**
     * A burst of white at {@code at} that blinds everything with eyes on it, stops it in its tracks for a moment and
     * makes it forget you - what the emergency escape (EscapeFlight) leaves behind as it takes off. Until 2026-10-12
     * B threw it as a flashbang of its own.
     */
    static void flash(ServerPlayer player, SuitWeapons.State state, Vec3 at) {
        state.flashAt = at;
        state.flashTick = player.level().getGameTime();
    }

    /** The flash going off. */
    private static void tickFlashbang(ServerPlayer player, SuitWeapons.State state) {
        if (state.flashTick == 0L || player.level().getGameTime() < state.flashTick) {
            return;
        }
        state.flashTick = 0L;
        ServerLevel level = player.serverLevel();
        Vec3 at = state.flashAt;
        KiFx.burst(level, at, KiFx.WHITE, (float) SuitTuning.FLASHBANG_RADIUS * 0.6F, 12, KiFx.FLASH);
        KiFx.burst(level, at, KiFx.WHITE, (float) SuitTuning.FLASHBANG_RADIUS, 8, KiFx.RING);
        KiFx.flare(level, player, at, (float) SuitTuning.FLASHBANG_RADIUS);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 2.0F);
        int blinded = 0;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SuitTuning.FLASHBANG_RADIUS),
                entity -> entity.isAlive() && entity != player && !SuitWeapons.isFriendly(player, entity)
                        && entity.getBoundingBox().getCenter().distanceTo(at) <= SuitTuning.FLASHBANG_RADIUS)) {
            // Only eyes on the flash: a wall between is a wall.
            if (level.clip(new ClipContext(at, mob.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType()
                    != HitResult.Type.MISS) {
                continue;
            }
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SuitTuning.FLASHBANG_BLIND_TICKS, 0, false, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SuitTuning.FLASHBANG_BLIND_TICKS, 1, false, false, true));
            if (mob instanceof net.minecraft.world.entity.Mob m) {
                m.setTarget(null);
                m.setLastHurtByMob(null);
                m.getNavigation().stop();
            }
            HulkbusterArts.stun(level, mob, SuitTuning.FLASHBANG_STUN_TICKS);
            blinded++;
        }
        if (blinded > 0) {
            player.displayClientMessage(Component.translatable("message.flightsuit.flashbang", blinded), true);
        }
    }
}
