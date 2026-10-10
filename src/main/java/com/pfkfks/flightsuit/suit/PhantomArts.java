package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Mark 3's new tricks (M17):
 * - stealing (Z held): cards fly round the aimed foe and back; if it used a skill lately (StolenSkill), that skill
 *   is now the phantom's one stolen card - kept for good (it survives logging out and dying), replaced by the next;
 * - using it (B): the stolen skill, as its owner would (charge and all);
 * - Tempest (V, the ultimate): a card thrown up, the sky over the aimed spot turns purple, and for four seconds
 *   cards rain down there - hurting, slowing and grounding everything under them.
 */
public final class PhantomArts {
    private static final String STOLEN_KEY = "flightsuit_stolen_skill";

    private PhantomArts() {
    }

    // ---------------------------------------------------------------- the stolen card, kept for good

    private static CompoundTag kept(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static @Nullable StolenSkill stolen(Player player) {
        return StolenSkill.byName(kept(player).getString(STOLEN_KEY));
    }

    private static void keep(Player player, StolenSkill skill) {
        kept(player).putString(STOLEN_KEY, skill.name());
    }

    /** The stolen card comes back with you after death (Forge copies the persisted tag; this makes sure). */
    public static void onClone(PlayerEvent.Clone event) {
        StolenSkill skill = stolen(event.getOriginal());
        if (skill != null) {
            keep(event.getEntity(), skill);
        }
    }

    // ---------------------------------------------------------------- stealing (Z held)

    static void steal(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (state.stealTicks > 0) {
            return;
        }
        if (now < state.stealReady) {
            SuitWeapons.cooldownMessage(player, state.stealReady - now);
            return;
        }
        LivingEntity target = SuitWeapons.aim(player, SuitTuning.STEAL_RANGE).target();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.STEAL_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.stealTarget = target;
        state.stealTicks = SuitTuning.STEAL_TICKS;
        ServerLevel level = player.serverLevel();
        // Three cards out to it...
        Vec3 hand = SwordArts.hands(player);
        Vec3 velocity = target.getBoundingBox().getCenter().subtract(hand).scale(1.0D / ModParticles.CARD_FLIGHT_LIFE);
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ModParticles.CARD_FLIGHT.get(), hand.x, hand.y + (i - 1) * 0.15D, hand.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
        // ...wrapping round it.
        SuitSkills.cardSwirl(level, target.position(), Math.max(0.7F, target.getBbWidth()), SuitTuning.STEAL_TICKS);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** What {@code target} has done lately that could be stolen, or null. */
    private static @Nullable StolenSkill seen(LivingEntity target, long now) {
        if (target instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter) {
            return fighter.recentSkill(now);
        }
        return StolenSkill.ofMob(target);
    }

    private static void tickSteal(ServerPlayer player, SuitWeapons.State state) {
        if (state.stealTicks <= 0) {
            return;
        }
        LivingEntity target = state.stealTarget;
        ServerLevel level = player.serverLevel();
        if (target != null && target.isAlive() && state.stealTicks % 3 == 0) {
            Vec3 at = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 4, 0.4D, 0.4D, 0.4D, 0.05D);
        }
        if (--state.stealTicks > 0) {
            return;
        }
        state.stealTarget = null;
        if (target == null || !target.isAlive()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.steal_nothing"), true);
            return;
        }
        long now = level.getGameTime();
        StolenSkill skill = seen(target, now);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.steal_nothing"), true);
            return;
        }
        if (target instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter && !fighter.allowSteal(skill)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.steal_again", skill.title()), true);
            return;
        }
        StolenSkill before = stolen(player);
        keep(player, skill);
        state.stealReady = now + SuitTuning.STEAL_COOLDOWN_TICKS;
        state.stolenReady = 0L;
        SuitWeapons.sendStatus(player, state);
        // The cards come home carrying it.
        SuitSkills.cardBurst(level, player.position());
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.sendSystemMessage(before == null || before == skill
                ? Component.translatable("message.flightsuit.stolen", skill.title())
                : Component.translatable("message.flightsuit.stolen_over", before.title(), skill.title()));
    }

    // ---------------------------------------------------------------- using it (B)

    static void useStolen(ServerPlayer player, SuitWeapons.State state) {
        StolenSkill skill = stolen(player);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.stolen_empty"), true);
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.stolenReady || state.chargingSkill != null) {
            SuitWeapons.cooldownMessage(player, state.stolenReady - now);
            return;
        }
        if (!player.getAbilities().instabuild && SuitEnergy.available(player, EquipmentSlot.CHEST) < skill.cost()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        if (skill.chargeTicks() > 0) {
            SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, skill.cost());
            state.stolenReady = now + skill.cooldownTicks();
            state.chargingSkill = skill;
            state.skillCharge = 0;
            SuitWeapons.sendStatus(player, state);
            return;
        }
        if (release(player, skill)) {
            SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, skill.cost());
            state.stolenReady = now + skill.cooldownTicks();
            SuitWeapons.sendStatus(player, state);
        } else {
            player.displayClientMessage(Component.translatable(skill == StolenSkill.INSTANT_TRANSMISSION || skill == StolenSkill.WITHER_SLASH
                    ? "message.flightsuit.no_target" : "message.flightsuit.shadow_step_blocked"), true);
        }
    }

    private static boolean release(ServerPlayer player, StolenSkill skill) {
        ServerLevel level = player.serverLevel();
        LivingEntity aimed = SuitWeapons.aim(player, skill == StolenSkill.INSTANT_TRANSMISSION ? 128.0D : 32.0D).target();
        Vec3 from = skill == StolenSkill.SPECIAL_BEAM_CANNON ? player.getEyePosition().add(player.getLookAngle().scale(0.6D))
                : SwordArts.hands(player);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return skill.cast(level, player, from, player.getLookAngle(), aimed, skill.damage(), hits(player));
    }

    private static Predicate<LivingEntity> hits(ServerPlayer player) {
        return entity -> !SuitWeapons.isFriendly(player, entity) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity);
    }

    private static void tickCharge(ServerPlayer player, SuitWeapons.State state) {
        StolenSkill skill = state.chargingSkill;
        if (skill == null) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
        skill.chargeEffect(player.serverLevel(), player, state.skillCharge);
        if (++state.skillCharge >= skill.chargeTicks()) {
            state.chargingSkill = null;
            release(player, skill);
        }
    }

    // ---------------------------------------------------------------- Tempest (V)

    static void tempest(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady || TempestRain.isRaining(player)) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.TEMPEST_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.TEMPEST_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        SuitWeapons.Aim aim = SuitWeapons.aim(player, SuitTuning.TEMPEST_RANGE);
        Vec3 at = aim.end();
        if (aim.target() == null && at.distanceTo(player.getEyePosition()) >= SuitTuning.TEMPEST_RANGE - 0.5D) {
            // Aimed at nothing: ten blocks in front.
            Vec3 flat = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            flat = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
            at = player.position().add(flat.scale(10.0D));
        }
        ServerLevel level = player.serverLevel();
        TempestRain.start(level, player, ground(level, at), entity -> !SuitWeapons.isFriendly(player, entity)
                && (entity instanceof Enemy || entity == player.getLastHurtMob()) && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity));
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.TEMPEST_THROW, 0));
        // One card thrown up into the sky.
        Vec3 hand = player.getEyePosition().add(0.0D, 0.4D, 0.0D);
        level.sendParticles(ModParticles.CARD_FLIGHT.get(), hand.x, hand.y, hand.z, 0, 0.0D, 1.4D, 0.0D, 1.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 1.2F);
        player.displayClientMessage(Component.translatable("message.flightsuit.tempest"), true);
    }

    /** The ground under {@code at} (or {@code at} itself if there's none within a dozen blocks). */
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 12; i++, pos = pos.below()) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return new Vec3(at.x, pos.getY(), at.z);
            }
        }
        return at;
    }

    /** The companion phantom's Tempest: the same rain round {@code at}, hurting what {@code hits} allows. */
    public static void companionTempest(ServerLevel level, LivingEntity caster, Vec3 at, Predicate<LivingEntity> hits) {
        TempestRain.start(level, caster, ground(level, at), hits);
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        if (suitClass != SuitClass.PHANTOM || !player.isAlive()) {
            state.stealTicks = 0;
            state.stealTarget = null;
            state.chargingSkill = null;
            return;
        }
        tickSteal(player, state);
        tickCharge(player, state);
    }
}
