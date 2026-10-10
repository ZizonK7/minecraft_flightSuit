package com.pfkfks.flightsuit.thanos;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.hero.CityHeroEntity;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One of Thanos's forces (DESIGN.md 4-16, M16). Chitauri shoot energy bolts; the Black Order each have a move
 * (Ebony Maw lifts and slams, Proxima Midnight lunges, Corvus Glaive blinks behind you, Cull Obsidian smashes
 * the ground); Thanos fights with whatever stones he holds (DESIGN 4-16 최종전). Black Order and Thanos don't
 * die - beaten, they withdraw (ThanosSaga decides what that means). Red Skull only talks. Never saved.
 */
public class ThanosForceEntity extends Monster {
    public static final String RAID_TAG = "flightsuit_thanos_raid";
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(ThanosForceEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private @Nullable BlockPos home;
    /** Thanos: the stones on his gauntlet (InfinityStone bits). */
    private int stones;
    private int blastCooldown = 40;
    private int specialCooldown = 120;
    private boolean rewound;
    private boolean gone;

    public ThanosForceEntity(EntityType<? extends ThanosForceEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        bossBar.setVisible(false);
        xpReward = 10;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public static ThanosForceEntity create(ServerLevel level, ThanosForce type, BlockPos home, int stones) {
        ThanosForceEntity entity = new ThanosForceEntity(ModEntities.THANOS_FORCE.get(), level);
        entity.entityData.set(TYPE, type.ordinal());
        entity.home = home.immutable();
        entity.stones = stones;
        set(entity, Attributes.MAX_HEALTH, type.health());
        set(entity, Attributes.ATTACK_DAMAGE, Math.max(1.0D, type.damage()));
        set(entity, Attributes.MOVEMENT_SPEED, type.speed());
        entity.setHealth(entity.getMaxHealth());
        entity.setCustomName(type.displayName());
        entity.setCustomNameVisible(type.role() != ThanosForce.Role.MINION);
        switch (type) {
            case PROXIMA_MIDNIGHT -> entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            case CORVUS_GLAIVE -> entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            case CULL_OBSIDIAN -> entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
            case CHITAURI_BRUTE -> set(entity, Attributes.KNOCKBACK_RESISTANCE, 1.0D);
            default -> {
            }
        }
        if (type.role() == ThanosForce.Role.NPC) {
            entity.restrictTo(home, 3);
        } else if (type.role() != ThanosForce.Role.MINION) {
            entity.restrictTo(home, 24);
            entity.bossBar.setName(type.displayName());
            entity.bossBar.setVisible(true);
        }
        entity.refreshDimensions();
        return entity;
    }

    private static void set(ThanosForceEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true) {
            @Override
            public boolean canUse() {
                return getForce().role() != ThanosForce.Role.NPC && getForce() != ThanosForce.CHITAURI_GUNNER && super.canUse();
            }
        });
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ThanosForceEntity.class) {
            @Override
            public boolean canUse() {
                return getForce().role() != ThanosForce.Role.NPC && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::isEnemy) {
            @Override
            public boolean canUse() {
                return getForce().role() != ThanosForce.Role.NPC && super.canUse();
            }
        });
    }

    public ThanosForce getForce() {
        return ThanosForce.byId(entityData.get(TYPE));
    }

    public int stones() {
        return stones;
    }

    public @Nullable BlockPos getHome() {
        return home;
    }

    public boolean isRaider() {
        return getTags().contains(RAID_TAG);
    }

    /** Players and everything on their side: suits, village people, golems, Hero City. */
    public boolean isEnemy(LivingEntity other) {
        if (!other.isAlive() || other instanceof ThanosForceEntity) {
            return false;
        }
        if (other instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return other instanceof SuitCompanionEntity || other instanceof ResidentEntity resident && !resident.isDowned()
                || other instanceof IronGolem || other instanceof CityHeroEntity;
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
        return super.getDimensions(pose).scale(getForce().scale());
    }

    /** Red Skull only talks: nothing should pick him as a target. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return getForce().role() != ThanosForce.Role.NPC && super.canBeSeenAsEnemy();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ---------------------------------------------------------------- moves

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (blastCooldown > 0) {
            blastCooldown--;
        }
        if (specialCooldown > 0) {
            specialCooldown--;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        boolean sees = getSensing().hasLineOfSight(target);
        switch (getForce()) {
            case CHITAURI -> {
                if (blastCooldown <= 0 && sees && distance > 3.0D && distance < 20.0D) {
                    bolt(server, target, 5.0F);
                    blastCooldown = 40 + random.nextInt(20);
                }
            }
            case CHITAURI_GUNNER -> gunner(server, target, distance, sees);
            case CHITAURI_BRUTE -> {
                if (specialCooldown <= 0 && distance < 4.5D) {
                    smash(server, 4.5D, 10.0F);
                    playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.7F);
                    specialCooldown = 100;
                }
            }
            case EBONY_MAW -> {
                if (specialCooldown <= 0 && sees && distance < 18.0D) {
                    // Telekinesis: up into the air, then down hard.
                    target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 2));
                    server.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.0D, target.getZ(), 30, 0.5D, 1.0D, 0.5D, 0.5D);
                    specialCooldown = 110;
                    blastCooldown = 34;
                    say(server, getForce().line("lift"));
                } else if (specialCooldown == 76) {
                    target.hurt(damageSources().mobAttack(this), 9.0F);
                    target.setDeltaMovement(target.getDeltaMovement().add(0.0D, -1.5D, 0.0D));
                    target.hurtMarked = true;
                } else if (blastCooldown <= 0 && sees && distance < 20.0D) {
                    bolt(server, target, 6.0F);
                    blastCooldown = 50;
                }
            }
            case PROXIMA_MIDNIGHT -> {
                if (specialCooldown <= 0 && distance > 4.0D && distance < 16.0D) {
                    lunge(target, 1.6D);
                    specialCooldown = 80;
                }
            }
            case CORVUS_GLAIVE -> {
                if (specialCooldown <= 0 && distance < 20.0D) {
                    Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(2.0D));
                    server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
                    if (randomTeleport(behind.x, target.getY(), behind.z, false)) {
                        playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.8F);
                    }
                    specialCooldown = 120;
                }
            }
            case CULL_OBSIDIAN -> {
                if (specialCooldown <= 0 && distance < 5.0D) {
                    smash(server, 5.0D, 13.0F);
                    specialCooldown = 120;
                }
            }
            case THANOS -> thanos(server, target, distance, sees);
            default -> {
            }
        }
    }

    /** A gunner stays at range: closes in until it has a clear shot, backs off when they get close, fires often. */
    private void gunner(ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (blastCooldown <= 0 && sees && distance < 24.0D) {
            bolt(server, target, 4.0F);
            blastCooldown = 25 + random.nextInt(15);
        }
        if (tickCount % 10 != 0) {
            return;
        }
        if (!sees || distance > 16.0D) {
            getNavigation().moveTo(target, 1.0D);
        } else if (distance < 7.0D) {
            Vec3 away = position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 1.0E-4D) {
                away = position().add(away.normalize().scale(6.0D));
                getNavigation().moveTo(away.x, away.y, away.z, 1.2D);
            }
        } else {
            getNavigation().stop();
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /** Thanos uses the stones on his gauntlet: Power smash, Space jump, Reality blinding, Time rewind, Mind daze; Soul feeds on his blows. */
    private void thanos(ServerLevel server, LivingEntity target, double distance, boolean sees) {
        if (has(InfinityStone.TIME) && !rewound && getHealth() < getMaxHealth() * 0.4F) {
            rewound = true;
            heal(getMaxHealth() * 0.2F);
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5D, getZ(), 40, 1.0D, 1.5D, 1.0D, 0.0D);
            say(server, getForce().line("time"));
            return;
        }
        if (specialCooldown > 0) {
            if (blastCooldown <= 0 && sees && distance > 5.0D && distance < 24.0D && (has(InfinityStone.POWER) || has(InfinityStone.SPACE))) {
                bolt(server, target, 8.0F);
                blastCooldown = 50;
            }
            return;
        }
        specialCooldown = 100;
        int roll = random.nextInt(4);
        if (roll == 0 && has(InfinityStone.SPACE) && distance > 6.0D) {
            Vec3 near = target.position().add(target.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(2.0D));
            server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(), 40, 0.5D, 1.0D, 0.5D, 0.5D);
            randomTeleport(near.x, target.getY(), near.z, false);
            say(server, getForce().line("space"));
        } else if (roll == 1 && has(InfinityStone.REALITY)) {
            for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0D), this::isEnemy)) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
            }
            server.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 1.0D, getZ(), 80, 4.0D, 2.0D, 4.0D, 0.0D);
            say(server, getForce().line("reality"));
        } else if (roll == 2 && has(InfinityStone.MIND)) {
            for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0D), this::isEnemy)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            }
            say(server, getForce().line("mind"));
        } else if (distance < 7.0D) {
            smash(server, 6.0D, has(InfinityStone.POWER) ? 18.0F : 12.0F);
        } else {
            lunge(target, 1.8D);
        }
    }

    private boolean has(InfinityStone stone) {
        return (stones & stone.bit()) != 0;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && getForce() == ThanosForce.THANOS && has(InfinityStone.SOUL)) {
            heal(4.0F);
        }
        return hit;
    }

    private void bolt(ServerLevel server, LivingEntity target, float damage) {
        swing(InteractionHand.MAIN_HAND);
        Vec3 from = getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 step = to.subtract(from);
        int points = Math.max(4, (int) (step.length() * 2.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            server.sendParticles(getForce().isChitauri() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.WITCH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        target.hurt(damageSources().mobAttack(this), damage);
        playSound(SoundEvents.FIRECHARGE_USE, 0.8F, 1.7F);
    }

    private void lunge(LivingEntity target, double speed) {
        Vec3 push = target.position().subtract(position()).normalize().scale(speed);
        setDeltaMovement(push.x, 0.4D, push.z);
        hurtMarked = true;
        playSound(SoundEvents.TRIDENT_RIPTIDE_1, 1.0F, 1.0F);
    }

    private void smash(ServerLevel server, double radius, float damage) {
        swing(InteractionHand.MAIN_HAND);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.3D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isEnemy)) {
            hit.hurt(damageSources().mobAttack(this), damage);
            Vec3 push = hit.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(1.3D);
                hit.push(push.x, 0.5D, push.z);
                hit.hurtMarked = true;
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F);
    }

    private void say(ServerLevel server, Component line) {
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0D))) {
            player.sendSystemMessage(line);
        }
    }

    // ---------------------------------------------------------------- damage, defeat, talk

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getForce().role() == ThanosForce.Role.NPC || source.getEntity() instanceof ThanosForceEntity) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Black Order and Thanos withdraw when beaten - ThanosSaga hands out the stone, or moves the raid on. */
    @Override
    public void die(DamageSource source) {
        ThanosForce force = getForce();
        if (level().isClientSide || force.role() == ThanosForce.Role.MINION || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(level() instanceof ServerLevel server)) {
            super.die(source);
            return;
        }
        if (gone) {
            return;
        }
        gone = true;
        setHealth(1.0F);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 24, 0.5D, 1.0D, 0.5D, 0.03D);
        say(server, force.line("beaten"));
        ThanosSaga.onBeaten(server, this, source.getEntity());
        discard();
    }

    /** The Infinity Gauntlet's snap: gone to dust. */
    public void dust(ServerLevel server) {
        if (gone) {
            return;
        }
        gone = true;
        server.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.0D, getZ(), 60, 0.4D, 1.0D, 0.4D, 0.02D);
        server.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 1.0D, getZ(), 30, 0.4D, 1.0D, 0.4D, 0.02D);
        if (getForce().role() != ThanosForce.Role.MINION) {
            ThanosSaga.onBeaten(server, this, null);
        }
        discard();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || getForce().role() != ThanosForce.Role.NPC) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ThanosSaga.redSkull(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Force", getForce().ordinal());
        tag.putInt("Stones", stones);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, ThanosForce.byId(tag.getInt("Force")).ordinal());
        stones = tag.getInt("Stones");
    }
}
