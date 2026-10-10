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
        if (suitClass != SuitClass.HULKBUSTER || !player.isAlive()) {
            state.pistonTarget = null;
            state.pistonHits = 0;
            state.slamPhase = SLAM_NONE;
            return;
        }
        tickPiston(player, state);
        tickSlam(player, state);
        if (player.tickCount % 2 == 0 && wearsFullSet(player)) {
            shoveSmallMobs(player);
        }
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
                && (entity == aimed || entity instanceof Enemy && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity)), state);
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
                entity -> !SuitWeapons.isFriendly(player, entity) && (entity instanceof Enemy || entity == player.getLastHurtMob())
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
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isPounding(player)) {
            event.setAmount(event.getAmount() * SuitTuning.PISTON_DAMAGE_TAKEN);
        }
        if (event.getSource().getDirectEntity() instanceof Projectile && wearsFullSet(player)) {
            event.setAmount(event.getAmount() * SuitTuning.HULKBUSTER_PROJECTILE_TAKEN);
        }
    }
}
