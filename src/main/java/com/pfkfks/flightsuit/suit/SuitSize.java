package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Big suits (the Hulkbuster, user decision "입으면 몸이 커짐"): a full set makes its wearer - player or companion
 * suit - half again as big, hitbox and eye height included, and the whole body is drawn that much bigger
 * (ClientEvents, SuitCompanionRenderer). It only grows where there's room to stand: under a low ceiling the
 * suit stays small until there is (checked every tick), so nobody suffocates suiting up indoors.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class SuitSize {
    private SuitSize() {
    }

    /** The size a full set of one suit design would give (1 = normal; a mixed or partial set is normal). */
    public static float of(LivingEntity entity) {
        SuitType type = null;
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof SuitArmorItem armor)) {
                return 1.0F;
            }
            if (type == null) {
                type = armor.getSuitType();
            } else if (type != armor.getSuitType()) {
                return 1.0F;
            }
        }
        return type == null ? 1.0F : type.size();
    }

    /** True once the entity has actually grown (there was room) - what rendering follows. */
    public static boolean grown(LivingEntity entity) {
        return entity.getBbHeight() > entity.getDimensions(entity.getPose()).height * 1.01F;
    }

    /** How big to draw the entity: its suit's size once it has grown, else 1. */
    public static float drawn(LivingEntity entity) {
        return grown(entity) ? of(entity) : 1.0F;
    }

    private static boolean wears(Object entity) {
        return entity instanceof Player || entity instanceof SuitCompanionEntity;
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        // Also fired from the entity constructor, before its equipment exists.
        if (!wears(event.getEntity()) || !event.getEntity().isAddedToWorld()) {
            return;
        }
        LivingEntity living = (LivingEntity) event.getEntity();
        float size = of(living);
        if (size == 1.0F) {
            return;
        }
        EntityDimensions big = event.getNewSize().scale(size);
        if (!living.level().noCollision(living, big.makeBoundingBox(living.position()).deflate(1.0E-7D))) {
            return;
        }
        event.setNewSize(big);
        event.setNewEyeHeight(event.getNewEyeHeight() * size);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            check(event.player);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof SuitCompanionEntity companion) {
            check(companion);
        }
    }

    /** Grows or shrinks as soon as the set is completed or broken up (or a low ceiling is left behind). */
    private static void check(LivingEntity entity) {
        if (grown(entity) != of(entity) > 1.0F) {
            entity.refreshDimensions();
        }
    }
}
