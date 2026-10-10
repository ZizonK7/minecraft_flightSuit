package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.network.EntityFxS2CPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Mark 5, Trunks (M17): a swordsman who fights on the wing - unlike the hero (Mark 4: on the ground, shield,
 * sword beams), Trunks cuts in the air, with ki, and through what's fired at him.
 * - three-step combo (hold right click): a cut across, a cut down, a thrust, and round again; all three on the
 *   same foe stun it; works flying;
 * - Burning Attack (X): arms crossed, then thrust out - a yellow ball of ki that bursts on what it meets;
 * - flash slash (C): a rush along the look, cutting everything on the way, untouchable while it lasts;
 * - sword parry (Z tap): the counter's parry with the sword; a parry makes the next cut land twice as hard;
 * - cutting projectiles (passive): while he cuts, arrows and ki blasts coming at the front are cut out of the air;
 * - Super Saiyan (V, toggle): golden hair and aura; everything he does lands half again as hard, he moves faster,
 *   the parry opens longer - and the battery runs down fast.
 */
public final class SwordArts {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.4F);
    private static final DustParticleOptions GOLD_SMALL = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.5F), 0.8F);
    private static final DustParticleOptions BLUE_TRAIL = new DustParticleOptions(new Vector3f(0.55F, 0.75F, 1.0F), 1.1F);
    private static final UUID SSJ_SPEED_ID = UUID.fromString("4c2d1a8e-7b3f-4f6e-9a21-5d8c3e0b7f11");
    /** Combo steps: across, down, thrust. */
    public static final int CUT_ACROSS = 0;
    public static final int CUT_DOWN = 1;
    public static final int THRUST = 2;

    /** Client side: entity ids glowing gold right now (EntityFxS2CPacket), for the golden hair texture. */
    public static final Set<Integer> GOLDEN = ConcurrentHashMap.newKeySet();

    private SwordArts() {
    }

    /** The damage multiplier Super Saiyan gives (1 when off). */
    static float power(SuitWeapons.State state) {
        return state.superSaiyan ? SuitTuning.SSJ_MULTIPLIER : 1.0F;
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (suitClass != SuitClass.SWORDSMAN || !player.isAlive()) {
            state.burningWindup = 0;
            state.flashTicks = 0;
            if (state.superSaiyan) {
                setSuperSaiyan(player, state, false);
            }
            return;
        }
        tickBurning(player, state);
        tickFlash(player, state);
        tickSuperSaiyan(player, state);
        if (state.firing == SuitWeapons.FIRE_SLASH) {
            cutProjectiles(player);
        }
    }

    // ---------------------------------------------------------------- three-step combo (hold)

    static void tickCombo(ServerPlayer player, SuitWeapons.State state) {
        if (state.firingTicks % SuitTuning.SLASH_INTERVAL != 1 || state.flashTicks > 0 || state.burningWindup > 0) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SLASH_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        long now = player.level().getGameTime();
        if (now - state.lastCut > SuitTuning.SLASH_INTERVAL * 3L) {
            // A pause breaks the combo: start over with the cut across.
            state.comboStep = CUT_ACROSS;
        }
        int step = state.comboStep;
        state.comboStep = (step + 1) % 3;
        state.lastCut = now;
        float multiplier = power(state);
        if (state.parryBonus) {
            multiplier *= SuitTuning.PARRY_BONUS;
            state.parryBonus = false;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player,
                step == CUT_ACROSS ? SuitAnim.SWORD_H : step == CUT_DOWN ? SuitAnim.SWORD_V : SuitAnim.SWORD_THRUST, 0));
        ServerLevel level = player.serverLevel();
        LivingEntity aimed = SuitWeapons.aim(player, SuitTuning.THRUST_REACH).target();
        Set<LivingEntity> hit = cut(level, player, player.getLookAngle(), step, multiplier,
                entity -> !SuitWeapons.isFriendly(player, entity) && (entity == aimed || entity instanceof Enemy
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)));
        // The same foe through all three steps: stunned.
        if (step == CUT_ACROSS) {
            state.comboHit.clear();
            state.comboHit.addAll(hit);
        } else {
            state.comboHit.retainAll(hit);
            if (step == THRUST) {
                for (LivingEntity target : state.comboHit) {
                    HulkbusterArts.stun(level, target, SuitTuning.COMBO_STUN_TICKS);
                }
                state.comboHit.clear();
            }
        }
    }

    /**
     * One step of the combo from {@code attacker} looking along {@code look}: the cut across (wide, flat), the cut
     * down (narrow, tall), the thrust (a narrow line, a block farther). Returns who it hit.
     */
    public static Set<LivingEntity> cut(ServerLevel level, LivingEntity attacker, Vec3 look, int step, float multiplier,
                                        Predicate<LivingEntity> hits) {
        Vec3 eye = attacker.getEyePosition();
        double reach = step == THRUST ? SuitTuning.THRUST_REACH : SuitTuning.SLASH_REACH;
        double halfYaw = step == CUT_ACROSS ? 55.0D : step == CUT_DOWN ? 20.0D : 10.0D;
        double halfPitch = step == CUT_ACROSS ? 30.0D : step == CUT_DOWN ? 70.0D : 12.0D;
        float damage = (step == THRUST ? SuitTuning.THRUST_DAMAGE : SuitTuning.SLASH_DAMAGE) * multiplier;
        double lookYaw = Math.toDegrees(Math.atan2(look.z, look.x));
        double lookPitch = Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, look.normalize().y))));
        Set<LivingEntity> hit = new HashSet<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(reach + 1.0D),
                entity -> entity != attacker && entity.isAlive() && hits.test(entity))) {
            AABB box = target.getBoundingBox();
            Vec3 centre = box.getCenter();
            Vec3 to = centre.subtract(eye);
            double distance = to.length();
            double slack = target.getBbWidth() / 2.0D;
            if (distance - slack > reach) {
                continue;
            }
            if (distance > slack + 0.5D) {
                // Angles, with some give for the target's size.
                double give = Math.toDegrees(Math.atan2(slack + 0.3D, distance));
                double yaw = Math.toDegrees(Math.atan2(to.z, to.x));
                double pitch = Math.toDegrees(Math.asin(to.y / distance));
                double dYaw = Math.abs(((yaw - lookYaw) % 360.0D + 540.0D) % 360.0D - 180.0D);
                double dPitch = Math.abs(pitch - lookPitch);
                if (dYaw > halfYaw + give || dPitch > halfPitch + give + Math.toDegrees(Math.atan2(target.getBbHeight() / 2.0D, distance))) {
                    continue;
                }
            }
            target.invulnerableTime = 0;
            if (target.hurt(attacker instanceof ServerPlayer player ? attacker.damageSources().playerAttack(player)
                    : attacker.damageSources().mobAttack(attacker), damage)) {
                target.knockback(step == THRUST ? 0.7D : 0.35D, -look.x, -look.z);
                hit.add(target);
            }
            level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 6, 0.2D, 0.2D, 0.2D, 0.3D);
        }
        drawCut(level, eye, look, step, multiplier > 1.01F);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                step == THRUST ? SoundEvents.TRIDENT_THROW : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F,
                step == CUT_ACROSS ? 1.1F : step == CUT_DOWN ? 1.3F : 1.5F);
        return hit;
    }

    /** The cut's arc in the air: across, down, or a line straight out. */
    private static void drawCut(ServerLevel level, Vec3 eye, Vec3 look, int step, boolean golden) {
        Vec3 dir = look.normalize();
        Vec3 side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        DustParticleOptions dust = golden ? GOLD_SMALL : BLUE_TRAIL;
        if (step == THRUST) {
            for (int i = 2; i <= 10; i++) {
                Vec3 at = eye.add(dir.scale(i * 0.5D)).add(0.0D, -0.3D, 0.0D);
                level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            Vec3 tip = eye.add(dir.scale(SuitTuning.THRUST_REACH)).add(0.0D, -0.3D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 4, 0.05D, 0.05D, 0.05D, 0.2D);
            return;
        }
        Vec3 axis = step == CUT_ACROSS ? side : up;
        for (int k = -6; k <= 6; k++) {
            double t = k / 6.0D;
            Vec3 at = eye.add(dir.scale(2.2D - t * t * 0.8D)).add(axis.scale(t * 2.0D)).add(0.0D, -0.3D, 0.0D);
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        Vec3 mid = eye.add(dir.scale(2.0D)).add(0.0D, -0.3D, 0.0D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    // ---------------------------------------------------------------- cutting projectiles (passive)

    private static boolean inFront(LivingEntity wearer, Vec3 point, double reach) {
        Vec3 to = point.subtract(wearer.getEyePosition());
        if (to.length() > reach) {
            return false;
        }
        Vec3 flat = to.multiply(1.0D, 0.0D, 1.0D);
        Vec3 facing = Vec3.directionFromRotation(0.0F, wearer.getYRot());
        return flat.lengthSqr() < 1.0E-4D || flat.normalize().dot(facing) >= Math.cos(Math.toRadians(55.0D));
    }

    /** While he cuts: arrows and other projectiles flying in at the front are cut out of the air. */
    private static void cutProjectiles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(SuitTuning.SLASH_REACH),
                p -> p.isAlive() && (p.getOwner() == null || !SuitWeapons.isFriendly(player, p.getOwner())))) {
            if (!inFront(player, projectile.position(), SuitTuning.SLASH_REACH + 0.5D)) {
                continue;
            }
            Vec3 at = projectile.position();
            projectile.discard();
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1D, 0.1D, 0.1D, 0.2D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }

    /**
     * A Dragon Ball fighter's ki blast coming at {@code victim} from {@code from}: true if Trunks is cutting and it
     * comes at his front - the blast is cut in two and does nothing (DbzFighterEntity asks before it hurts).
     */
    public static boolean cutsKi(LivingEntity victim, Vec3 from) {
        if (!(victim instanceof ServerPlayer player) || SuitWeapons.armedClass(player) != SuitClass.SWORDSMAN) {
            return false;
        }
        SuitWeapons.State state = SuitWeapons.existing(player);
        if (state == null || state.firing != SuitWeapons.FIRE_SLASH) {
            return false;
        }
        Vec3 flat = from.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 facing = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(facing) < Math.cos(Math.toRadians(55.0D))) {
            return false;
        }
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.5D));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.5F);
        return true;
    }

    // ---------------------------------------------------------------- Burning Attack (X)

    static void burningAttack(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready || state.burningWindup > 0) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.BURNING_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.BURNING_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.burningWindup = SuitTuning.BURNING_WINDUP_TICKS;
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.BURNING_ATTACK, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable("message.flightsuit.burning_attack"), true);
    }

    private static void tickBurning(ServerPlayer player, SuitWeapons.State state) {
        if (state.burningWindup <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 hands = hands(player);
        level.sendParticles(GOLD, hands.x, hands.y, hands.z, 3, 0.15D, 0.15D, 0.15D, 0.0D);
        if (--state.burningWindup > 0) {
            return;
        }
        KiShots.fire(level, player, KiShots.Style.BURNING, hands, player.getLookAngle(), SuitTuning.BURNING_SPEED, SuitTuning.BURNING_RANGE,
                SuitTuning.BURNING_DAMAGE * power(state), SuitTuning.BURNING_RADIUS, entity -> !SuitWeapons.isFriendly(player, entity));
    }

    /** Both hands thrust out in front. */
    public static Vec3 hands(LivingEntity wearer) {
        return wearer.getEyePosition().add(wearer.getLookAngle().scale(1.0D)).add(0.0D, -0.35D, 0.0D);
    }

    // ---------------------------------------------------------------- flash slash (C)

    static void flashSlash(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready || state.flashTicks > 0) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FLASH_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.FLASH_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.flashTicks = SuitTuning.FLASH_TICKS;
        state.flashDir = player.getLookAngle().normalize();
        state.flashFrom = player.position();
        state.flashLast = player.position();
        state.flashHit.clear();
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SWORD_H, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /** True while the flash slash is rushing (nothing touches him). */
    public static boolean isFlashing(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.flashTicks > 0;
    }

    private static void tickFlash(ServerPlayer player, SuitWeapons.State state) {
        if (state.flashTicks <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 now = player.position();
        sweepPath(level, player, state, state.flashLast, now);
        // Afterimages along the way: five ghost cuts.
        for (int i = 0; i < 3; i++) {
            Vec3 at = state.flashLast.lerp(now, i / 3.0D).add(0.0D, 1.0D, 0.0D);
            level.sendParticles(state.superSaiyan ? GOLD : BLUE_TRAIL, at.x, at.y, at.z, 4, 0.2D, 0.5D, 0.2D, 0.0D);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        boolean blocked = state.flashTicks < SuitTuning.FLASH_TICKS && now.distanceTo(state.flashLast) < 0.3D;
        state.flashLast = now;
        state.flashTicks--;
        double travelled = now.distanceTo(state.flashFrom);
        if (state.flashTicks <= 0 || blocked || travelled >= SuitTuning.FLASH_DISTANCE) {
            state.flashTicks = 0;
            sweepPath(level, player, state, state.flashFrom, now);
            player.setDeltaMovement(state.flashDir.scale(0.2D));
            player.hurtMarked = true;
            ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.SWORD_SHEATHE, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 1.6F);
            return;
        }
        double speed = SuitTuning.FLASH_DISTANCE / SuitTuning.FLASH_TICKS;
        player.setDeltaMovement(state.flashDir.scale(speed));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
    }

    /** Cuts everything (once per rush) within FLASH_WIDTH of the stretch {@code from}-{@code to}. */
    private static void sweepPath(ServerLevel level, ServerPlayer player, SuitWeapons.State state, Vec3 from, Vec3 to) {
        Vec3 mid = new Vec3(0.0D, player.getBbHeight() / 2.0D, 0.0D);
        Vec3 a = from.add(mid);
        Vec3 b = to.add(mid);
        Vec3 seg = b.subtract(a);
        double len = seg.length();
        Vec3 unit = len < 1.0E-4D ? state.flashDir : seg.scale(1.0D / len);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(SuitTuning.FLASH_WIDTH + 1.0D),
                entity -> entity != player && entity.isAlive() && !SuitWeapons.isFriendly(player, entity)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity) && !state.flashHit.contains(entity))) {
            Vec3 centre = target.getBoundingBox().getCenter();
            double along = Math.max(0.0D, Math.min(len, centre.subtract(a).dot(unit)));
            if (a.add(unit.scale(along)).distanceTo(centre) > SuitTuning.FLASH_WIDTH + target.getBbWidth() / 2.0D) {
                continue;
            }
            state.flashHit.add(target);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), SuitTuning.FLASH_DAMAGE * power(state));
            for (int i = 0; i < 5; i++) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y + (i - 2) * 0.3D, centre.z, 1, 0.3D, 0.1D, 0.3D, 0.0D);
            }
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    // ---------------------------------------------------------------- sword parry

    /** The parry window: longer as a Super Saiyan. */
    static int parryWindow(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.superSaiyan ? SuitTuning.SSJ_PARRY_WINDOW_TICKS : SuitTuning.PARRY_WINDOW_TICKS;
    }

    /** Parried with the sword: the next cut lands harder. */
    static void onParried(ServerPlayer player) {
        SuitWeapons.state(player).parryBonus = true;
    }

    // ---------------------------------------------------------------- Super Saiyan (V)

    static void toggleSuperSaiyan(ServerPlayer player, SuitWeapons.State state) {
        if (state.superSaiyan) {
            setSuperSaiyan(player, state, false);
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.ssjLockedUntil) {
            SuitWeapons.cooldownMessage(player, state.ssjLockedUntil - now);
            return;
        }
        if (!player.getAbilities().instabuild && SuitEnergy.available(player, EquipmentSlot.CHEST)
                < SuitTuning.SSJ_COST + SuitEnergy.capacity(SuitEnergy.source(player, EquipmentSlot.CHEST)) * SuitTuning.SSJ_MIN_CHARGE) {
            player.displayClientMessage(Component.translatable("message.flightsuit.ssj_low"), true);
            return;
        }
        SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SSJ_COST);
        setSuperSaiyan(player, state, true);
    }

    static void setSuperSaiyan(ServerPlayer player, SuitWeapons.State state, boolean on) {
        if (state.superSaiyan == on) {
            return;
        }
        state.superSaiyan = on;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SSJ_SPEED_ID);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(SSJ_SPEED_ID, "Super Saiyan", SuitTuning.SSJ_MULTIPLIER - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        ModNetwork.sendToTrackingAndSelf(player, new EntityFxS2CPacket(player.getId(), EntityFxS2CPacket.GOLDEN, on));
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        if (on) {
            transformBurst(level, player);
            player.displayClientMessage(Component.translatable("message.flightsuit.ssj_on"), true);
        } else {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.4D, 0.6D, 0.4D, 0.05D);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.flightsuit.ssj_off"), true);
        }
    }

    /** The moment of turning: a pillar of light, wind blown out, a roar. */
    public static void transformBurst(ServerLevel level, Entity who) {
        transformBurst(level, who, com.pfkfks.flightsuit.fx.KiFx.GOLD);
    }

    /** A pillar of light in {@code colour} (drawn by the clients, KiFx) and dust blown out along the ground. */
    public static void transformBurst(ServerLevel level, Entity who, int colour) {
        com.pfkfks.flightsuit.fx.KiFx.pillar(level, who, who.position(), colour, 26.0F, 34);
        com.pfkfks.flightsuit.fx.KiFx.burst(level, who.getBoundingBox().getCenter(), colour, 2.0F, 10, com.pfkfks.flightsuit.fx.KiFx.FLASH);
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10.0D;
            level.sendParticles(ParticleTypes.CLOUD, who.getX() + Math.cos(angle) * 0.8D, who.getY() + 0.2D, who.getZ() + Math.sin(angle) * 0.8D,
                    0, Math.cos(angle), 0.05D, Math.sin(angle), 0.6D);
        }
        level.playSound(null, who.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, who.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    /**
     * Every other tick while golden: a spark now and then (the aura itself the clients draw round whoever's golden -
     * KiFxClient - so it's there in third person and for everyone else).
     */
    public static void aura(ServerLevel level, Entity who) {
        double h = who.getBbHeight();
        if (who.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, who.getX(), who.getY() + h * 0.85D, who.getZ(), 1, 0.4D, 0.2D, 0.4D, 0.02D);
        }
    }

    private static void tickSuperSaiyan(ServerPlayer player, SuitWeapons.State state) {
        if (!state.superSaiyan) {
            return;
        }
        int capacity = SuitEnergy.capacity(SuitEnergy.source(player, EquipmentSlot.CHEST));
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SSJ_DRAIN)
                || !player.getAbilities().instabuild && SuitEnergy.available(player, EquipmentSlot.CHEST) < capacity * SuitTuning.SSJ_MIN_CHARGE) {
            setSuperSaiyan(player, state, false);
            state.ssjLockedUntil = player.level().getGameTime() + SuitTuning.SSJ_LOCK_TICKS;
            SuitWeapons.sendStatus(player, state);
            player.displayClientMessage(Component.translatable("message.flightsuit.ssj_drained"), true);
            return;
        }
        if (player.tickCount % 2 == 0) {
            aura(player.serverLevel(), player);
        }
    }

    // ---------------------------------------------------------------- damage hooks

    /** Nothing touches him in the middle of the flash slash. */
    static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFlashing(player)) {
            event.setCanceled(true);
        }
    }
}
