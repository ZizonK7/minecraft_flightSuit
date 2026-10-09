package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stealth-class active camouflage: wearing the full stealth suit and sneaking turns you invisible (suit
 * included - see SuitArmorItem's client model hook), makes hostile mobs drop you as a target and refuse to
 * pick you up again, and drains power while it lasts.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class StealthHandler {
    private static final double SHAKE_OFF_RADIUS = 24.0D;
    private static final Set<UUID> CLOAKED = new HashSet<>();

    private StealthHandler() {
    }

    public static boolean isCloaked(ServerPlayer player) {
        return CLOAKED.contains(player.getUUID());
    }

    /** Wearing a full stealth suit (any player, either side). */
    public static boolean wearsStealthSuit(WornSuit worn) {
        return worn.fullSet() && worn.fullSetType().suitClass().canCloak();
    }

    public static void tick(ServerPlayer player, WornSuit worn) {
        boolean want = wearsStealthSuit(worn) && player.isShiftKeyDown() && !SuitUpManager.isSuitingUp(player)
                && SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CLOAK_COST);
        boolean was = CLOAKED.contains(player.getUUID());
        if (want) {
            MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
            if (current == null || current.getDuration() < 5) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, true, false, false));
            }
            if (!was || player.tickCount % 20 == 0) {
                shakeOffPursuers(player);
            }
            if (!was) {
                CLOAKED.add(player.getUUID());
                player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
        } else if (was) {
            CLOAKED.remove(player.getUUID());
            player.removeEffect(MobEffects.INVISIBILITY);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }

    private static void shakeOffPursuers(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SHAKE_OFF_RADIUS),
                mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    /** Cloaked players can't be newly targeted. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && CLOAKED.contains(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    public static void forget(UUID id) {
        CLOAKED.remove(id);
    }
}
