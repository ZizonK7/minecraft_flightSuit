package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.ShieldStateS2CPacket;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Mark 44, the Hulkbuster (M17): the palm repulsor is gone - it fights with its fists.
 * - flurry (hold right click): left, right, left... every PUNCH_INTERVAL ticks; each punch hits everything in a
 *   fan in front and throws it back; thrown into a wall straight after, it takes another hit ("벽에 박기");
 * - piston punch (X): rushes the aimed foe and pounds it five times, the last one stunning it;
 * - ground slam (C): a hop and both fists into the ground - a shockwave that hurts and throws up everything round;
 *   used in the air it drops straight down first;
 * - heavy plating (passive): hard to push, small projectiles do half, small mobs it walks into are shoved aside,
 *   and it flies at 70% of a Mark 1 (SuitServerEvents).
 * The same fists serve the companion suit (punchFan, slam).
 */
public final class HulkbusterArts {
    static final byte SLAM_NONE = 0;
    static final byte SLAM_RISING = 1;
    static final byte SLAM_FALLING = 2;
    /** Longest a slam may take to find the ground. */
    private static final int SLAM_MAX_TICKS = 80;
    /** Longest the piston punch rushes before it gives up. */
    private static final int PISTON_RUSH_TICKS = 12;

    private HulkbusterArts() {
    }

    static boolean wearsFullSet(ServerPlayer player) {
        WornSuit worn = WornSuit.of(player);
        return worn.fullSet() && WornSuit.primaryType(player).suitClass() == SuitClass.HULKBUSTER;
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        tickWallSlams(player, state);
        tickHeld(player, state, suitClass);
        if (suitClass != SuitClass.HULKBUSTER || !player.isAlive()) {
            state.pistonTarget = null;
            state.pistonHits = 0;
            state.slamPhase = SLAM_NONE;
            if (state.finisherTarget != null) {
                Stasis.release(state.finisherTarget);
                state.finisherTarget = null;
            }
            return;
        }
        tickPiston(player, state);
        tickSlam(player, state);
        tickArmour(player, state);
        tickHammer(player, state);
        tickFinisher(player, state);
        tickGauge(player, state);
        if (player.tickCount % 2 == 0 && wearsFullSet(player)) {
            shoveSmallMobs(player);
        }
    }

    // ---------------------------------------------------------------- armour mode (Z held, M18)

    /** Dug in: rooted, most of what hits it shrugged off, and a shockwave when it lets go. */
    static void setArmourMode(ServerPlayer player, SuitWeapons.State state, boolean on) {
        if (state.armourMode == on) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (on && SuitEnergy.available(player, EquipmentSlot.CHEST) < SuitTuning.ARMOUR_MODE_DRAIN * 10) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.armourMode = on;
        SuitWeapons.sendStatus(player, state);
        if (on) {
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6F, 0.7F);
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_SLAM, 0));
            ModNetwork.sendToTrackingAndSelf(player, new ShieldStateS2CPacket(player.getId(), true));
            player.displayClientMessage(Component.translatable("message.flightsuit.armour_on"), true);
        } else {
            ModNetwork.sendToTrackingAndSelf(player, new ShieldStateS2CPacket(player.getId(), false));
            // Letting go: the plates slam back and the ground jumps.
            int hit = slam(level, player, SuitTuning.ARMOUR_MODE_WAVE_RADIUS, SuitTuning.ARMOUR_MODE_WAVE_DAMAGE,
                    entity -> !SuitWeapons.isFriendly(player, entity) && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player)
                            && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity));
            KiFx.burst(level, player.position().add(0.0D, 0.2D, 0.0D), KiFx.RED, (float) SuitTuning.ARMOUR_MODE_WAVE_RADIUS, 10, KiFx.RING);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F);
            if (hit > 0) {
                player.displayClientMessage(Component.translatable("message.flightsuit.armour_wave", hit), true);
            }
        }
    }

    private static void tickArmour(ServerPlayer player, SuitWeapons.State state) {
        if (!state.armourMode) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.ARMOUR_MODE_DRAIN)) {
            setArmourMode(player, state, false);
            return;
        }
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false));
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (player.tickCount % 10 == 0) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(), 3, 0.5D, 0.6D, 0.5D, 0.01D);
        }
    }

    /** True while dug in (SuitServerEvents keeps it on the ground, the damage hook shrugs hits off). */
    public static boolean inArmourMode(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.armourMode;
    }

    // ---------------------------------------------------------------- jackhammer (V, M18)

    /** Grabs the aimed foe and pounds it ten times where it stands - the boss-killer. With the boss gauge full: the finisher. */
    static void jackhammer(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (state.finisherTarget != null) {
            return;
        }
        if (state.bossGauge >= SuitTuning.BOSS_GAUGE_MAX && state.hammerTarget == null && state.pistonTarget == null) {
            finisher(player, state);
            return;
        }
        if (now < state.ultReady || state.hammerTarget != null || state.pistonTarget != null) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.JACKHAMMER_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.JACKHAMMER_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.JACKHAMMER_COOLDOWN_TICKS;
        state.hammerTarget = target;
        state.hammerHits = 0;
        state.hammerTicks = 0;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.4F);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8F, 0.5F);
        player.displayClientMessage(Component.translatable("message.flightsuit.jackhammer", target.getDisplayName()), true);
    }

    private static void tickHammer(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity target = state.hammerTarget;
        if (target == null) {
            return;
        }
        if (!target.isAlive() || target.level() != player.level() || target.distanceTo(player) > SuitTuning.JACKHAMMER_RANGE + 4.0D) {
            state.hammerTarget = null;
            return;
        }
        state.hammerTicks++;
        ServerLevel level = player.serverLevel();
        // Both rooted: the wearer braced, the victim held in front of the fists (big things stay where they are).
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false));
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        double reach = player.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 0.6D;
        if (target.getBbWidth() < 2.5D) {
            Vec3 want = player.position().add(face.scale(reach));
            Vec3 pull = want.subtract(target.position());
            target.setDeltaMovement(pull.scale(0.5D).add(0.0D, 0.02D, 0.0D));
            target.hurtMarked = true;
        }
        if (!Stasis.hold(level, target, 8, false)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 6, false, false));
        }
        if (state.hammerTicks % SuitTuning.JACKHAMMER_INTERVAL != 0) {
            return;
        }
        state.hammerHits++;
        boolean last = state.hammerHits >= SuitTuning.JACKHAMMER_HITS;
        boolean left = state.hammerHits % 2 == 0;
        player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, left ? SuitAnim.HULK_PUNCH_LEFT : SuitAnim.HULK_PUNCH_RIGHT, 0));
        float damage = SuitTuning.JACKHAMMER_DAMAGE * (Stasis.isBoss(target) ? SuitTuning.JACKHAMMER_BOSS_MULTIPLIER : 1.0F);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), damage);
        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.4D, 0.4D, 0.4D, 0.5D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 0.3D, at.z, 4, 0.3D, 0.1D, 0.3D, 0.08D);
        level.playSound(null, target.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.4F);
        if (last) {
            Stasis.release(target);
            target.knockback(2.0D, -face.x, -face.z);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
            target.hurtMarked = true;
            state.wallWatch.put(target, level.getGameTime() + SuitTuning.WALL_SLAM_TICKS);
            KiFx.burst(level, at, KiFx.RED, 2.0F, 12, KiFx.BLAST);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.9F);
            state.hammerTarget = null;
        }
    }

    /** True while the jackhammer (or the finisher) pounds (rooted, half damage like the piston punch). */
    public static boolean isHammering(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && (state.hammerTarget != null || state.finisherTarget != null);
    }

    // ---------------------------------------------------------------- the boss gauge and the finisher (V, 2026-10-12)

    /** Fighting a boss fills the gauge - hitting it and being hit by it; full, V is the finisher. */
    private static void gainGauge(ServerPlayer player, float amount) {
        SuitWeapons.State state = SuitWeapons.state(player);
        if (state.finisherTarget != null || amount <= 0.0F) {
            return;
        }
        boolean wasFull = state.bossGauge >= SuitTuning.BOSS_GAUGE_MAX;
        int before = (int) state.bossGauge;
        state.bossGauge = Math.min(SuitTuning.BOSS_GAUGE_MAX, state.bossGauge + amount);
        state.bossGaugeLast = player.level().getGameTime();
        if ((int) state.bossGauge != before) {
            SuitWeapons.sendStatus(player, state);
        }
        if (!wasFull && state.bossGauge >= SuitTuning.BOSS_GAUGE_MAX) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
            KiFx.aura(player.serverLevel(), player, KiFx.RED, 30);
            player.displayClientMessage(Component.translatable("message.flightsuit.finisher_ready"), true);
        }
    }

    /** A quiet spell (no boss fought for a while): the gauge drains a point a second. */
    private static void tickGauge(ServerPlayer player, SuitWeapons.State state) {
        if (state.bossGauge <= 0.0F || player.tickCount % 20 != 0
                || player.level().getGameTime() - state.bossGaugeLast < SuitTuning.BOSS_GAUGE_QUIET_TICKS) {
            return;
        }
        state.bossGauge = Math.max(0.0F, state.bossGauge - 1.0F);
        SuitWeapons.sendStatus(player, state);
    }

    /**
     * The finisher: the aimed foe grabbed and held in front of the fists, a camera scene (SuitCutscenes.HULK_FINISHER,
     * the camera only), a flurry of punches, then one last punch that sends it flying.
     */
    private static void finisher(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.JACKHAMMER_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        long now = player.level().getGameTime();
        state.bossGauge = 0.0F;
        state.finisherTarget = target;
        state.finisherTicks = 0;
        state.ultReady = Math.max(state.ultReady, now + SuitTuning.JACKHAMMER_COOLDOWN_TICKS / 3);
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        // Facing the foe squarely, so the scene (laid out along the facing) frames the two.
        Vec3 to = target.position().subtract(player.position());
        float yaw = (float) (Math.atan2(-to.x, to.z) * (180.0D / Math.PI));
        player.setYRot(yaw);
        player.setYBodyRot(yaw);
        player.setYHeadRot(yaw);
        com.pfkfks.flightsuit.cutscene.CutsceneRunner.play(level, SuitCutscenes.HULK_FINISHER, player.position(), yaw,
                java.util.Map.of(), actors -> {
                }, true, java.util.List.of(player));
        KiFx.charge(level, player, KiFx.RED, 1.2F, SuitTuning.FINISHER_GRAB_TICKS, KiFx.AT_HANDS);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.2F, 0.4F);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("message.flightsuit.finisher", target.getDisplayName()), true);
    }

    private static void tickFinisher(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity target = state.finisherTarget;
        if (target == null) {
            return;
        }
        int t = state.finisherTicks++;
        if (t >= SuitTuning.FINISHER_TICKS || target.level() != player.level() || target.distanceTo(player) > SuitTuning.JACKHAMMER_RANGE + 8.0D) {
            state.finisherTarget = null;
            Stasis.release(target);
            return;
        }
        ServerLevel level = player.serverLevel();
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false));
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (t < SuitTuning.FINISHER_FINAL_TICK && target.isAlive()) {
            // Held in front of the fists (big things stay where they are).
            if (target.getBbWidth() < 2.5D) {
                double reach = player.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 0.6D;
                Vec3 pull = player.position().add(face.scale(reach)).subtract(target.position());
                target.setDeltaMovement(pull.scale(0.5D).add(0.0D, 0.02D, 0.0D));
                target.hurtMarked = true;
            }
            if (!Stasis.hold(level, target, 8, false)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 6, false, false));
            }
        }
        Vec3 at = target.getBoundingBox().getCenter();
        int flurryEnd = SuitTuning.FINISHER_GRAB_TICKS + SuitTuning.FINISHER_FLURRY_TICKS;
        if (t >= SuitTuning.FINISHER_GRAB_TICKS && t < flurryEnd && (t - SuitTuning.FINISHER_GRAB_TICKS) % SuitTuning.FINISHER_INTERVAL == 0) {
            int n = (t - SuitTuning.FINISHER_GRAB_TICKS) / SuitTuning.FINISHER_INTERVAL;
            boolean left = n % 2 == 0;
            player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, left ? SuitAnim.HULK_PUNCH_LEFT : SuitAnim.HULK_PUNCH_RIGHT, 0));
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), SuitTuning.FINISHER_PUNCH_DAMAGE);
            }
            Vec3 side = new Vec3(-face.z, 0.0D, face.x).scale(left ? -0.4D : 0.4D);
            KiFx.burst(level, at.add(side).add(0.0D, (n % 3 - 1) * 0.3D, 0.0D), KiFx.RED, 0.9F, 5, KiFx.SPARK);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.4D, 0.4D, 0.4D, 0.5D);
            level.playSound(null, target.blockPosition(), n % 2 == 0 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.8F, 1.2F + (n % 4) * 0.1F);
        } else if (t == flurryEnd + 2) {
            // The wind-up for the last one.
            KiFx.charge(level, player, KiFx.GOLD, 1.4F, SuitTuning.FINISHER_FINAL_TICK - t, KiFx.AT_HANDS);
            level.playSound(null, player.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.2F, 0.5F);
        } else if (t == SuitTuning.FINISHER_FINAL_TICK) {
            player.swing(InteractionHand.MAIN_HAND, true);
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_PUNCH_RIGHT, 0));
            Stasis.release(target);
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player),
                        SuitTuning.FINISHER_FINAL_DAMAGE + target.getMaxHealth() * SuitTuning.FINISHER_FINAL_SHARE);
                target.knockback(3.0D, -face.x, -face.z);
                target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.8D, 0.0D));
                target.hurtMarked = true;
                state.wallWatch.put(target, level.getGameTime() + SuitTuning.WALL_SLAM_TICKS);
            }
            KiFx.burst(level, at, KiFx.GOLD, 3.5F, 14, KiFx.BLAST);
            KiFx.burst(level, at, KiFx.WHITE, 2.0F, 8, KiFx.FLASH);
            com.pfkfks.flightsuit.cutscene.CutsceneRunner.shake(level, at, 24.0D, 12);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.7F);
            level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.5F);
        }
    }

    // ---------------------------------------------------------------- block throwing (B twice, M18)

    /**
     * B once: rips a 2x2x2 chunk out of the aimed ground and holds it floating over the right shoulder. B again:
     * hurls it where the crosshair points (it hurts what it lands on). Held too long, it's thrown on its own.
     */
    static void throwBlocks(ServerPlayer player, SuitWeapons.State state) {
        if (!state.held.isEmpty()) {
            hurl(player, state);
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(SuitTuning.THROW_RANGE)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("message.flightsuit.throw_no_block"), true);
            return;
        }
        BlockPos base = hit.getBlockPos();
        java.util.List<BlockPos> chunk = new java.util.ArrayList<>();
        for (int dx = 0; dx >= -1; dx--) {
            for (int dy = 0; dy >= -1; dy--) {
                for (int dz = 0; dz >= -1; dz--) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState block = level.getBlockState(pos);
                    float hardness = block.getDestroySpeed(level, pos);
                    if (!block.isAir() && hardness >= 0.0F && hardness <= 20.0F && level.getBlockEntity(pos) == null
                            && block.getFluidState().isEmpty() && block.isCollisionShapeFullBlock(level, pos) && level.mayInteract(player, pos)) {
                        chunk.add(pos);
                    }
                }
            }
        }
        if (chunk.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.throw_no_block"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.THROW_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        for (BlockPos pos : chunk) {
            BlockState block = level.getBlockState(pos);
            level.removeBlock(pos, false);
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, block);
            falling.setNoGravity(true);
            falling.setDeltaMovement(Vec3.ZERO);
            falling.time = 1;
            falling.dropItem = false;
            state.held.add(falling);
        }
        state.heldSince = now;
        SuitWeapons.sendStatus(player, state);
        BlockState dust = level.getBlockState(base.below(2));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust.isAir() ? Blocks.DIRT.defaultBlockState() : dust),
                base.getX(), base.getY(), base.getZ(), 30, 1.0D, 0.6D, 1.0D, 0.1D);
        level.playSound(null, base, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_SLAM, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.throw_held"), true);
    }

    /** Where the held chunk floats: over the right shoulder, a little ahead, bobbing. */
    private static Vec3 shoulder(ServerPlayer player) {
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 right = new Vec3(-face.z, 0.0D, face.x);
        double bob = Math.sin(player.tickCount * 0.12D) * 0.06D;
        return player.position().add(face.scale(0.6D)).add(right.scale(1.1D)).add(0.0D, player.getBbHeight() + 0.9D + bob, 0.0D);
    }

    /** The chunk rides along until it's thrown; dropped (with gravity, hurting nobody) if the suit comes off. */
    private static void tickHeld(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (state.held.isEmpty()) {
            return;
        }
        state.held.removeIf(falling -> falling.isRemoved() || falling.level() != player.level());
        if (state.held.isEmpty()) {
            SuitWeapons.sendStatus(player, state);
            return;
        }
        if (suitClass != SuitClass.HULKBUSTER || !player.isAlive()) {
            for (FallingBlockEntity falling : state.held) {
                falling.setNoGravity(false);
                falling.time = 1;
            }
            state.held.clear();
            SuitWeapons.sendStatus(player, state);
            return;
        }
        long now = player.level().getGameTime();
        if (now - state.heldSince >= SuitTuning.THROW_HOLD_TICKS) {
            hurl(player, state);
            return;
        }
        Vec3 centre = shoulder(player);
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 right = new Vec3(-face.z, 0.0D, face.x);
        for (int i = 0; i < state.held.size(); i++) {
            FallingBlockEntity falling = state.held.get(i);
            double dx = (i & 1) - 0.5D;
            double dy = (i >> 1) & 1;
            double dz = ((i >> 2) & 1) - 0.5D;
            Vec3 at = centre.add(right.scale(dx)).add(face.scale(dz)).add(0.0D, dy, 0.0D);
            falling.setPos(at.x, at.y - 0.5D, at.z);
            falling.setDeltaMovement(Vec3.ZERO);
            falling.time = 1;
            falling.fallDistance = 0.0F;
        }
        if (player.tickCount % 10 == 0) {
            BlockState dust = state.held.get(0).getBlockState();
            Vec3 under = centre.add(0.0D, -0.6D, 0.0D);
            player.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), under.x, under.y, under.z, 2, 0.6D, 0.1D, 0.6D, 0.02D);
        }
    }

    /** B again: the chunk goes where the crosshair points, arcing in, hurting whatever it lands on. */
    private static void hurl(ServerPlayer player, SuitWeapons.State state) {
        ServerLevel level = player.serverLevel();
        SuitWeapons.Aim aim = SuitWeapons.aim(player, 48.0D);
        Vec3 target = aim.target() != null ? aim.target().getBoundingBox().getCenter() : aim.end();
        for (FallingBlockEntity falling : state.held) {
            if (falling.isRemoved()) {
                continue;
            }
            Vec3 from = falling.position();
            Vec3 dir = target.subtract(from);
            double distance = dir.horizontalDistance();
            Vec3 velocity = dir.normalize().scale(SuitTuning.THROW_SPEED).add(0.0D, 0.2D + distance * 0.02D, 0.0D)
                    .add((level.random.nextDouble() - 0.5D) * 0.15D, 0.0D, (level.random.nextDouble() - 0.5D) * 0.15D);
            falling.setNoGravity(false);
            falling.setDeltaMovement(velocity);
            falling.hurtMarked = true;
            falling.setHurtsEntities(SuitTuning.THROW_DAMAGE, (int) (SuitTuning.THROW_DAMAGE * 3));
            falling.time = 1;
        }
        state.held.clear();
        state.skill3Ready = level.getGameTime() + SuitTuning.THROW_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_PUNCH_RIGHT, 0));
    }

    // ---------------------------------------------------------------- flurry (hold)

    static void tickFlurry(ServerPlayer player, SuitWeapons.State state) {
        if (state.firingTicks % SuitTuning.PUNCH_INTERVAL != 1 || state.pistonTarget != null) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.PUNCH_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        boolean left = state.punches++ % 2 == 1;
        player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, left ? SuitAnim.HULK_PUNCH_LEFT : SuitAnim.HULK_PUNCH_RIGHT, 0));
        ServerLevel level = player.serverLevel();
        LivingEntity aimed = SuitWeapons.aim(player, SuitTuning.PUNCH_REACH + 1.0D).target();
        int landed = punchFan(level, player, SuitTuning.PUNCH_DAMAGE, entity -> !SuitWeapons.isFriendly(player, entity)
                && (entity == aimed || SuitWeapons.isHostile(player, entity) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)), state);
        Vec3 fist = fist(player, left);
        level.sendParticles(ParticleTypes.CLOUD, fist.x, fist.y, fist.z, 3, 0.15D, 0.15D, 0.15D, 0.05D);
        level.playSound(null, player.blockPosition(), landed > 0 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 1.0F, landed > 0 ? 0.6F : 0.8F);
        if (landed > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.8F, 0.9F);
        }
    }

    /** Roughly where a fist lands, in front of the chest. */
    private static Vec3 fist(LivingEntity puncher, boolean left) {
        Vec3 look = puncher.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return puncher.getEyePosition().add(look.scale(1.8D)).add(right.scale(left ? -0.5D : 0.5D)).add(0.0D, -0.5D, 0.0D);
    }

    /**
     * One punch: everything {@code hits} allows in the fan in front (PUNCH_REACH, PUNCH_ARC_DEG) takes the damage and
     * is thrown back; thrown into a wall soon after, it takes WALL_SLAM_DAMAGE more ({@code state} keeps the watch -
     * null for a companion, which skips the wall check). Returns how many it hit.
     */
    public static int punchFan(ServerLevel level, LivingEntity puncher, float damage, Predicate<LivingEntity> hits) {
        return punchFan(level, puncher, damage, hits, null);
    }

    static int punchFan(ServerLevel level, LivingEntity puncher, float damage, Predicate<LivingEntity> hits,
                        @org.jetbrains.annotations.Nullable SuitWeapons.State state) {
        Vec3 eye = puncher.getEyePosition();
        Vec3 look = puncher.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        look = look.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, puncher.getYRot()) : look.normalize();
        double reach = SuitTuning.PUNCH_REACH;
        double cos = Math.cos(Math.toRadians(SuitTuning.PUNCH_ARC_DEG / 2.0D));
        int landed = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, puncher.getBoundingBox().inflate(reach, 1.5D, reach),
                entity -> entity != puncher && entity.isAlive() && hits.test(entity))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye).multiply(1.0D, 0.0D, 1.0D);
            double flat = to.length() - target.getBbWidth() / 2.0D - puncher.getBbWidth() / 2.0D;
            if (flat > reach || to.lengthSqr() > 1.0E-4D && to.normalize().dot(look) < cos) {
                continue;
            }
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(puncher instanceof ServerPlayer player ? puncher.damageSources().playerAttack(player)
                    : puncher.damageSources().mobAttack(puncher), damage);
            if (hurt) {
                target.knockback(SuitTuning.PUNCH_KNOCKBACK, -look.x, -look.z);
                target.hurtMarked = true;
                if (state != null) {
                    state.wallWatch.put(target, level.getGameTime() + SuitTuning.WALL_SLAM_TICKS);
                }
                landed++;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25D, 0.25D, 0.25D, 0.4D);
        }
        return landed;
    }

    /** Punched foes that hit a wall while still flying back take the extra hit (once). */
    private static void tickWallSlams(ServerPlayer player, SuitWeapons.State state) {
        if (state.wallWatch.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        Iterator<Map.Entry<LivingEntity, Long>> it = state.wallWatch.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = it.next();
            LivingEntity target = entry.getKey();
            if (!target.isAlive() || now > entry.getValue() || target.level() != player.level()) {
                it.remove();
                continue;
            }
            if (target.horizontalCollision) {
                it.remove();
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), SuitTuning.WALL_SLAM_DAMAGE);
                ServerLevel level = player.serverLevel();
                BlockState wall = wallBehind(target);
                if (wall != null) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wall), target.getX(), target.getY() + target.getBbHeight() / 2.0D,
                            target.getZ(), 20, target.getBbWidth() / 2.0D + 0.2D, target.getBbHeight() / 3.0D, target.getBbWidth() / 2.0D + 0.2D, 0.1D);
                }
                level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.7F);
            }
        }
    }

    /** The block the target was thrown into (for the dust), if one is right next to it. */
    private static BlockState wallBehind(LivingEntity target) {
        BlockPos at = target.blockPosition().above();
        for (BlockPos pos : new BlockPos[]{at.north(), at.south(), at.east(), at.west(), at.below().north(), at.below().south(),
                at.below().east(), at.below().west()}) {
            BlockState state = target.level().getBlockState(pos);
            if (!state.isAir()) {
                return state;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- piston punch (X)

    static void pistonPunch(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready || state.pistonTarget != null) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.PISTON_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.PISTON_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.PISTON_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.pistonTarget = target;
        state.pistonHits = 0;
        state.pistonTicks = 0;
        player.level().playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.5F);
        player.displayClientMessage(Component.translatable("message.flightsuit.piston_punch"), true);
    }

    /** True while the pistons are pounding (the wearer is rooted and takes half). */
    public static boolean isPounding(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.pistonTarget != null;
    }

    private static void tickPiston(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity target = state.pistonTarget;
        if (target == null) {
            return;
        }
        if (!target.isAlive() || target.level() != player.level()) {
            state.pistonTarget = null;
            return;
        }
        state.pistonTicks++;
        ServerLevel level = player.serverLevel();
        double reach = player.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 1.2D;
        Vec3 to = target.position().subtract(player.position());
        if (state.pistonHits == 0 && to.horizontalDistance() > reach) {
            // The rush: straight at it.
            if (state.pistonTicks > PISTON_RUSH_TICKS) {
                state.pistonTarget = null;
                return;
            }
            Vec3 dir = to.normalize();
            player.setDeltaMovement(dir.scale(Math.min(1.6D, to.length())));
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5D, player.getZ(), 2, 0.3D, 0.2D, 0.3D, 0.02D);
            return;
        }
        // Rooted while it pounds.
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false));
        if (state.pistonTicks % SuitTuning.PISTON_HIT_INTERVAL != 0) {
            return;
        }
        state.pistonHits++;
        boolean last = state.pistonHits >= SuitTuning.PISTON_HITS;
        boolean left = state.pistonHits % 2 == 0;
        player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, left ? SuitAnim.HULK_PUNCH_LEFT : SuitAnim.HULK_PUNCH_RIGHT, 0));
        Vec3 push = to.multiply(1.0D, 0.0D, 1.0D);
        push = push.lengthSqr() < 1.0E-4D ? player.getLookAngle() : push.normalize();
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), last ? SuitTuning.PISTON_FINAL_DAMAGE : SuitTuning.PISTON_DAMAGE);
        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.3D, 0.3D, 0.3D, 0.5D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.2D, 0.2D, 0.2D, 0.08D);
        level.playSound(null, target.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.9F, 1.2F);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (last) {
            stun(level, target, SuitTuning.PISTON_STUN_TICKS);
            target.knockback(2.6D, -push.x, -push.z);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.4D, 0.0D));
            target.hurtMarked = true;
            state.wallWatch.put(target, level.getGameTime() + SuitTuning.WALL_SLAM_TICKS);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.3F);
            state.pistonTarget = null;
        }
    }

    /** Stunned: held stock-still (Stasis) - or, for what can't be held (bosses), slowed to a crawl and weakened. */
    public static void stun(ServerLevel level, LivingEntity target, int ticks) {
        if (!Stasis.hold(level, target, ticks, false)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
        }
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() + 0.3D, target.getZ(), 6, 0.3D, 0.1D, 0.3D, 0.0D);
    }

    // ---------------------------------------------------------------- ground slam (C)

    static void groundSlam(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready || state.slamPhase != SLAM_NONE) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SLAM_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.SLAM_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.slamTicks = 0;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.HULK_SLAM, 0));
        if (player.onGround()) {
            state.slamPhase = SLAM_RISING;
            player.setDeltaMovement(player.getDeltaMovement().x * 0.3D, 0.5D, player.getDeltaMovement().z * 0.3D);
        } else {
            // In the air: straight down first.
            state.slamPhase = SLAM_FALLING;
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
        player.hurtMarked = true;
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    private static void tickSlam(ServerPlayer player, SuitWeapons.State state) {
        if (state.slamPhase == SLAM_NONE) {
            return;
        }
        state.slamTicks++;
        player.fallDistance = 0.0F;
        if (state.slamPhase == SLAM_RISING) {
            if (state.slamTicks >= 6 || player.getDeltaMovement().y <= 0.0D) {
                state.slamPhase = SLAM_FALLING;
            }
            return;
        }
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (!player.onGround() && state.slamTicks < SLAM_MAX_TICKS) {
            player.setDeltaMovement(player.getDeltaMovement().x * 0.5D, -1.4D, player.getDeltaMovement().z * 0.5D);
            player.hurtMarked = true;
            return;
        }
        state.slamPhase = SLAM_NONE;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        slam(player.serverLevel(), player, SuitTuning.SLAM_RADIUS, SuitTuning.SLAM_DAMAGE,
                entity -> !SuitWeapons.isFriendly(player, entity) && (SuitWeapons.isHostile(player, entity) || entity == player.getLastHurtMob())
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity));
    }

    /**
     * The shockwave where both fists hit the ground: everything {@code hits} allows within {@code radius} is hurt and
     * thrown up; the ground cracks in rings of its own dust and the earth booms. Returns how many it hit.
     */
    public static int slam(ServerLevel level, LivingEntity centre, double radius, float damage, Predicate<LivingEntity> hits) {
        int count = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(radius, 3.0D, radius),
                entity -> entity != centre && entity.isAlive() && entity.distanceTo(centre) <= radius + centre.getBbWidth() / 2.0D
                        && hits.test(entity))) {
            victim.invulnerableTime = 0;
            victim.hurt(centre instanceof ServerPlayer player ? centre.damageSources().playerAttack(player)
                    : centre.damageSources().mobAttack(centre), damage);
            Vec3 out = victim.position().subtract(centre.position()).multiply(1.0D, 0.0D, 1.0D);
            out = out.lengthSqr() < 1.0E-4D ? Vec3.ZERO : out.normalize().scale(0.4D);
            victim.setDeltaMovement(victim.getDeltaMovement().add(out.x, SuitTuning.SLAM_LIFT, out.z));
            victim.hurtMarked = true;
            count++;
        }
        BlockPos below = centre.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        }
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int ring = 1; ring <= 4; ring++) {
            double r = radius * ring / 4.0D;
            int points = 10 + ring * 6;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0D / points + ring * 0.3D;
                double x = centre.getX() + Math.cos(angle) * r;
                double z = centre.getZ() + Math.sin(angle) * r;
                level.sendParticles(crumbs, x, centre.getY() + 0.1D, z, 3, 0.2D, 0.05D, 0.2D, 0.15D);
                if (i % 2 == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, centre.getY() + 0.2D, z, 0, 0.0D, 0.03D, 0.0D, 1.0D);
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, centre.getX(), centre.getY() + 0.3D, centre.getZ(), 3, 1.0D, 0.1D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.POOF, centre.getX(), centre.getY() + 0.3D, centre.getZ(), 30, radius / 3.0D, 0.2D, radius / 3.0D, 0.1D);
        level.playSound(null, centre.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.5F);
        level.playSound(null, centre.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, centre.blockPosition(), SoundEvents.WARDEN_STEP, SoundSource.PLAYERS, 2.0F, 0.6F);
        return count;
    }

    // ---------------------------------------------------------------- heavy plating (passive)

    /** Small mobs it walks into are shoved out of the way. */
    private static void shoveSmallMobs(ServerPlayer player) {
        if (player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-3D) {
            return;
        }
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(0.4D),
                mob -> mob.isAlive() && mob.getBbHeight() < SuitTuning.HULKBUSTER_SHOVE_HEIGHT && !SuitWeapons.isFriendly(player, mob))) {
            Vec3 out = mob.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
            out = out.lengthSqr() < 1.0E-4D ? player.getLookAngle().multiply(1.0D, 0.0D, 1.0D) : out;
            out = out.normalize().scale(0.6D);
            mob.setDeltaMovement(mob.getDeltaMovement().add(out.x, 0.2D, out.z));
            mob.hurtMarked = true;
        }
    }

    /** Small projectiles glance off the plating (half damage); the pounding wearer takes half of everything. */
    static void onHurt(LivingHurtEvent event) {
        // The boss gauge: blows the Mark 44 lands on a boss, and the boss's blows on it.
        if (event.getSource().getEntity() instanceof ServerPlayer hitter && hitter != event.getEntity()
                && SuitWeapons.armedClass(hitter) == SuitClass.HULKBUSTER && Stasis.isBoss(event.getEntity())) {
            gainGauge(hitter, event.getAmount() * SuitTuning.BOSS_GAUGE_PER_DEALT);
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player
                && SuitWeapons.armedClass(player) == SuitClass.HULKBUSTER && Stasis.isBoss(attacker)) {
            gainGauge(player, event.getAmount() * SuitTuning.BOSS_GAUGE_PER_TAKEN);
        }
        if (isPounding(player) || isHammering(player)) {
            event.setAmount(event.getAmount() * SuitTuning.PISTON_DAMAGE_TAKEN);
        }
        if (inArmourMode(player)) {
            event.setAmount(event.getAmount() * SuitTuning.ARMOUR_MODE_TAKEN);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
        if (event.getSource().getDirectEntity() instanceof Projectile && wearsFullSet(player)) {
            event.setAmount(event.getAmount() * SuitTuning.HULKBUSTER_PROJECTILE_TAKEN);
        }
    }
}
