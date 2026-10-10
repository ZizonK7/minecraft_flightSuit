package com.pfkfks.flightsuit.hero;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.war.RaidMember;
import com.pfkfks.flightsuit.war.WarRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A Hero City hero (or S.H.I.E.L.D. agent) (DESIGN.md 4-13, M13). Keeps the city: fights monsters and villains
 * there, and anyone at war with the city. Heroes don't die - beaten, they withdraw for a while. Like the
 * fortress garrisons they aren't saved: the city puts its people back each time someone comes.
 */
public class CityHeroEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(CityHeroEntity.class, EntityDataSerializers.INT);

    private @Nullable BlockPos home;
    private int skillCooldown;
    private int rallyCooldown;
    private int shots;
    private long lastPenaltyAt;

    public CityHeroEntity(EntityType<? extends CityHeroEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    public static CityHeroEntity create(ServerLevel level, HeroType type, BlockPos home) {
        CityHeroEntity hero = new CityHeroEntity(ModEntities.CITY_HERO.get(), level);
        hero.entityData.set(TYPE, type.ordinal());
        hero.home = home.immutable();
        hero.restrictTo(home, hero.homeRadius());
        hero.setItemSlot(EquipmentSlot.MAINHAND, type.held());
        // The shield goes in the off hand, like Captain carries it.
        if (type == HeroType.CAPTAIN) {
            hero.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            hero.setItemSlot(EquipmentSlot.OFFHAND, type.held());
        } else if (type == HeroType.BLACK_WIDOW) {
            // A Widow's Bite baton in each hand.
            hero.setItemSlot(EquipmentSlot.OFFHAND, type.held());
        }
        set(hero, Attributes.MAX_HEALTH, type.health());
        set(hero, Attributes.ATTACK_DAMAGE, type.damage());
        set(hero, Attributes.MOVEMENT_SPEED, type.speed());
        hero.setHealth(hero.getMaxHealth());
        hero.setCustomName(type.displayName());
        hero.setCustomNameVisible(type.isHero());
        hero.refreshDimensions();
        return hero;
    }

    private static void set(CityHeroEntity hero, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = hero.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, HeroType.AGENT.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true) {
            @Override
            public boolean canUse() {
                return !getHeroType().ranged() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !getHeroType().ranged() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CityHeroEntity.class));
        targetSelector.addGoal(2, new FoeGoal());
    }

    public HeroType getHeroType() {
        return HeroType.byId(entityData.get(TYPE));
    }

    public @Nullable BlockPos getHome() {
        return home;
    }

    /** Captain and Iron Man keep to the HQ lobby; the others walk the whole city. */
    private int homeRadius() {
        HeroType type = getHeroType();
        return type == HeroType.CAPTAIN || type == HeroType.IRON_MAN ? 10 : HeroCityBuilder.EDGE - 6;
    }

    /**
     * During an invasion everyone - Captain and Iron Man too - may go anywhere in the city, and heads for the
     * nearest invader when there's nobody to fight close by.
     */
    public void setOnAlert(boolean alert, @Nullable LivingEntity nearestInvader) {
        if (home == null) {
            return;
        }
        restrictTo(home, alert ? HeroCityBuilder.EDGE + 4 : homeRadius());
        if (alert && nearestInvader != null && getTarget() == null) {
            getNavigation().moveTo(nearestInvader, getHeroType().ranged() ? 1.0D : 1.15D);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getHeroType().scale());
    }

    // ---- who they fight ----

    /** Monsters and raiders near the city, villains, and players (with their suits and soldiers) at war with the city. */
    public boolean isFoe(LivingEntity other) {
        if (!other.isAlive() || other == this || other instanceof CityHeroEntity) {
            return false;
        }
        if (other instanceof RaidMember member) {
            if (member.isNoThreat()) {
                return false;
            }
            if (member.role() == WarRole.RAID) {
                return true;
            }
            UUID commander = member.commander();
            return commander != null && level() instanceof ServerLevel server && HeroData.get(server.getServer()).trust(commander) <= -50;
        }
        if (other instanceof Enemy) {
            return home == null || other.blockPosition().closerThan(home, 40.0D) || HeroCity.isVillain(other);
        }
        UUID backer = null;
        if (other instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            backer = player.getUUID();
        } else if (other instanceof SuitCompanionEntity suit) {
            backer = suit.getOwnerId();
        } else if (other instanceof ResidentEntity resident && !resident.isDowned()) {
            backer = resident.getCommander();
        }
        return backer != null && level() instanceof ServerLevel server && HeroData.get(server.getServer()).trust(backer) <= -50;
    }

    private final class FoeGoal extends TargetGoal {
        private LivingEntity candidate;

        FoeGoal() {
            super(CityHeroEntity.this, true);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (getTarget() != null || getRandom().nextInt(reducedTickDelay(10)) != 0) {
                return false;
            }
            candidate = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20.0D), CityHeroEntity.this::isFoe)) {
                double dist = entity.distanceToSqr(CityHeroEntity.this);
                if (dist < best && getSensing().hasLineOfSight(entity)) {
                    candidate = entity;
                    best = dist;
                }
            }
            return candidate != null;
        }

        @Override
        public void start() {
            setTarget(candidate);
            super.start();
        }
    }

    /** Iron Man and Hawkeye keep their distance and shoot. */
    private final class RangedGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private int cooldown;

        RangedGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return getHeroType().ranged() && target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            setAggressive(true);
        }

        @Override
        public void stop() {
            setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = distanceTo(target);
            boolean sees = getSensing().hasLineOfSight(target);
            if (distance > 12.0D || !sees) {
                getNavigation().moveTo(target, 1.1D);
            } else if (distance < 5.0D) {
                Vec3 away = position().subtract(target.position()).normalize().scale(4.0D).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
            } else {
                getNavigation().stop();
            }
            if (--cooldown <= 0 && sees && distance < 18.0D) {
                cooldown = getHeroType() == HeroType.IRON_MAN ? 25 : 30;
                shoot(target);
            }
        }
    }

    private void shoot(LivingEntity target) {
        swing(InteractionHand.MAIN_HAND);
        if (getHeroType() == HeroType.AGENT) {
            beam(target, ParticleTypes.SMOKE);
            target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F);
            playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.8F);
            return;
        }
        if (getHeroType() == HeroType.IRON_MAN) {
            beam(target, ParticleTypes.END_ROD);
            target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F);
            playSound(SoundEvents.FIRECHARGE_USE, 0.8F, 1.6F);
            return;
        }
        // Hawkeye doesn't miss: his arrows only ever find foes (no stray arrow into a friend fighting beside him).
        boolean volley = ++shots % 4 == 0;
        int hits = 0;
        for (LivingEntity foe : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(volley ? 4.0D : 0.0D), this::isFoe)) {
            if (hits >= (volley ? 3 : 1)) {
                break;
            }
            beam(foe, ParticleTypes.CRIT);
            foe.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F);
            hits++;
        }
        playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.2F);
    }

    private void beam(LivingEntity target, ParticleOptions particle) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 from = getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 step = to.subtract(from);
        int points = Math.max(4, (int) (step.length() * 2.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            server.sendParticles(particle, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // ---- signature moves ----

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (skillCooldown > 0) {
            skillCooldown--;
        }
        if (rallyCooldown > 0) {
            rallyCooldown--;
        }
        if (target == null || !target.isAlive() || skillCooldown > 0) {
            return;
        }
        double distance = distanceTo(target);
        boolean sees = getSensing().hasLineOfSight(target);
        switch (getHeroType()) {
            case CAPTAIN -> {
                if (sees && distance > 3.0D && distance < 14.0D) {
                    // The shield, thrown.
                    beam(target, ParticleTypes.CRIT);
                    strike(target, 8.0F, 1.2D);
                    playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.4F);
                    skillCooldown = 60;
                }
                if (rallyCooldown <= 0) {
                    rallyCooldown = 300;
                    for (LivingEntity ally : level().getEntitiesOfClass(CityHeroEntity.class, getBoundingBox().inflate(12.0D))) {
                        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0));
                    }
                    say(getHeroType().line("rally"));
                }
            }
            case THOR -> {
                if (sees && distance < 16.0D && level() instanceof ServerLevel server) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
                    if (bolt != null) {
                        bolt.moveTo(target.getX(), target.getY(), target.getZ());
                        bolt.setVisualOnly(true);
                        server.addFreshEntity(bolt);
                    }
                    strike(target, 10.0F, 0.6D);
                    for (LivingEntity near : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0D), this::isFoe)) {
                        if (near != target) {
                            strike(near, 4.0F, 0.4D);
                        }
                    }
                    skillCooldown = 100;
                }
            }
            case HULK -> {
                if (distance < 4.5D) {
                    swing(InteractionHand.MAIN_HAND);
                    for (LivingEntity near : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0D), this::isFoe)) {
                        strike(near, 14.0F, 1.0D);
                        near.push(0.0D, 0.7D, 0.0D);
                    }
                    if (level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(), 6, 2.0D, 0.2D, 2.0D, 0.0D);
                    }
                    playSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
                    skillCooldown = 80;
                }
            }
            case SPIDER_MAN -> {
                if (sees && distance > 3.0D && distance < 14.0D) {
                    beam(target, ParticleTypes.WHITE_ASH);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                    strike(target, 3.0F, 0.0D);
                    playSound(SoundEvents.SPIDER_AMBIENT, 0.8F, 1.6F);
                    skillCooldown = 60;
                }
            }
            case BLACK_WIDOW -> {
                if (distance < 3.0D) {
                    swing(InteractionHand.MAIN_HAND);
                    strike(target, 6.0F, 0.2D);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                    if (level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0D, target.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.1D);
                    }
                    skillCooldown = 80;
                }
            }
            default -> {
            }
        }
    }

    private void strike(LivingEntity victim, float amount, double knockback) {
        if (victim.hurt(damageSources().mobAttack(this), amount) && knockback > 0.0D) {
            Vec3 push = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(knockback);
                victim.push(push.x, 0.3D, push.z);
                victim.hurtMarked = true;
            }
        }
    }

    private void say(net.minecraft.network.chat.Component line) {
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(32.0D))) {
            player.sendSystemMessage(line);
        }
    }

    // ---- damage, defeat, talk ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer player && level().getGameTime() - lastPenaltyAt > 40L) {
            lastPenaltyAt = level().getGameTime();
            HeroCity.onHeroHit(player, false);
        }
        if (source.getEntity() instanceof CityHeroEntity) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Heroes don't die: beaten, they withdraw (HeroCity counts it toward the city falling). */
    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            super.die(source);
            return;
        }
        setHealth(1.0F);
        if (level() instanceof ServerLevel server) {
            Entity killer = source.getEntity();
            if (killer instanceof ServerPlayer player) {
                HeroCity.onHeroHit(player, true);
            }
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 16, 0.4D, 0.8D, 0.4D, 0.02D);
            if (getHeroType().isHero()) {
                say(getHeroType().line("withdraw"));
            }
            HeroCity.onHeroBeaten(server, this, killer);
        }
        discard();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            HeroCity.talk(serverPlayer, this);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return getHeroType() == HeroType.HULK ? SoundEvents.RAVAGER_HURT : SoundEvents.PLAYER_HURT;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Hero", getHeroType().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, HeroType.byId(tag.getInt("Hero")).ordinal());
    }
}
