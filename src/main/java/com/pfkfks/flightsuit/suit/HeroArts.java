package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.ClawshotS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * Mark 4, the Hero of Twilight (after Twilight Princess's hero). A swordsman rather than a gunner:
 * - Master Sword (hold right click): a slash across the front every few ticks; at full health every slash also
 *   looses a sword beam that flies straight on (the old Zelda rule);
 * - spin attack (X): one sweep all the way round; at full health the great spin, wider and harder;
 * - clawshot (C): the claw flies at the crosshair - into a block, it reels you there; into a monster, it drags the
 *   monster to you (a big one pulls you to it instead, and you land a slash on arrival). C again lets go;
 * - Hylian shield (passive): arrows and other projectiles from the front glance off.
 * Sword beams are simulated here (a moving point drawn with particles) rather than spawned as entities.
 */
public final class HeroArts {
    static final byte HOOK_NONE = 0;
    static final byte HOOK_OUT = 1;
    /** Reeling the hero in to the claw (a block, or a monster too big to drag). */
    static final byte HOOK_REEL = 2;
    /** Dragging a monster in to the hero. */
    static final byte HOOK_DRAG = 3;
    static final byte HOOK_BACK = 4;

    private static final int SPIN_SWEEP_TICKS = 8;
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.85F, 1.0F), 1.3F);
    private static final DustParticleOptions BEAM_CORE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.8F);

    /** A sword beam in flight. */
    static final class SwordBeam {
        Vec3 pos;
        final Vec3 dir;
        double travelled;

        SwordBeam(Vec3 pos, Vec3 dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    private HeroArts() {
    }

    static boolean fullHealth(LivingEntity entity) {
        return entity.getHealth() >= entity.getMaxHealth() - 0.01F;
    }

    /** The right hand, roughly - where the claw leaves from. */
    private static Vec3 hand(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.6D)).add(right.scale(0.35D)).add(0.0D, -0.25D, 0.0D);
    }

    private static void hit(ServerPlayer player, LivingEntity target, float damage, double knockback, Vec3 push) {
        target.invulnerableTime = 0;
        if (target.hurt(player.damageSources().playerAttack(player), damage) && knockback > 0.0D) {
            target.knockback(knockback, -push.x, -push.z);
        }
        Vec3 center = target.getBoundingBox().getCenter();
        player.serverLevel().sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 6, 0.2D, 0.2D, 0.2D, 0.3D);
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        tickBeams(player, state);
        tickSpin(player, state);
        tickHook(player, state, suitClass);
    }

    // ---------------------------------------------------------------- Master Sword

    static void tickSword(ServerPlayer player, SuitWeapons.State state) {
        if (state.firingTicks % SuitTuning.SWORD_INTERVAL != 1) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SWORD_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean backhand = state.swings++ % 2 == 1;
        slash(player, level);
        Vec3 look = player.getLookAngle();
        Vec3 front = player.getEyePosition().add(look.scale(1.6D)).add(0.0D, -0.3D, 0.0D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.8F, backhand ? 1.25F : 1.05F);
        if (fullHealth(player)) {
            state.swordBeams.add(new SwordBeam(player.getEyePosition().add(look.scale(0.8D)).add(0.0D, -0.2D, 0.0D), look));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.9F, 1.8F);
        }
    }

    /** One slash: every hostile in the arc in front, plus whatever is right under the crosshair. */
    private static void slash(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = SuitTuning.SWORD_REACH;
        double cos = Math.cos(Math.toRadians(SuitTuning.SWORD_ARC_DEG / 2.0D));
        LivingEntity aimed = SuitWeapons.aim(player, reach).target();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity) && (entity == aimed || entity instanceof Enemy))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (target != aimed && (to.length() - target.getBbWidth() / 2.0D > reach || to.normalize().dot(look) < cos
                    || !player.hasLineOfSight(target))) {
                continue;
            }
            hit(player, target, SuitTuning.SWORD_DAMAGE, 0.4D, look);
        }
    }

    /** Sword beams fly straight on until they hit something or run out of range. */
    private static void tickBeams(ServerPlayer player, SuitWeapons.State state) {
        if (state.swordBeams.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Iterator<SwordBeam> it = state.swordBeams.iterator();
        while (it.hasNext()) {
            SwordBeam beam = it.next();
            Vec3 from = beam.pos;
            Vec3 to = from.add(beam.dir.scale(SuitTuning.SWORD_BEAM_SPEED));
            BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end, new AABB(from, end).inflate(1.0D),
                    entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                            && !SuitWeapons.isFriendly(player, entity));
            drawBeam(level, from.lerp(end, 0.5D), beam.dir);
            drawBeam(level, end, beam.dir);
            if (hit != null) {
                hit(player, (LivingEntity) hit.getEntity(), SuitTuning.SWORD_BEAM_DAMAGE, 0.3D, beam.dir);
                burst(level, hit.getLocation());
                it.remove();
                continue;
            }
            beam.travelled += end.distanceTo(from);
            beam.pos = end;
            if (block.getType() != HitResult.Type.MISS || beam.travelled >= SuitTuning.SWORD_BEAM_RANGE) {
                burst(level, end);
                it.remove();
            }
        }
    }

    /** A pale blue crescent standing across the beam's path, white along the middle. */
    private static void drawBeam(ServerLevel level, Vec3 at, Vec3 dir) {
        Vec3 side = Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        for (int k = -3; k <= 3; k++) {
            Vec3 p = at.add(side.scale(k * 0.18D)).subtract(dir.scale(k * k * 0.035D));
            level.sendParticles(Math.abs(k) <= 1 ? BEAM_CORE : BEAM_DUST, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.2D, 0.2D, 0.2D, 0.25D);
        level.sendParticles(BEAM_DUST, at.x, at.y, at.z, 8, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    // ---------------------------------------------------------------- spin attack (X)

    static void spinAttack(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown",
                    String.format("%.1f", (state.skill1Ready - now) / 20.0F)), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SPIN_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        boolean great = fullHealth(player);
        double radius = great ? SuitTuning.GREAT_SPIN_RADIUS : SuitTuning.SPIN_RADIUS;
        float damage = great ? SuitTuning.GREAT_SPIN_DAMAGE : SuitTuning.SPIN_DAMAGE;
        state.skill1Ready = now + SuitTuning.SPIN_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.spinTicks = SPIN_SWEEP_TICKS;
        state.spinRadius = radius;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SPIN_ATTACK, 0));

        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2.0D, radius),
                entity -> entity.isAlive() && entity instanceof Enemy && !SuitWeapons.isFriendly(player, entity))) {
            Vec3 out = target.position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
            if (out.length() - target.getBbWidth() / 2.0D > radius) {
                continue;
            }
            hit(player, target, damage, great ? 1.2D : 0.8D, out.lengthSqr() < 1.0E-4D ? player.getLookAngle() : out.normalize());
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.9F);
        if (great) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        player.displayClientMessage(Component.translatable(great ? "message.flightsuit.great_spin" : "message.flightsuit.spin"), true);
    }

    /** The sweep travels once round the hero over a few ticks; the great spin leaves a ring of light at its edge. */
    private static void tickSpin(ServerPlayer player, SuitWeapons.State state) {
        if (state.spinTicks <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int step = SPIN_SWEEP_TICKS - state.spinTicks;
        double base = Math.toRadians(player.getYRot() + 90.0D);
        double y = player.getY() + 1.0D;
        boolean great = state.spinRadius > SuitTuning.SPIN_RADIUS;
        for (int i = 0; i < 3; i++) {
            double angle = base + 2.0D * Math.PI * (step * 3 + i) / (SPIN_SWEEP_TICKS * 3);
            double mid = Math.min(2.2D, state.spinRadius * 0.5D);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * mid, y, player.getZ() + Math.sin(angle) * mid,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (great) {
                level.sendParticles(BEAM_DUST, player.getX() + Math.cos(angle) * state.spinRadius, y,
                        player.getZ() + Math.sin(angle) * state.spinRadius, 2, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
        state.spinTicks--;
    }

    // ---------------------------------------------------------------- clawshot (C)

    static void clawshot(ServerPlayer player, SuitWeapons.State state) {
        if (state.hookPhase != HOOK_NONE) {
            // C again: let go.
            if (state.hookPhase != HOOK_BACK) {
                state.hookPhase = HOOK_BACK;
                state.hookMob = null;
            }
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.skill2Ready) {
            player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown",
                    String.format("%.1f", (state.skill2Ready - now) / 20.0F)), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CLAW_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.hookPhase = HOOK_OUT;
        state.hookPos = hand(player);
        state.hookDir = player.getLookAngle();
        state.hookTravelled = 0.0D;
        state.hookTicks = 0;
        state.hookMob = null;
        player.level().playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.4F);
        sendHook(player, state, true);
    }

    private static void tickHook(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (state.hookPhase == HOOK_NONE) {
            return;
        }
        if (suitClass != SuitClass.HERO || !player.isAlive() || SuitUpManager.isSuitingUp(player)) {
            endHook(player, state);
            return;
        }
        state.hookTicks++;
        switch (state.hookPhase) {
            case HOOK_OUT -> flyOut(player, state);
            case HOOK_REEL -> reel(player, state);
            case HOOK_DRAG -> drag(player, state);
            default -> retract(player, state);
        }
        if (state.hookPhase != HOOK_NONE) {
            sendHook(player, state, true);
        }
    }

    /** Small enough to haul in; bosses and anything big pull the hero to them instead. */
    private static boolean canDrag(LivingEntity mob) {
        return !(mob instanceof Player) && mob.canChangeDimensions()
                && mob.getBbWidth() * mob.getBbWidth() * mob.getBbHeight() <= SuitTuning.CLAW_DRAG_MAX_VOLUME;
    }

    private static void flyOut(ServerPlayer player, SuitWeapons.State state) {
        ServerLevel level = player.serverLevel();
        Vec3 from = state.hookPos;
        Vec3 to = from.add(state.hookDir.scale(SuitTuning.CLAW_SPEED));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end, new AABB(from, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                        && !SuitWeapons.isFriendly(player, entity));
        if (hit != null) {
            LivingEntity mob = (LivingEntity) hit.getEntity();
            state.hookMob = mob;
            state.hookPos = hit.getLocation();
            state.hookTicks = 0;
            mob.invulnerableTime = 0;
            mob.hurt(player.damageSources().playerAttack(player), SuitTuning.CLAW_DAMAGE);
            state.hookPhase = canDrag(mob) ? HOOK_DRAG : HOOK_REEL;
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
            return;
        }
        state.hookPos = end;
        if (block.getType() != HitResult.Type.MISS) {
            state.hookPhase = HOOK_REEL;
            state.hookTicks = 0;
            level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 8, 0.1D, 0.1D, 0.1D, 0.2D);
            level.playSound(null, end.x, end.y, end.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
            return;
        }
        state.hookTravelled += SuitTuning.CLAW_SPEED;
        if (state.hookTravelled >= SuitTuning.CLAW_RANGE) {
            state.hookPhase = HOOK_BACK;
        }
    }

    /** Pulls the hero along the chain; a hooked monster gets a slash on arrival. */
    private static void reel(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity mob = state.hookMob;
        if (mob != null) {
            if (!mob.isAlive() || mob.level() != player.level()) {
                state.hookPhase = HOOK_BACK;
                state.hookMob = null;
                return;
            }
            state.hookPos = mob.getBoundingBox().getCenter();
        }
        Vec3 body = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = state.hookPos.subtract(body);
        double stop = mob != null ? mob.getBbWidth() / 2.0D + 1.2D : 1.0D;
        double dist = to.length();
        player.fallDistance = 0.0F;
        if (dist <= stop || state.hookTicks > SuitTuning.CLAW_MAX_REEL_TICKS) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            if (mob != null && dist <= stop) {
                player.swing(InteractionHand.MAIN_HAND, true);
                hit(player, mob, SuitTuning.CLAW_STRIKE_DAMAGE, 0.6D, to.normalize());
                Vec3 at = mob.getBoundingBox().getCenter();
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                player.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            endHook(player, state);
            return;
        }
        player.setDeltaMovement(to.normalize().scale(Math.max(0.3D, Math.min(SuitTuning.CLAW_PULL_SPEED, dist - stop + 0.3D))));
        player.hurtMarked = true;
    }

    /** Hauls a hooked monster in to just in front of the hero, where it staggers for a moment. */
    private static void drag(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity mob = state.hookMob;
        if (mob == null || !mob.isAlive() || mob.level() != player.level()) {
            state.hookPhase = HOOK_BACK;
            state.hookMob = null;
            return;
        }
        Vec3 ahead = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        ahead = ahead.lengthSqr() < 1.0E-4D ? Vec3.ZERO : ahead.normalize().scale(1.8D);
        Vec3 spot = player.position().add(ahead);
        Vec3 to = spot.subtract(mob.position());
        if (to.length() < 0.8D || mob.distanceTo(player) < 2.0D || state.hookTicks > SuitTuning.CLAW_MAX_REEL_TICKS) {
            mob.setDeltaMovement(Vec3.ZERO);
            mob.hurtMarked = true;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            endHook(player, state);
            return;
        }
        mob.setDeltaMovement(to.normalize().scale(Math.min(SuitTuning.CLAW_DRAG_SPEED, to.length() * 0.6D)));
        mob.hurtMarked = true;
        mob.hasImpulse = true;
        mob.fallDistance = 0.0F;
        state.hookPos = mob.getBoundingBox().getCenter();
    }

    private static void retract(ServerPlayer player, SuitWeapons.State state) {
        Vec3 to = hand(player).subtract(state.hookPos);
        if (to.length() <= SuitTuning.CLAW_SPEED) {
            endHook(player, state);
            return;
        }
        state.hookPos = state.hookPos.add(to.normalize().scale(SuitTuning.CLAW_SPEED));
    }

    private static void endHook(ServerPlayer player, SuitWeapons.State state) {
        state.hookPhase = HOOK_NONE;
        state.hookMob = null;
        state.skill2Ready = player.level().getGameTime() + SuitTuning.CLAW_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        sendHook(player, state, false);
    }

    private static void sendHook(ServerPlayer player, SuitWeapons.State state, boolean active) {
        ModNetwork.sendToTrackingAndSelf(player, new ClawshotS2CPacket(player.getId(), active, state.hookPos));
    }

    // ---------------------------------------------------------------- Hylian shield (passive)

    /** Projectiles from the front glance off the shield (a little reactor power per block). */
    static void hylianShield(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getDirectEntity() instanceof Projectile projectile)
                || projectile.getOwner() == player || SuitWeapons.armedClass(player) != SuitClass.HERO) {
            return;
        }
        Vec3 from = projectile.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 facing = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (from.lengthSqr() < 1.0E-4D || from.normalize().dot(facing) < Math.cos(Math.toRadians(SuitTuning.HYLIAN_BLOCK_ANGLE_DEG))) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.HYLIAN_BLOCK_COST)) {
            return;
        }
        event.setCanceled(true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 6,
                0.1D, 0.1D, 0.1D, 0.2D);
    }
}
