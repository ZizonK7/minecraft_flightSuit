package com.pfkfks.flightsuit.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * 2026-10-12: the witch's brew the phantom stole (StolenSkill.WITCH_POTION) - a real splash potion bottle tumbling
 * through the air (drawn as one, in the brew's colour), bursting on what it meets into the vanilla splash and one
 * bad effect on every foe round it: harming, poison, slowness or weakness, picked as it's thrown. Never saved.
 */
public class WitchBrewEntity extends ThrowableItemProjectile {
    private static final Potion[] BREWS = {Potions.HARMING, Potions.POISON, Potions.SLOWNESS, Potions.WEAKNESS};

    private int brew;
    private float damage;
    private double radius = 3.0D;
    private Predicate<LivingEntity> hits = entity -> false;

    public WitchBrewEntity(EntityType<? extends WitchBrewEntity> type, Level level) {
        super(type, level);
    }

    public static void throwFrom(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, float damage, Predicate<LivingEntity> hits) {
        WitchBrewEntity brew = new WitchBrewEntity(com.pfkfks.flightsuit.registry.ModEntities.WITCH_BREW.get(), level);
        brew.setOwner(caster);
        brew.brew = level.getRandom().nextInt(BREWS.length);
        brew.damage = damage;
        brew.hits = hits;
        brew.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), BREWS[brew.brew]));
        brew.setPos(from.x, from.y, from.z);
        brew.shoot(dir.x, dir.y + 0.1D, dir.z, 1.1F, 1.0F);
        level.addFreshEntity(brew);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected float getGravity() {
        return 0.04F;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        Vec3 at = position();
        LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
        DamageSource source = owner instanceof ServerPlayer player ? damageSources().playerAttack(player)
                : owner != null ? damageSources().mobAttack(owner) : damageSources().magic();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                entity -> entity != owner && entity.isAlive() && hits.test(entity)
                        && entity.getBoundingBox().getCenter().distanceTo(at) <= radius + entity.getBbWidth() / 2.0D)) {
            victim.invulnerableTime = 0;
            switch (brew) {
                case 0 -> victim.hurt(source, damage * 1.5F);
                case 1 -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                case 2 -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
                default -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            }
            if (brew != 0) {
                victim.hurt(source, damage * 0.5F);
            }
        }
        // The vanilla splash: the glass breaking and the coloured cloud.
        level.levelEvent(2002, BlockPos.containing(at), PotionUtils.getColor(getItem()));
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
