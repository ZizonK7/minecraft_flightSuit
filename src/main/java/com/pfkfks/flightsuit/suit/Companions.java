package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Owner-side commands for companion suits (G tap/hold, H). */
public final class Companions {
    /** How far commands reach. */
    private static final double COMMAND_RANGE = 96.0D;

    private Companions() {
    }

    public static List<SuitCompanionEntity> owned(ServerPlayer player, double range) {
        return player.serverLevel().getEntitiesOfClass(SuitCompanionEntity.class,
                player.getBoundingBox().inflate(range), suit -> suit.isAlive() && suit.isOwnedBy(player));
    }

    /** Nearest powered, idle companion to board (null if none in range). */
    public static SuitCompanionEntity nearest(ServerPlayer player, double range) {
        SuitCompanionEntity best = null;
        for (SuitCompanionEntity suit : owned(player, range)) {
            if (suit.isPowered() && !suit.isBusy() && (best == null || suit.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = suit;
            }
        }
        if (best != null) {
            return best;
        }
        // A powered-down suit can still be boarded if you walk right up to it.
        for (SuitCompanionEntity suit : owned(player, 3.0D)) {
            if (!suit.isPowered()) {
                return suit;
            }
        }
        return null;
    }

    /** G hold: every companion in range flies home. */
    public static int recallAll(ServerPlayer player) {
        int count = 0;
        for (SuitCompanionEntity suit : owned(player, COMMAND_RANGE)) {
            if (suit.isPowered()) {
                suit.goHome();
                count++;
            }
        }
        return count;
    }

    /** H: everyone attacks what the owner is aiming at; aiming at nothing calls them off. */
    public static void commandAttack(ServerPlayer player) {
        List<SuitCompanionEntity> suits = owned(player, COMMAND_RANGE);
        if (suits.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.no_companions"), true);
            return;
        }
        LivingEntity target = aimedEntity(player);
        for (SuitCompanionEntity suit : suits) {
            if (!suit.isBusy()) {
                suit.setCommandTarget(target);
            }
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.companion_stand_down"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.flightsuit.companion_attack",
                    suits.size(), target.getDisplayName()), true);
        }
    }

    private static LivingEntity aimedEntity(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(COMMAND_RANGE));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, stop, new AABB(eye, stop).inflate(1.0D),
                entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator()
                        && !(entity instanceof SuitCompanionEntity) && !(entity instanceof Player));
        Entity entity = hit == null ? null : hit.getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }
}
