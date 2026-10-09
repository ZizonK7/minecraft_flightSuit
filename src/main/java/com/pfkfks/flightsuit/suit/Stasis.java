package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Holds a mob stock-still for a while: no AI (so no moving, no attacking), but it can still be hit. Used by
 * Mark 2's cryo beam (frozen solid, frost and shivering) and by the phantom's card duel (the opponent waits
 * at the table). The hold is stored on the mob itself, so it survives chunk unloads and always lets go.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class Stasis {
    private static final String UNTIL = "flightsuit_stasis_until";
    private static final String FROST = "flightsuit_stasis_frost";
    private static final String HAD_NO_AI = "flightsuit_stasis_noai";
    /** After thawing, a while before the cryo beam can freeze the same mob again. */
    private static final String IMMUNE_UNTIL = "flightsuit_stasis_immune";
    private static final int REFREEZE_IMMUNITY_TICKS = 40;

    private Stasis() {
    }

    /** Wither, dragon and the like: too big to freeze, and they take a share of their health instead of dying. */
    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof ElderGuardian
                || entity instanceof Warden || entity.getMaxHealth() >= 100.0F;
    }

    public static boolean isHeld(LivingEntity entity) {
        return entity.getPersistentData().contains(UNTIL);
    }

    /**
     * @param frost frozen by the cryo beam (frost, shivering, refreeze immunity after) rather than a plain hold
     * @return whether it took hold - players, bosses, mobs already held or freshly thawed are left alone
     */
    public static boolean hold(ServerLevel level, LivingEntity target, int ticks, boolean frost) {
        CompoundTag data = target.getPersistentData();
        if (!(target instanceof Mob mob) || target instanceof Player || isBoss(target) || isHeld(target)
                || (frost && level.getGameTime() < data.getLong(IMMUNE_UNTIL))) {
            return false;
        }
        data.putLong(UNTIL, level.getGameTime() + ticks);
        data.putBoolean(FROST, frost);
        data.putBoolean(HAD_NO_AI, mob.isNoAi());
        mob.setNoAi(true);
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0D, Math.min(0.0D, mob.getDeltaMovement().y), 0.0D);
        if (frost) {
            mob.setTicksFrozen(mob.getTicksRequiredToFreeze() + ticks);
            level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0D, mob.getZ(), 40,
                    mob.getBbWidth() / 2.0D, mob.getBbHeight() / 2.0D, mob.getBbWidth() / 2.0D, 0.05D);
            level.playSound(null, mob.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, mob.blockPosition(), SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        return true;
    }

    /** Lets go now (the duel's over). */
    public static void release(LivingEntity target) {
        if (isHeld(target)) {
            thaw(target);
        }
    }

    private static void thaw(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        boolean frost = data.getBoolean(FROST);
        if (target instanceof Mob mob) {
            mob.setNoAi(data.getBoolean(HAD_NO_AI));
        }
        data.remove(UNTIL);
        data.remove(FROST);
        data.remove(HAD_NO_AI);
        if (frost && target.level() instanceof ServerLevel level) {
            data.putLong(IMMUNE_UNTIL, level.getGameTime() + REFREEZE_IMMUNITY_TICKS);
            target.setTicksFrozen(0);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(), 20,
                    target.getBbWidth() / 2.0D, target.getBbHeight() / 2.0D, target.getBbWidth() / 2.0D, 0.1D);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !isHeld(entity)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (level.getGameTime() >= data.getLong(UNTIL)) {
            thaw(entity);
            return;
        }
        if (data.getBoolean(FROST)) {
            entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + 2));
            if (entity.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0D, entity.getZ(), 2,
                        entity.getBbWidth() / 2.0D, entity.getBbHeight() / 2.0D, entity.getBbWidth() / 2.0D, 0.0D);
            }
        }
    }
}
