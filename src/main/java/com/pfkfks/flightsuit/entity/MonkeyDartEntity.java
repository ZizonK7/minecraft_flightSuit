package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-10-12: a dart thrown by a Cannon Shooter's monkey tower (MonkeyEntity, Bloons-style) - an arrow that only
 * hits the owner's foes, can't be picked up and is gone soon after it sticks. Never saved.
 */
public class MonkeyDartEntity extends AbstractArrow {
    public MonkeyDartEntity(EntityType<? extends MonkeyDartEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    /** A dart from {@code from} toward {@code dir}, in {@code owner}'s name. */
    public static void throwDart(Level level, LivingEntity owner, Vec3 from, Vec3 dir, float speed, double damage) {
        MonkeyDartEntity dart = new MonkeyDartEntity(com.pfkfks.flightsuit.registry.ModEntities.MONKEY_DART.get(), level);
        dart.setOwner(owner);
        dart.setPos(from.x, from.y, from.z);
        dart.setBaseDamage(damage / speed);
        dart.shoot(dir.x, dir.y, dir.z, speed, 1.0F);
        level.addFreshEntity(dart);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || entity instanceof MonkeyEntity) {
            return false;
        }
        return !(getOwner() instanceof ServerPlayer owner) || !SuitWeapons.isFriendly(owner, entity)
                && !(entity instanceof LivingEntity living && com.pfkfks.flightsuit.suit.BishopArts.isAlly(owner, living));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (inGroundTime > 20 || tickCount > 100)) {
            discard();
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
