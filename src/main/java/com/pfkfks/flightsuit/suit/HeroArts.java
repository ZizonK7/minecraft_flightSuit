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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.Iterator;
import com.pfkfks.flightsuit.fx.KiFx;

/**
 * Mark 4, the Hero of Twilight (after Twilight Princess's hero). A swordsman rather than a gunner:
 * - Master Sword (left click): a cut across the front; at full health (or a full chain) every cut also looses a
 *   sword beam that flies straight on (the old Zelda rule);
 * - spin attack (X at once, or right click held, then let go - M18 feedback): one sweep all the way round; held to
 *   a full charge (or at full health) the great spin, wider and harder;
 * - clawshot (C): the claw flies at the crosshair - into a block, it reels you there; into a monster, it drags the
 *   monster to you (a big one pulls you to it instead, and you land a slash on arrival). C again lets go;
 * - Hylian shield (Z held - M18 feedback, "the famous shield"): projectiles from the front glance off; a melee blow met within a few ticks of
 *   raising it is a perfect guard (free, the attacker staggers, two links), a late one still half lands.
 * After the first M18 test ("hard but strong" came out just strong): the chain. Every move that differs from the
 * one before it, within CHAIN_WINDOW_TICKS, adds a link; the sword's damage grows CHAIN_STEP per link (CHAIN_MAX
 * links: sword beams on every cut). The same move twice adds nothing and lets the window run out; a hit taken
 * breaks the chain. Sword beams are simulated here (a moving point drawn with particles) rather than spawned.
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
    /** The chain's moves (State.chainLast). */
    static final byte MOVE_SWING = 1;
    static final byte MOVE_SPIN = 2;
    static final byte MOVE_CLAW = 3;
    static final byte MOVE_SONG = 4;
    static final byte MOVE_GUARD = 5;
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

    /** The sword at its best: full health, the Triforce's power (V) while it lasts, or the chain at its longest. */
    static boolean empowered(ServerPlayer player, SuitWeapons.State state) {
        return fullHealth(player) || state.triforceUntil > player.level().getGameTime() || state.chain >= SuitTuning.CHAIN_MAX;
    }

    /** The damage multiplier: the chain's links, and the Triforce's power on top while it lasts. */
    static float power(ServerPlayer player, SuitWeapons.State state) {
        float chain = 1.0F + SuitTuning.CHAIN_STEP * state.chain;
        return state.triforceUntil > player.level().getGameTime() ? chain * SuitTuning.TRIFORCE_MULTIPLIER : chain;
    }

    /**
     * One move of the chain: a different move from the last within the window adds a link (and the HUD, a flash and
     * a rising note say so); the same move again adds nothing and leaves the window to run out.
     */
    static void link(ServerPlayer player, SuitWeapons.State state, byte move) {
        long now = player.level().getGameTime();
        if (now > state.chainUntil) {
            state.chain = 0;
            state.chainLast = 0;
        }
        if (move == state.chainLast) {
            return;
        }
        state.chainLast = move;
        state.chainUntil = now + SuitTuning.CHAIN_WINDOW_TICKS;
        if (state.chain >= SuitTuning.CHAIN_MAX) {
            return;
        }
        state.chain++;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        boolean max = state.chain >= SuitTuning.CHAIN_MAX;
        int colour = max ? KiFx.GOLD : state.chain >= 3 ? KiFx.SBC : 0x7CE8A0;
        KiFx.burst(level, hand(player), colour, 0.35F + 0.12F * state.chain, 6, max ? KiFx.RING : KiFx.SPARK);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.8F + 0.18F * state.chain);
        if (max) {
            KiFx.aura(level, player, KiFx.GOLD, 20);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.5F);
            player.displayClientMessage(Component.translatable("message.flightsuit.chain_max"), true);
        }
    }

    /** A hit taken, or the window run out: the chain falls apart. */
    static void breakChain(ServerPlayer player, SuitWeapons.State state, boolean hurt) {
        if (state.chain == 0 && state.chainLast == 0) {
            return;
        }
        boolean had = state.chain > 0;
        state.chain = 0;
        state.chainLast = 0;
        state.chainUntil = 0L;
        SuitWeapons.sendStatus(player, state);
        if (had) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, hurt ? 0.9F : 0.5F,
                    hurt ? 0.7F : 1.1F);
        }
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
        tickSong(player, state, suitClass);
        if (suitClass != SuitClass.HERO) {
            state.triforceUntil = 0L;
            state.chain = 0;
            state.chainLast = 0;
            state.shieldHeld = false;
            state.spinCharge = 0;
            return;
        }
        tickShieldHeld(player, state);
        long now = player.level().getGameTime();
        if (state.chain > 0 && now > state.chainUntil) {
            breakChain(player, state, false);
        }
        if (state.triforceUntil > 0L && state.triforceUntil <= now) {
            state.triforceUntil = 0L;
            SuitWeapons.sendStatus(player, state);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
        } else if (state.triforceUntil > now && player.tickCount % 40 == 0) {
            KiFx.aura(player.serverLevel(), player, KiFx.GOLD, 45);
        }
    }

    // ---------------------------------------------------------------- Master Sword (left click, M18)

    /** One cut per click (or per SWORD_INTERVAL while the button is held), a sword beam with it at full power. */
    static void swing(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now - state.lastSwing < SuitTuning.SWORD_INTERVAL || state.spinTicks > 0) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SWORD_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.lastSwing = now;
        link(player, state, MOVE_SWING);
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        boolean backhand = state.swings++ % 2 == 1;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, backhand ? SuitAnim.SWORD_V : SuitAnim.SWORD_H, 0));
        slash(player, level, SuitTuning.SWORD_DAMAGE * power(player, state));
        Vec3 look = player.getLookAngle();
        Vec3 front = player.getEyePosition().add(look.scale(1.6D)).add(0.0D, -0.3D, 0.0D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.8F, backhand ? 1.25F : 1.05F);
        if (empowered(player, state)) {
            state.swordBeams.add(new SwordBeam(player.getEyePosition().add(look.scale(0.8D)).add(0.0D, -0.2D, 0.0D), look));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.9F, 1.8F);
        }
    }

    /** One slash: every hostile in the arc in front, plus whatever is right under the crosshair. */
    private static void slash(ServerPlayer player, ServerLevel level, float damage) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = SuitTuning.SWORD_REACH;
        double cos = Math.cos(Math.toRadians(SuitTuning.SWORD_ARC_DEG / 2.0D));
        LivingEntity aimed = SuitWeapons.aim(player, reach).target();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity) && (entity == aimed || SuitWeapons.isHostile(player, entity)))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (target != aimed && (to.length() - target.getBbWidth() / 2.0D > reach || to.normalize().dot(look) < cos
                    || !player.hasLineOfSight(target))) {
                continue;
            }
            hit(player, target, damage, 0.4D, look);
        }
    }

    // ---------------------------------------------------------------- Hylian shield (Z held, M18 feedback)

    /** Z down: the shield comes up (the perfect guard is timed from this moment); Z up: it comes down. */
    static void setShield(ServerPlayer player, SuitWeapons.State state, boolean on) {
        if (state.shieldHeld == on) {
            return;
        }
        if (on && (SuitWeapons.armedClass(player) != SuitClass.HERO || SuitUpManager.isSuitingUp(player) || !player.isAlive())) {
            return;
        }
        state.shieldHeld = on;
        if (on) {
            state.shieldRaisedAt = player.level().getGameTime();
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.2F);
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.PARRY, 0));
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.4F, 0.9F);
        }
    }

    /** Held up: a slow walk behind it; it costs a little to keep up and falls when the power's gone. */
    private static void tickShieldHeld(ServerPlayer player, SuitWeapons.State state) {
        if (!state.shieldHeld) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.HYLIAN_HOLD_DRAIN)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            setShield(player, state, false);
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
        if ((player.level().getGameTime() - state.shieldRaisedAt) % 20 == 10) {
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.PARRY, 0));
        }
    }

    /** True while the hero holds the shield up (Z). */
    static boolean shieldUp(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.shieldHeld;
    }

    // ---------------------------------------------------------------- spin attack (right click held, M18 feedback)

    /** The sword drawn back, light gathering on it (the old Zelda charge). */
    static void startSpinCharge(ServerPlayer player, SuitWeapons.State state) {
        state.spinCharge = 0;
        KiFx.charge(player.serverLevel(), player, 0x9AD8FF, 0.7F, SuitTuning.GREAT_SPIN_CHARGE_TICKS, KiFx.AT_HANDS);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 2.0F);
    }

    static void tickSpinCharge(ServerPlayer player, SuitWeapons.State state) {
        state.spinCharge++;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false, false));
        if (state.spinCharge == SuitTuning.GREAT_SPIN_CHARGE_TICKS) {
            // Full: the great spin is ready.
            KiFx.burst(player.serverLevel(), hand(player), 0x9AD8FF, 0.9F, 8, KiFx.SPARK);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
        }
    }

    /** Let go: a spin if it was held long enough (the great spin at a full charge), nothing for a tap. */
    static void releaseSpin(ServerPlayer player, SuitWeapons.State state) {
        KiFx.stopCharge(player.serverLevel(), player);
        int charge = state.spinCharge;
        state.spinCharge = 0;
        if (charge < SuitTuning.SPIN_CHARGE_TICKS) {
            return;
        }
        spin(player, state, charge >= SuitTuning.GREAT_SPIN_CHARGE_TICKS);
    }

    // ---------------------------------------------------------------- spin attack at once (X)

    /**
     * X: the spin straight away, no charge (M18 3rd feedback: the bow that had taken X went back to the spin). The
     * great spin at full health, in the Triforce or on a full chain; held right click still charges one.
     */
    static void spinAttack(ServerPlayer player, SuitWeapons.State state) {
        spin(player, state, false);
    }

    // ---------------------------------------------------------------- the Triforce (V, M18)

    /** Ten seconds of the sword at full power whatever the health, and every cut half again as hard. */
    static void triforce(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.TRIFORCE_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.TRIFORCE_COOLDOWN_TICKS;
        state.triforceUntil = now + SuitTuning.TRIFORCE_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5D)).add(0.0D, -0.3D, 0.0D);
        KiFx.burst(level, hand, KiFx.GOLD, 1.4F, 16, KiFx.FLASH);
        KiFx.pillar(level, player, player.position(), KiFx.GOLD, 6.0F, 30);
        KiFx.aura(level, player, KiFx.GOLD, 45);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SPIN_ATTACK, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.triforce_on"), true);
    }

    // ---------------------------------------------------------------- the song of sleep (B, 2026-10-12)

    private static final String SLEEP_UNTIL = "FlightSuitSleepUntil";
    private static final String WAKE_UNTIL = "FlightSuitWakeUntil";
    /** The tune (Zelda's Lullaby, roughly): when each note sounds, and its pitch on the flute. */
    private static final int[] SONG_AT = {2, 7, 12, 19, 24, 29};
    private static final float[] SONG_PITCH = {1.335F, 1.587F, 1.189F, 1.335F, 1.587F, 1.189F};

    /** B: the aimed foe hears a short tune on the ocarina (a camera scene for Link) and falls asleep. */
    static void song(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (state.songTarget != null) {
            return;
        }
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.SONG_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SONG_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.SONG_COOLDOWN_TICKS;
        link(player, state, MOVE_SONG);
        SuitWeapons.sendStatus(player, state);
        state.songTarget = target;
        state.songTicks = 0;
        // Facing the one it's for, so the scene's last shot looks down the line at it.
        Vec3 to = target.position().subtract(player.position());
        float yaw = (float) (Math.atan2(-to.x, to.z) * (180.0D / Math.PI));
        player.setYRot(yaw);
        player.setYBodyRot(yaw);
        player.setYHeadRot(yaw);
        ServerLevel level = player.serverLevel();
        com.pfkfks.flightsuit.cutscene.CutsceneRunner.play(level, SuitCutscenes.HERO_SONG, player.position(), yaw,
                java.util.Map.of(), actors -> {
                }, true, java.util.List.of(player));
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.OCARINA, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.song", target.getDisplayName()), true);
    }

    private static void tickSong(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        tickSleeper(player, state);
        LivingEntity target = state.songTarget;
        if (target == null) {
            return;
        }
        if (suitClass != SuitClass.HERO || !player.isAlive()) {
            state.songTarget = null;
            return;
        }
        int t = state.songTicks++;
        ServerLevel level = player.serverLevel();
        // Playing: rooted; the foe grows drowsy.
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false, false));
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false, false));
        }
        for (int i = 0; i < SONG_AT.length; i++) {
            if (t == SONG_AT[i]) {
                Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.4D)).add(0.0D, -0.2D, 0.0D);
                level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2F, SONG_PITCH[i]);
                level.sendParticles(ParticleTypes.NOTE, mouth.x, mouth.y + 0.3D, mouth.z, 0, (SONG_PITCH[i] - 1.0F) * 2.0F, 0.0D, 0.0D, 1.0D);
                if (target.isAlive()) {
                    Vec3 head = target.getEyePosition().add(0.0D, 0.4D, 0.0D);
                    level.sendParticles(ParticleTypes.NOTE, head.x, head.y, head.z, 0, (SONG_PITCH[i] - 1.0F) * 2.0F, 0.0D, 0.0D, 1.0D);
                }
            }
        }
        if (t < SuitTuning.SONG_TICKS) {
            return;
        }
        state.songTarget = null;
        if (!target.isAlive() || target.level() != player.level() || target.distanceTo(player) > SuitTuning.SONG_RANGE + 8.0D) {
            player.displayClientMessage(Component.translatable("message.flightsuit.song_missed"), true);
            return;
        }
        sleep(level, player, state, target);
    }

    /** Asleep: held still (a boss only dozes, slowed, and not for as long) until the first blow or the time is up. */
    private static void sleep(ServerLevel level, ServerPlayer player, SuitWeapons.State state, LivingEntity target) {
        boolean boss = Stasis.isBoss(target);
        int ticks = boss ? SuitTuning.SLEEP_BOSS_TICKS : SuitTuning.SLEEP_TICKS;
        long now = level.getGameTime();
        target.getPersistentData().putLong(SLEEP_UNTIL, now + ticks);
        target.getPersistentData().remove(WAKE_UNTIL);
        if (!Stasis.hold(level, target, ticks, false)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, false));
        }
        if (target instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
        }
        state.sleeper = target;
        KiFx.aura(level, target, SLEEP_COLOUR, ticks);
        KiFx.burst(level, target.getBoundingBox().getCenter(), SLEEP_COLOUR, target.getBbWidth() + 0.8F, 14, KiFx.RING);
        level.playSound(null, target.blockPosition(), SoundEvents.FOX_SLEEP, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.flightsuit.sleeping", target.getDisplayName()), true);
    }

    private static final int SLEEP_COLOUR = 0x9AB8FF;

    /**
     * The one asleep: "Z"s off its head and a snore now and then; woken, gold rings at its feet while its
     * window is open; let go once that's shut.
     */
    private static void tickSleeper(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity sleeper = state.sleeper;
        if (sleeper == null) {
            return;
        }
        long now = player.level().getGameTime();
        net.minecraft.nbt.CompoundTag data = sleeper.getPersistentData();
        boolean asleep = data.getLong(SLEEP_UNTIL) > now;
        if (!sleeper.isAlive() || !asleep && data.getLong(WAKE_UNTIL) <= now) {
            state.sleeper = null;
            return;
        }
        if (asleep && sleeper.level() instanceof ServerLevel level) {
            // "Z"s drifting up off its head, and a snore now and then.
            if (now % 12L == 0L) {
                Vec3 head = sleeper.getEyePosition().add(0.0D, 0.35D, 0.0D);
                double side = (now / 12L) % 2L == 0L ? 1.0D : -1.0D;
                level.sendParticles(com.pfkfks.flightsuit.registry.ModParticles.SLEEP_Z.get(), head.x, head.y, head.z, 0, side, 0.0D, 0.0D, 1.0D);
            }
            if (now % 40L == 0L) {
                level.playSound(null, sleeper.blockPosition(), SoundEvents.FOX_SLEEP, SoundSource.PLAYERS, 0.8F, 0.9F);
            }
        }
        // Awake and open (user: show it on the foe, not as a count): a gold ring spreading at its feet over and
        // over while its blows land double, on top of the gold aura.
        if (!asleep && (now % 8L) == 0L && sleeper.level() instanceof ServerLevel level) {
            KiFx.burst(level, sleeper.position().add(0.0D, 0.1D, 0.0D), KiFx.GOLD, sleeper.getBbWidth() + 0.9F, 8, KiFx.RING);
        }
    }

    /**
     * A blow on a sleeper: it wakes - and from that blow on, for SONG_WAKE_TICKS, every hit on it lands twice as hard
     * (2026-10-12, user: "첫 타격 이후 3초 정도"). Any attacker's blow; burning and the like don't wake it.
     */
    static void sleepHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0.0F || event.getSource().getEntity() == null) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = victim.getPersistentData();
        long now = victim.level().getGameTime();
        if (data.getLong(SLEEP_UNTIL) > now) {
            data.remove(SLEEP_UNTIL);
            data.putLong(WAKE_UNTIL, now + SuitTuning.SONG_WAKE_TICKS);
            Stasis.release(victim);
            victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            ServerLevel level = (ServerLevel) victim.level();
            Vec3 at = victim.getBoundingBox().getCenter();
            KiFx.burst(level, at, KiFx.GOLD, victim.getBbWidth() + 1.0F, 10, KiFx.FLASH);
            KiFx.aura(level, victim, KiFx.GOLD, SuitTuning.SONG_WAKE_TICKS);
            level.playSound(null, victim.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.6F);
            if (event.getSource().getEntity() instanceof ServerPlayer hitter) {
                hitter.displayClientMessage(Component.translatable("message.flightsuit.woken"), true);
            }
        }
        if (data.getLong(WAKE_UNTIL) > now) {
            event.setAmount(event.getAmount() * SuitTuning.SONG_WAKE_MULTIPLIER);
            Vec3 at = victim.getBoundingBox().getCenter();
            KiFx.burst((ServerLevel) victim.level(), at, KiFx.GOLD, 0.6F, 5, KiFx.SPARK);
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
                hit(player, (LivingEntity) hit.getEntity(), SuitTuning.SWORD_BEAM_DAMAGE * power(player, state), 0.3D, beam.dir);
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

    /** The spin: all the way round; the great spin (a full charge, full health, the Triforce, a full chain) wider and harder. */
    static void spin(ServerPlayer player, SuitWeapons.State state, boolean charged) {
        long now = player.level().getGameTime();
        if (now < state.spinReady) {
            SuitWeapons.cooldownMessage(player, state.spinReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SPIN_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        link(player, state, MOVE_SPIN);
        boolean great = charged || empowered(player, state);
        double radius = great ? SuitTuning.GREAT_SPIN_RADIUS : SuitTuning.SPIN_RADIUS;
        float damage = (great ? SuitTuning.GREAT_SPIN_DAMAGE : SuitTuning.SPIN_DAMAGE) * power(player, state);
        state.spinReady = now + SuitTuning.SPIN_COOLDOWN_TICKS;
        // X's cooldown on the HUD is the spin's, however it was started.
        state.skill1Ready = state.spinReady;
        SuitWeapons.sendStatus(player, state);
        state.spinTicks = SPIN_SWEEP_TICKS;
        state.spinRadius = radius;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SPIN_ATTACK, 0));

        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2.0D, radius),
                entity -> entity.isAlive() && SuitWeapons.isHostile(player, entity) && !SuitWeapons.isFriendly(player, entity))) {
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
        link(player, state, MOVE_CLAW);
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
                hit(player, mob, SuitTuning.CLAW_STRIKE_DAMAGE * power(player, state), 0.6D, to.normalize());
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

    // ---------------------------------------------------------------- Hylian shield (right click held)

    /** Is the blow coming at the front the shield covers? */
    private static boolean fromTheFront(ServerPlayer player, Vec3 origin) {
        Vec3 to = origin.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        return to.lengthSqr() >= 1.0E-4D && to.normalize().dot(face) >= Math.cos(Math.toRadians(SuitTuning.HYLIAN_BLOCK_ANGLE_DEG));
    }

    /**
     * The shield held up: projectiles from the front glance off (a little power each); a melee blow from the front
     * met within PERFECT_GUARD_TICKS of raising it is a perfect guard - nothing lands, the attacker staggers and
     * the chain gains two links. Raised earlier than that, the blow still half lands (onHurt).
     */
    static void hylianShield(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || SuitWeapons.armedClass(player) != SuitClass.HERO || !shieldUp(player)) {
            return;
        }
        SuitWeapons.State state = SuitWeapons.existing(player);
        if (state == null) {
            return;
        }
        Vec3 face = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            if (event.getSource().getEntity() == null || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
                return;
            }
            Vec3 origin = event.getSource().getSourcePosition();
            if (origin == null || !fromTheFront(player, origin)) {
                return;
            }
            long now = player.level().getGameTime();
            if (now - state.shieldRaisedAt > SuitTuning.PERFECT_GUARD_TICKS) {
                // Late: onHurt takes its share.
                return;
            }
            event.setCanceled(true);
            ServerLevel level = player.serverLevel();
            Vec3 clash = player.getEyePosition().add(face.scale(0.8D)).add(0.0D, -0.3D, 0.0D);
            KiFx.burst(level, clash, KiFx.GOLD, 1.1F, 8, KiFx.FLASH);
            KiFx.burst(level, clash, KiFx.GOLD, 1.6F, 10, KiFx.RING);
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.9F);
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.distanceTo(player) < 5.0F) {
                Vec3 push = attacker.position().subtract(player.position());
                attacker.knockback(0.8D, -push.x, -push.z);
                HulkbusterArts.stun(level, attacker, SuitTuning.PERFECT_GUARD_STUN_TICKS);
            }
            link(player, state, MOVE_GUARD);
            state.chainLast = 0;
            link(player, state, MOVE_GUARD);
            player.displayClientMessage(Component.translatable("message.flightsuit.perfect_guard"), true);
            return;
        }
        if (projectile.getOwner() == player || !fromTheFront(player, projectile.position())) {
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

    /**
     * A blow that lands: Hero Mode doubles it (the hard suit); a late guard halves it (for a little power); whatever
     * gets through breaks the chain.
     */
    static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || SuitWeapons.armedClass(player) != SuitClass.HERO) {
            return;
        }
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * SuitTuning.HERO_DAMAGE_TAKEN);
        }
        SuitWeapons.State state = SuitWeapons.existing(player);
        if (state == null) {
            return;
        }
        Vec3 origin = event.getSource().getSourcePosition();
        if (shieldUp(player) && origin != null && !(event.getSource().getDirectEntity() instanceof Projectile) && fromTheFront(player, origin)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
                && SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.HYLIAN_BLOCK_COST * 2)) {
            event.setAmount(event.getAmount() * SuitTuning.HYLIAN_LATE_TAKEN);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
        if (event.getAmount() > 0.0F) {
            breakChain(player, state, true);
        }
    }
}
