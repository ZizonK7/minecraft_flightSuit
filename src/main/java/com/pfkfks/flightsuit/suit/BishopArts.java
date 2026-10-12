package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.entity.MonkeyEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Bishop Mark 7 (M18, MapleStory's Bishop - the support suit, with Doctor Strange's reactive feel): a staff in hand
 * (SuitWeaponItem).
 * <ul>
 *   <li>right click held: the staff's beam - heals the ally under the crosshair, shoots holy arrows at a foe;</li>
 *   <li>Z: Holy Magic Shell - a two-second window in which incoming shots, beams and missiles break on a shell of
 *       light, and one caught in the first moments goes straight back where it came from; since 2026-10-12 Z picks
 *       whom: left click the marked ally (three seconds for them), right click yourself;</li>
 *   <li>X: Angel Ray - a line of light that hurts foes and heals allies along it;</li>
 *   <li>C: chains of light - the aimed foe held fast (bosses only for a moment);</li>
 *   <li>V: Genesis - pillars of light fall on every foe around, every ally made whole;</li>
 *   <li>B: Heaven's Door - for a while, an ally dealt a killing blow is instead set back to the health they had
 *       three seconds before.</li>
 * </ul>
 * Allies: players, the wearer's companion suits and monkeys, villagers, their village's residents, the towns' folk
 * and heroes, the Dragon Ball fighters on our side, and tamed animals.
 */
public final class BishopArts {
    /** Heaven's Door on an entity: until when (persistent data), and its health three seconds ago (sampled). */
    private static final String DOOR_UNTIL = "FlightSuitDoorUntil";
    private static final String DOOR_OWNER = "FlightSuitDoorOwner";
    private static final String DOOR_HEALTH = "FlightSuitDoorHealth";
    private static final String DOOR_SAMPLED = "FlightSuitDoorSampled";
    /** Holy Magic Shell on an entity (2026-10-12: any ally, not only the bishop): until, raised at, whose. */
    private static final String SHELL_UNTIL = "FlightSuitShellUntil";
    private static final String SHELL_RAISED = "FlightSuitShellRaised";
    private static final String SHELL_OWNER = "FlightSuitShellOwner";
    private static final int HOLY = 0xFFF3B0;
    private static final int HEAL = 0xA8FFC0;

    private BishopArts() {
    }

    /** Someone the bishop would heal rather than hurt. */
    public static boolean isAlly(ServerPlayer bishop, LivingEntity entity) {
        if (entity == bishop) {
            return true;
        }
        if (entity instanceof Player || entity instanceof AbstractVillager || entity instanceof RemoteBodyEntity) {
            return true;
        }
        if (entity instanceof SuitCompanionEntity || entity instanceof MonkeyEntity) {
            return true;
        }
        if (entity instanceof com.pfkfks.flightsuit.village.ResidentEntity || entity instanceof com.pfkfks.flightsuit.town.TownsfolkEntity
                || entity instanceof com.pfkfks.flightsuit.hero.CityHeroEntity) {
            return true;
        }
        if (entity instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter) {
            return !fighter.getCharacter().isFoe();
        }
        if (entity instanceof com.pfkfks.flightsuit.war.RaidMember member) {
            return member.isPlayerSide();
        }
        return entity instanceof Animal animal && (animal instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame());
    }

    private static boolean isFoe(ServerPlayer bishop, LivingEntity entity) {
        return entity.isAlive() && !SuitWeapons.isFriendly(bishop, entity) && !isAlly(bishop, entity)
                && (SuitWeapons.isHostile(bishop, entity) || entity.getLastHurtByMob() == bishop) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity);
    }

    private static List<LivingEntity> alliesAround(ServerPlayer bishop, double radius) {
        return bishop.serverLevel().getEntitiesOfClass(LivingEntity.class, bishop.getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && isAlly(bishop, entity) && entity.distanceToSqr(bishop) <= radius * radius);
    }

    /** The staff's head: a little ahead of the right hand. */
    static Vec3 staff(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.8D)).add(right.scale(0.3D)).add(0.0D, 0.1D, 0.0D);
    }

    private static void heal(ServerLevel level, LivingEntity ally, float amount) {
        if (ally.getHealth() >= ally.getMaxHealth()) {
            return;
        }
        ally.heal(amount);
        Vec3 at = ally.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 3, 0.3D, 0.4D, 0.3D, 0.0D);
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        tickDoors(player);
        if (suitClass != SuitClass.BISHOP) {
            state.shellAimUntil = 0L;
            state.genesisLeft = 0;
            return;
        }
        tickGenesis(player, state);
        tickShellAim(player, state);
        long now = player.level().getGameTime();
        if (state.doorUntil > 0L && state.doorUntil <= now) {
            state.doorUntil = 0L;
            SuitWeapons.sendStatus(player, state);
        }
    }

    // ---------------------------------------------------------------- the staff (right click held)

    /** Heals the ally in the beam a couple of points every few ticks, or hurts the foe in it. */
    static void tickHoly(ServerPlayer player, SuitWeapons.State state) {
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.HOLY_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        if (state.firingTicks % SuitTuning.BEAM_HIT_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SuitWeapons.Aim aim = SuitWeapons.aim(player, SuitTuning.BEAM_RANGE, true);
        LivingEntity target = aim.target();
        if (target == null) {
            if (state.firingTicks % 10 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            return;
        }
        if (isAlly(player, target)) {
            heal(level, target, SuitTuning.HEAL_PER_HIT);
            if (state.firingTicks % 12 == 0) {
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.7F);
            }
        } else if (!SuitWeapons.isFriendly(player, target)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), SuitTuning.HOLY_ARROW_DAMAGE);
            if (target.isInvertedHealAndHarm()) {
                // The undead burn in holy light.
                target.setSecondsOnFire(2);
            }
            Vec3 look = player.getLookAngle();
            target.knockback(0.15D, -look.x, -look.z);
            if (state.firingTicks % 8 == 0) {
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.5F);
            }
        }
    }

    // ---------------------------------------------------------------- Holy Magic Shell (Z)

    /**
     * Z: picking who gets the shell (2026-10-12, user: "누르고 좌클릭하면 그 친구한테, 우클릭은 본인에게") - for a
     * few seconds the ally under the crosshair is marked (a ring at their feet and an outline); left click shells
     * them, right click the bishop. Z again puts it away.
     */
    static void shell(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (state.shellAimUntil > now) {
            state.shellAimUntil = 0L;
            player.displayClientMessage(Component.translatable("message.flightsuit.shell_aim_off"), true);
            return;
        }
        if (now < state.stepReady) {
            SuitWeapons.cooldownMessage(player, state.stepReady - now);
            return;
        }
        state.shellAimUntil = now + SuitTuning.SHELL_AIM_TICKS;
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.8F);
        player.displayClientMessage(Component.translatable("message.flightsuit.shell_aim"), true);
    }

    /** True while the bishop is picking whom to shell (the clicks are the picking, not the staff). */
    static boolean aimingShell(ServerPlayer player, SuitWeapons.State state) {
        return state.shellAimUntil > player.level().getGameTime();
    }

    /** The ally the shell would go to: the one under the crosshair (not the bishop), or null. */
    private static @Nullable LivingEntity shellTarget(ServerPlayer player) {
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.SHELL_AIM_RANGE, true).target();
        return target != null && target != player && isAlly(player, target) ? target : null;
    }

    /** While picking: the ally under the crosshair is marked. */
    private static void tickShellAim(ServerPlayer player, SuitWeapons.State state) {
        if (!aimingShell(player, state) || player.tickCount % 3 != 0) {
            return;
        }
        LivingEntity target = shellTarget(player);
        if (target == null) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 6, 0, false, false, false));
        KiFx.burst(player.serverLevel(), target.position().add(0.0D, 0.1D, 0.0D), HOLY, target.getBbWidth() + 0.8F, 4, KiFx.RING);
    }

    /** Left click while picking: the marked ally gets the shell. */
    static void shellAlly(ServerPlayer player, SuitWeapons.State state) {
        LivingEntity target = shellTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.shell_no_ally"), true);
            return;
        }
        if (raise(player, state, target, SuitTuning.SHELL_ALLY_TICKS)) {
            Vec3 from = staff(player);
            KiFx.beam(player.serverLevel(), from, target.getBoundingBox().getCenter(), HOLY, 0.25F, 8, KiFx.BEAM_THIN, null, KiFx.AT_POS, null);
            if (target instanceof ServerPlayer other) {
                other.displayClientMessage(Component.translatable("message.flightsuit.shell_given", player.getDisplayName()), true);
            }
            player.displayClientMessage(Component.translatable("message.flightsuit.shell_on", target.getDisplayName()), true);
        }
    }

    /** Right click while picking: the bishop's own shell. */
    static void shellSelf(ServerPlayer player, SuitWeapons.State state) {
        raise(player, state, player, SuitTuning.SHELL_TICKS);
    }

    private static boolean raise(ServerPlayer player, SuitWeapons.State state, LivingEntity who, int ticks) {
        long now = player.level().getGameTime();
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SHELL_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return false;
        }
        state.shellAimUntil = 0L;
        state.stepReady = now + SuitTuning.SHELL_COOLDOWN_TICKS;
        CompoundTag data = who.getPersistentData();
        data.putLong(SHELL_UNTIL, now + ticks);
        data.putLong(SHELL_RAISED, now);
        data.putUUID(SHELL_OWNER, player.getUUID());
        ServerLevel level = player.serverLevel();
        KiFx.aura(level, who, HOLY, ticks);
        KiFx.burst(level, who.position().add(0.0D, who.getBbHeight() / 2.0D, 0.0D), HOLY, Math.max(1.8F, who.getBbHeight()), ticks, KiFx.ORB);
        level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.PARRY, 0));
        return true;
    }

    /** True while a shell is up on {@code victim} (KiGuard asks before ki lands). */
    public static boolean shelled(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        return data.contains(SHELL_UNTIL) && data.getLong(SHELL_UNTIL) > victim.level().getGameTime();
    }

    /** Whose shell it is (for the shot sent back), if they're about. */
    private static @Nullable ServerPlayer shellOwner(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        if (!data.hasUUID(SHELL_OWNER) || !(victim.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getPlayerByUUID(data.getUUID(SHELL_OWNER)) instanceof ServerPlayer owner ? owner : null;
    }

    private static boolean justRaised(LivingEntity victim) {
        return victim.level().getGameTime() - victim.getPersistentData().getLong(SHELL_RAISED) <= SuitTuning.SHELL_REFLECT_TICKS;
    }

    /**
     * Ki that reaches a shell (KiGuard): broken on it - and sent back at the shooter as a blast of the bishop's own
     * when the shell was raised just now.
     */
    public static boolean shellBlocks(LivingEntity victim, LivingEntity shooter, Vec3 from) {
        if (!shelled(victim) || !(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 at = victim.position().add(0.0D, victim.getBbHeight() / 2.0D, 0.0D);
        KiFx.burst(level, at, HOLY, 1.6F, 8, KiFx.RING);
        level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.6F);
        ServerPlayer bishop = shellOwner(victim);
        if (bishop != null && justRaised(victim) && shooter != null && shooter.isAlive()) {
            Vec3 dir = shooter.getBoundingBox().getCenter().subtract(at).normalize();
            KiShots.fire(level, bishop, KiShots.Style.KI, at.add(dir.scale(0.8D)), dir, 2.0D, 40.0D, SuitTuning.ANGEL_RAY_DAMAGE, 0.8D,
                    entity -> entity == shooter || isFoe(bishop, entity));
            bishop.displayClientMessage(Component.translatable("message.flightsuit.shell_reflect"), true);
        }
        return true;
    }

    /** Arrows and the like on a shell: broken, or turned round in the first moments. */
    static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !shelled(victim)) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile) || projectile.getOwner() == victim
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) {
            return;
        }
        ServerPlayer bishop = shellOwner(victim);
        if (bishop != null && projectile.getOwner() == bishop) {
            return;
        }
        event.setCanceled(true);
        ServerLevel level = (ServerLevel) victim.level();
        Vec3 at = projectile.position();
        KiFx.burst(level, at, HOLY, 0.8F, 6, KiFx.SPARK);
        level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (bishop != null && justRaised(victim) && projectile.getOwner() instanceof LivingEntity shooter) {
            // Back the way it came, in the bishop's name.
            Vec3 dir = shooter.getBoundingBox().getCenter().subtract(at).normalize();
            projectile.setOwner(bishop);
            projectile.setDeltaMovement(dir.scale(Math.max(1.2D, projectile.getDeltaMovement().length())));
            projectile.hurtMarked = true;
            bishop.displayClientMessage(Component.translatable("message.flightsuit.shell_reflect"), true);
        } else {
            projectile.discard();
        }
    }

    // ---------------------------------------------------------------- Angel Ray (X)

    static void angelRay(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.ANGEL_RAY_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.ANGEL_RAY_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 from = staff(player);
        Vec3 dir = player.getLookAngle();
        Vec3 far = from.add(dir.scale(SuitTuning.ANGEL_RAY_RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        double length = end.distanceTo(from);
        int hurt = 0;
        int healed = 0;
        for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(SuitTuning.ANGEL_RAY_WIDTH + 1.0D),
                entity -> entity != player && entity.isAlive())) {
            Vec3 centre = who.getBoundingBox().getCenter();
            double along = centre.subtract(from).dot(dir);
            if (along < 0.0D || along > length + who.getBbWidth()) {
                continue;
            }
            double off = centre.subtract(from.add(dir.scale(along))).length();
            if (off > SuitTuning.ANGEL_RAY_WIDTH + who.getBbWidth() / 2.0D) {
                continue;
            }
            if (isAlly(player, who)) {
                heal(level, who, SuitTuning.ANGEL_RAY_HEAL);
                healed++;
            } else if (isFoe(player, who) || who.getLastHurtByMob() == player) {
                who.invulnerableTime = 0;
                who.hurt(player.damageSources().playerAttack(player), SuitTuning.ANGEL_RAY_DAMAGE);
                who.knockback(0.5D, -dir.x, -dir.z);
                hurt++;
            }
        }
        KiFx.beam(level, from, end, HOLY, 1.1F, 12, KiFx.BEAM_PLAIN, null, KiFx.AT_POS, null);
        KiFx.burst(level, end, HOLY, 1.4F, 12, KiFx.FLASH);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.BURNING_ATTACK, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.angel_ray", hurt, healed), true);
    }

    // ---------------------------------------------------------------- chains of light (C)

    static void chains(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.CHAINS_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CHAINS_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.CHAINS_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        boolean boss = Stasis.isBoss(target);
        int ticks = boss ? SuitTuning.CHAINS_BOSS_TICKS : SuitTuning.CHAINS_TICKS;
        if (!Stasis.hold(level, target, ticks, false)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
        }
        Vec3 from = staff(player);
        Vec3 at = target.getBoundingBox().getCenter();
        // Three thin chains from the staff round the target, and rings closing on it.
        for (int i = -1; i <= 1; i++) {
            Vec3 side = new Vec3(-(at.z - from.z), 0.0D, at.x - from.x).normalize().scale(i * 0.6D);
            KiFx.beam(level, from, at.add(side), HOLY, 0.18F, ticks, KiFx.BEAM_THIN, null, KiFx.AT_POS, target);
        }
        KiFx.burst(level, at, HOLY, target.getBbWidth() + 0.8F, ticks, KiFx.RING);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.translatable(boss ? "message.flightsuit.chains_boss" : "message.flightsuit.chains", target.getDisplayName()), true);
    }

    // ---------------------------------------------------------------- Genesis (V)

    static void genesis(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady || state.genesisLeft > 0) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.GENESIS_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.GENESIS_COOLDOWN_TICKS;
        state.genesisLeft = SuitTuning.GENESIS_PILLARS;
        state.genesisTicks = 0;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        // The sky opens: a flash, and every ally made whole.
        KiFx.flare(level, player, player.position().add(0.0D, 6.0D, 0.0D), 4.0F);
        KiFx.pillar(level, player, player.position(), HOLY, 24.0F, 40);
        for (LivingEntity ally : alliesAround(player, SuitTuning.GENESIS_RADIUS)) {
            ally.setHealth(ally.getMaxHealth());
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false, true));
            ally.clearFire();
            Vec3 at = ally.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 10, 0.4D, 0.5D, 0.4D, 0.0D);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.TEMPEST_THROW, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.genesis"), true);
    }

    /** One pillar every few ticks on a foe around (the nearest first), each a thump of light. */
    private static void tickGenesis(ServerPlayer player, SuitWeapons.State state) {
        if (state.genesisLeft <= 0) {
            return;
        }
        if (++state.genesisTicks % SuitTuning.GENESIS_INTERVAL != 0) {
            return;
        }
        state.genesisLeft--;
        ServerLevel level = player.serverLevel();
        List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SuitTuning.GENESIS_RADIUS), entity -> isFoe(player, entity)
                        && entity.distanceToSqr(player) <= SuitTuning.GENESIS_RADIUS * SuitTuning.GENESIS_RADIUS));
        if (foes.isEmpty()) {
            return;
        }
        foes.sort((a, b) -> Float.compare(b.getHealth(), a.getHealth()));
        LivingEntity foe = foes.get(state.genesisLeft % foes.size());
        Vec3 at = foe.position();
        foe.invulnerableTime = 0;
        foe.hurt(player.damageSources().playerAttack(player), SuitTuning.GENESIS_DAMAGE);
        if (foe.isInvertedHealAndHarm()) {
            foe.setSecondsOnFire(4);
        }
        foe.setDeltaMovement(foe.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
        foe.hurtMarked = true;
        KiFx.pillar(level, null, at, HOLY, 14.0F, 12);
        KiFx.burst(level, at.add(0.0D, foe.getBbHeight() * 0.5D, 0.0D), HOLY, 2.0F, 10, KiFx.BLAST);
        level.playSound(null, foe.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.1F);
        level.playSound(null, foe.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.8F);
    }

    // ---------------------------------------------------------------- Heaven's Door (B)

    static void heavensDoor(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.DOOR_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.DOOR_COOLDOWN_TICKS;
        state.doorUntil = now + SuitTuning.DOOR_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        int count = 0;
        for (LivingEntity ally : alliesAround(player, SuitTuning.DOOR_RADIUS)) {
            CompoundTag data = ally.getPersistentData();
            data.putLong(DOOR_UNTIL, state.doorUntil);
            data.putUUID(DOOR_OWNER, player.getUUID());
            data.putFloat(DOOR_HEALTH, ally.getHealth());
            data.putLong(DOOR_SAMPLED, now);
            KiFx.burst(level, ally.getBoundingBox().getCenter(), HEAL, ally.getBbWidth() + 0.6F, 20, KiFx.RING);
            count++;
        }
        KiFx.pillar(level, player, player.position(), HEAL, 5.0F, 30);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.PARRY, 0));
        player.displayClientMessage(Component.translatable("message.flightsuit.heavens_door", count), true);
    }

    /** Every second, the doors' allies have their health remembered three seconds back (a short ring of two samples). */
    private static void tickDoors(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        long now = player.level().getGameTime();
        for (LivingEntity ally : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(48.0D),
                entity -> entity.getPersistentData().contains(DOOR_UNTIL) && player.getUUID().equals(entity.getPersistentData().getUUID(DOOR_OWNER)))) {
            CompoundTag data = ally.getPersistentData();
            if (data.getLong(DOOR_UNTIL) <= now) {
                data.remove(DOOR_UNTIL);
                data.remove(DOOR_OWNER);
                data.remove(DOOR_HEALTH);
                data.remove(DOOR_SAMPLED);
                continue;
            }
            // The sample slides forward once it's older than the rewind.
            if (now - data.getLong(DOOR_SAMPLED) >= SuitTuning.DOOR_REWIND_TICKS) {
                data.putFloat(DOOR_HEALTH, ally.getHealth());
                data.putLong(DOOR_SAMPLED, now);
            }
        }
    }

    /** A killing blow on someone behind the door: the door takes it, and their health is as it was three seconds ago. */
    static void onHurt(LivingHurtEvent event) {
        LivingEntity who = event.getEntity();
        if (who.level().isClientSide || event.getAmount() < who.getHealth()) {
            return;
        }
        CompoundTag data = who.getPersistentData();
        if (!data.contains(DOOR_UNTIL) || data.getLong(DOOR_UNTIL) <= who.level().getGameTime()) {
            return;
        }
        float back = Math.max(data.getFloat(DOOR_HEALTH), who.getMaxHealth() * 0.3F);
        data.remove(DOOR_UNTIL);
        data.remove(DOOR_OWNER);
        data.remove(DOOR_HEALTH);
        data.remove(DOOR_SAMPLED);
        event.setCanceled(true);
        who.setHealth(back);
        who.invulnerableTime = 20;
        ServerLevel level = (ServerLevel) who.level();
        Vec3 at = who.getBoundingBox().getCenter();
        KiFx.burst(level, at, HEAL, 2.2F, 16, KiFx.FLASH);
        KiFx.pillar(level, who, who.position(), HEAL, 6.0F, 20);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 30, 0.5D, 0.6D, 0.5D, 0.3D);
        level.playSound(null, who.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
        if (who instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.flightsuit.heavens_door_saved"), true);
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof LivingEntity attacker && attacker != who) {
            Vec3 push = attacker.position().subtract(who.position());
            attacker.knockback(0.8D, -push.x, -push.z);
        }
    }
}
