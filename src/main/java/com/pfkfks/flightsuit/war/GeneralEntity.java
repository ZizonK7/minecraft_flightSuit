package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.ai.FollowCommanderGoal;
import com.pfkfks.flightsuit.war.ai.GeneralGuardGoal;
import com.pfkfks.flightsuit.war.ai.MarchOnVillageGoal;
import com.pfkfks.flightsuit.war.ai.RaiderTargetGoal;
import com.pfkfks.flightsuit.war.ai.YieldedGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A named Three Kingdoms general (DESIGN.md 4-11 장수 표): leads a raid's last wave with their own skills and a
 * boss bar. Generals never die - beaten, they kneel - so a surrendered general can be recruited and then
 * defends the player's village (DESIGN "항복과 등용"; leading the army out comes with M12's campaigns).
 */
public class GeneralEntity extends Monster implements RaidMember {
    private static final EntityDataAccessor<Integer> GENERAL = SynchedEntityData.defineId(GeneralEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> YIELDED = SynchedEntityData.defineId(GeneralEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RECRUITED = SynchedEntityData.defineId(GeneralEntity.class, EntityDataSerializers.BOOLEAN);

    private static final UUID ENRAGE_BONUS = UUID.fromString("2f6f2b8e-3a51-4f0e-9a8b-6c1d7e5f4a21");
    /** A recruited general who is beaten lies low this long, then gets back up. */
    private static final int WOUND_TICKS = 20 * 60;

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private int raidId = -1;
    /** The hall being raided, or (recruited) the hall they now guard. */
    private @Nullable BlockPos hallPos;
    private @Nullable UUID ownerId;
    private int woundTicks;
    private int standUpTicks;
    private boolean enraged;
    private int whirlwindCooldown;
    private int roarCooldown;
    private int chargeCooldown;
    private int chargeTicks;
    private int ambushCooldown;
    private @Nullable UUID duelWith;
    private boolean duelOffered;
    private WarRole role = WarRole.RAID;
    private @Nullable BlockPos home;
    private @Nullable Kingdom foe;
    private int rallyCooldown;
    private long lastPenaltyAt;

    public GeneralEntity(EntityType<? extends GeneralEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 40;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public static GeneralEntity create(ServerLevel level, General general, int raidId, @Nullable BlockPos hall) {
        GeneralEntity entity = new GeneralEntity(ModEntities.GENERAL.get(), level);
        entity.setup(general, raidId, hall);
        return entity;
    }

    private void setup(General general, int raidId, @Nullable BlockPos hall) {
        entityData.set(GENERAL, general.ordinal());
        this.raidId = raidId;
        this.hallPos = hall == null ? null : hall.immutable();
        setItemSlot(EquipmentSlot.MAINHAND, general.weapon());
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(general.health());
        }
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(general.baseDamage());
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(general.speed());
        }
        setHealth(getMaxHealth());
        setCustomName(general.displayName());
        setCustomNameVisible(true);
        bossBar.setName(general.displayName());
    }

    /** Keeps their kingdom's fortress (M12); the ruler is who you talk to there. Not saved, like the garrison. */
    public GeneralEntity asGarrison(BlockPos fortCenter) {
        role = WarRole.GARRISON;
        home = fortCenter.immutable();
        hallPos = null;
        restrictTo(home, getGeneral().isLeader() ? 8 : 16);
        bossBar.setVisible(false);
        return this;
    }

    /** Storms {@code fort}'s fortress (a 방어 지원 battle's last wave). */
    public GeneralEntity asStorm(Kingdom fort, BlockPos objective) {
        role = WarRole.RAID;
        foe = fort;
        hallPos = objective.immutable();
        return this;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                  @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (getMainHandItem().isEmpty()) {
            VillageHallBlockEntity hall = Villages.containing(level.getLevel(), blockPosition());
            setup(General.byId(random.nextInt(General.values().length)), -1, hall == null ? null : hall.getBlockPos());
        }
        return result;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GENERAL, General.GUAN_YU.ordinal());
        entityData.define(YIELDED, false);
        entityData.define(RECRUITED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new YieldedGoal(this, () -> hasYielded() || woundTicks > 0));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canFight() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new MarchOnVillageGoal(this, () -> canFight() && !isRecruited() && role == WarRole.RAID ? hallPos : null));
        goalSelector.addGoal(3, new GeneralGuardGoal(this));
        goalSelector.addGoal(3, new FollowCommanderGoal(this, () -> isRecruited() && canFight() && following ? ownerId : null, () -> null));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, KingdomSoldierEntity.class, GeneralEntity.class) {
            @Override
            public boolean canUse() {
                return canFight() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new RaiderTargetGoal(this, this, () -> !canFight()));
        // Recruited: the village's monsters and raiders inside it.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                target -> isRecruited() && canFight() && !RaidMember.isNoThreat(target) && isInHomeVillage(target.blockPosition())));
    }

    // ---- state ----

    public General getGeneral() {
        return General.byId(entityData.get(GENERAL));
    }

    public boolean hasYielded() {
        return entityData.get(YIELDED);
    }

    public boolean isRecruited() {
        return entityData.get(RECRUITED);
    }

    public boolean isWounded() {
        return woundTicks > 0;
    }

    /** Neither kneeling nor nursing a wound. */
    public boolean canFight() {
        return !hasYielded() && woundTicks <= 0;
    }

    @Override
    public int raidId() {
        return raidId;
    }

    @Override
    public boolean isNoThreat() {
        return hasYielded() || isRecruited();
    }

    @Override
    public Kingdom kingdom() {
        return getGeneral().kingdom();
    }

    @Override
    public WarRole role() {
        return role;
    }

    @Override
    public @Nullable Kingdom foe() {
        return foe;
    }

    @Override
    public @Nullable UUID commander() {
        return ownerId;
    }

    @Override
    public @Nullable BlockPos home() {
        return home;
    }

    @Override
    public boolean isPlayerSide() {
        return isRecruited() || role == WarRole.ALLY;
    }

    /** Marching with the lord (/village army follow), instead of keeping the village. */
    private boolean following;

    public void setFollowing(boolean following) {
        this.following = following && isRecruited();
        if (this.following) {
            restrictTo(BlockPos.ZERO, -1);
        } else if (hallPos != null) {
            restrictTo(hallPos, 32);
        }
    }

    public boolean isFollowing() {
        return following;
    }

    @Override
    public boolean shouldBeSaved() {
        return role != WarRole.GARRISON && super.shouldBeSaved();
    }

    public @Nullable BlockPos getHallPos() {
        return hallPos;
    }

    public @Nullable UUID getOwnerId() {
        return ownerId;
    }

    public @Nullable UUID getDuelWith() {
        return duelWith;
    }

    public boolean isInHomeVillage(BlockPos pos) {
        VillageHallBlockEntity hall = Villages.hallAt(level(), hallPos);
        return hall != null && hall.contains(pos);
    }

    public void yieldNow() {
        if (hasYielded() || isRecruited()) {
            return;
        }
        entityData.set(YIELDED, true);
        setTarget(null);
        getNavigation().stop();
        setAggressive(false);
        bossBar.setVisible(false);
        duelWith = null;
    }

    /** Takes the player's side: guards their village from now on. */
    public void recruit(VillageHallBlockEntity hall) {
        entityData.set(YIELDED, false);
        entityData.set(RECRUITED, true);
        raidId = -1;
        hallPos = hall.getBlockPos().immutable();
        ownerId = hall.getOwner();
        setHealth(getMaxHealth());
        setTarget(null);
        bossBar.setVisible(false);
        restrictTo(hallPos, 32);
        setCustomName(Component.translatable("general.flightsuit.ally", getGeneral().displayName()).withStyle(ChatFormatting.GOLD));
        playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(canFight() ? target : null);
    }

    /** Who this general fights right now (the skills use it to pick who to hit). */
    public boolean isFoe(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == this) {
            return false;
        }
        if (isRecruited() && entity instanceof Monster && !(entity instanceof RaidMember)) {
            return true;
        }
        return WarTargets.isEnemy(this, this, living);
    }

    // ---- ticking ----

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (woundTicks > 0) {
            if (--woundTicks == 0) {
                setHealth(getMaxHealth() * 0.5F);
                playSound(SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
            }
            return;
        }
        if (hasYielded() && raidId < 0 && !isRecruited()) {
            // Spawned by hand (egg / command): no raid to recruit or release him, so he gets back up after a while.
            if (++standUpTicks >= WOUND_TICKS) {
                standUpTicks = 0;
                entityData.set(YIELDED, false);
                setHealth(getMaxHealth() * 0.5F);
                bossBar.setVisible(true);
            }
            return;
        }
        if (!canFight()) {
            return;
        }
        tickSkills();
    }

    private void tickSkills() {
        General general = getGeneral();
        LivingEntity target = getTarget();
        if (whirlwindCooldown > 0) {
            whirlwindCooldown--;
        }
        if (roarCooldown > 0) {
            roarCooldown--;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (ambushCooldown > 0) {
            ambushCooldown--;
        }
        if (chargeTicks > 0) {
            tickCharge();
        }
        if (general.has(General.Skill.DUEL) && !isRecruited()) {
            tickDuel(general);
        }
        if (general.has(General.Skill.ENRAGE) && !enraged && getHealth() < getMaxHealth() * 0.5F) {
            enrage(general);
        }
        if (rallyCooldown > 0) {
            rallyCooldown--;
        }
        if (general.has(General.Skill.RALLY) && rallyCooldown <= 0 && target != null) {
            rally(general);
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        if (general.has(General.Skill.WHIRLWIND) && whirlwindCooldown <= 0 && distance < 3.5D) {
            whirlwind();
        } else if (general.has(General.Skill.ROAR) && roarCooldown <= 0 && distance < 8.0D) {
            roar(general);
        } else if (general.has(General.Skill.CHARGE) && chargeCooldown <= 0 && distance > 5.0D && distance < 14.0D && hasLineOfSight(target)) {
            startCharge(target);
        } else if (general.has(General.Skill.AMBUSH) && ambushCooldown <= 0 && distance > 4.0D && distance < 16.0D) {
            ambush(target);
        }
    }

    private List<LivingEntity> foesWithin(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isFoe);
    }

    private void strike(LivingEntity victim, float amount, double knockback) {
        if (victim.hurt(damageSources().mobAttack(this), amount)) {
            Vec3 push = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(knockback);
                victim.push(push.x, 0.35D, push.z);
                victim.hurtMarked = true;
            }
        }
    }

    private float attackDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** 청룡언월도 회전베기: everything around takes a heavy cut and is thrown back. */
    private void whirlwind() {
        whirlwindCooldown = 100;
        swing(InteractionHand.MAIN_HAND);
        for (LivingEntity victim : foesWithin(4.0D)) {
            strike(victim, attackDamage() * 1.2F, 1.0D);
        }
        if (level() instanceof ServerLevel server) {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6.0D;
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(angle) * 2.5D, getY() + 1.0D,
                        getZ() + Math.sin(angle) * 2.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
    }

    /**
     * 장판교 고함: everyone around reels (slowed, weakened), and the shout shatters the glass and bursts the
     * doors nearby - fight damage the village's builders put back.
     */
    private void roar(General general) {
        roarCooldown = 240;
        say(general.line("roar"), 48.0D);
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.8F);
        for (LivingEntity victim : foesWithin(8.0D)) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.6D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            WarDamage.shatter(server, blockPosition(), 6, 12);
        }
    }

    private void startCharge(LivingEntity target) {
        chargeCooldown = 140;
        chargeTicks = 12;
        Vec3 dir = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        setDeltaMovement(dir.x * 1.4D, 0.25D, dir.z * 1.4D);
        hurtMarked = true;
        playSound(SoundEvents.RAVAGER_STEP, 1.5F, 0.8F);
    }

    private void tickCharge() {
        chargeTicks--;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 2, 0.2D, 0.0D, 0.2D, 0.0D);
        }
        for (LivingEntity victim : foesWithin(1.2D)) {
            strike(victim, attackDamage(), 1.6D);
            chargeTicks = 0;
        }
    }

    private void enrage(General general) {
        enraged = true;
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(ENRAGE_BONUS) == null) {
            damage.addPermanentModifier(new AttributeModifier(ENRAGE_BONUS, "Enraged", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        say(general.line("enrage"), 48.0D);
        playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.1F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0D, getZ(), 6, 0.4D, 0.3D, 0.4D, 0.0D);
        }
    }

    /** 지휘: everyone of their kingdom around fights harder and heals for a while. */
    private void rally(General general) {
        rallyCooldown = 300;
        say(general.line("rally"), 32.0D);
        for (LivingEntity ally : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0D),
                entity -> entity instanceof RaidMember member && !member.isNoThreat() && member.kingdom() == kingdom())) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
        playSound(SoundEvents.BELL_BLOCK, 1.5F, 1.2F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.0D, getZ(), 10, 1.5D, 0.5D, 1.5D, 0.0D);
        }
    }

    /** 기습: gone in a puff of smoke, back behind the target. */
    private void ambush(LivingEntity target) {
        ambushCooldown = 160;
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(1.8D));
        AABB box = getBoundingBox().move(behind.subtract(position()));
        if (!level().noCollision(this, box)) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
        }
        teleportTo(behind.x, behind.y, behind.z);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.4F);
        getLookControl().setLookAt(target);
        swing(InteractionHand.MAIN_HAND);
        strike(target, attackDamage(), 0.6D);
    }

    /**
     * 일기토: the first player he sees in the fight is called out. The soldiers leave that player to him; if he
     * loses the duel the whole army breaks (RaidManager), and if the player walks away it lapses.
     */
    private void tickDuel(General general) {
        if (duelWith != null) {
            Player rival = level().getPlayerByUUID(duelWith);
            if (rival == null || !rival.isAlive() || rival.distanceTo(this) > 40.0F) {
                duelWith = null;
                say(general.line("duel_lapsed"), 48.0D);
            } else if (getTarget() != rival) {
                setTarget(rival);
            }
            return;
        }
        if (duelOffered || tickCount % 20 != 0 || raidId < 0) {
            return;
        }
        Player rival = level().getNearestPlayer(this, 16.0D);
        if (rival != null && !rival.isCreative() && !rival.isSpectator() && hasLineOfSight(rival)) {
            duelOffered = true;
            duelWith = rival.getUUID();
            setTarget(rival);
            say(general.line("duel", rival.getName()), 64.0D);
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.5F, 0.6F);
        }
    }

    private void say(Component line, double range) {
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(range))) {
            player.sendSystemMessage(line);
        }
    }

    // ---- defeat instead of death ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        Entity attacker = source.getEntity();
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass) {
            if (hasYielded() || woundTicks > 0) {
                return false;
            }
            if (isRecruited() && (attacker instanceof Player || attacker instanceof SuitCompanionEntity || attacker instanceof ResidentEntity
                    || attacker instanceof IronGolem
                    || attacker instanceof GeneralEntity && ((GeneralEntity) attacker).isRecruited())) {
                return false;
            }
            if (!isRecruited() && attacker instanceof RaidMember member && !member.isNoThreat() && member.raidId() == raidId && raidId >= 0) {
                return false;
            }
            if (role == WarRole.GARRISON && attacker instanceof ServerPlayer player && level().getGameTime() - lastPenaltyAt > 40L) {
                lastPenaltyAt = level().getGameTime();
                Diplomacy.onGarrisonHit(player, kingdom(), false);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            super.die(source);
            return;
        }
        setHealth(1.0F);
        General general = getGeneral();
        if (isRecruited()) {
            woundTicks = WOUND_TICKS;
            setTarget(null);
            getNavigation().stop();
            say(general.line("wounded"), 48.0D);
            return;
        }
        Entity killer = source.getEntity();
        if (role == WarRole.GARRISON) {
            say(general.line("defeated"), 64.0D);
            yieldNow();
            if (level() instanceof ServerLevel server) {
                if (killer instanceof ServerPlayer player) {
                    Diplomacy.onGarrisonHit(player, kingdom(), true);
                }
                FortressManager.onGeneralBeaten(server, this, FortressManager.byPlayerSide(killer));
            }
            return;
        }
        boolean duel = duelWith != null && killer != null && killer.getUUID().equals(duelWith);
        say(general.line(duel ? "duel_lost" : "defeated"), 64.0D);
        yieldNow();
        playSound(SoundEvents.ANVIL_LAND, 0.8F, 0.6F);
        if (level() instanceof ServerLevel server) {
            RaidManager.onGeneralDefeated(server, this, duel);
        }
    }

    // ---- talking to a recruited general ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        General general = getGeneral();
        if (role == WarRole.GARRISON && !hasYielded() && player instanceof ServerPlayer serverPlayer) {
            Diplomacy.talk(serverPlayer, this);
        } else if (isRecruited()) {
            player.displayClientMessage(general.line(woundTicks > 0 ? "wounded" : "greet", player.getName()), false);
        } else if (hasYielded()) {
            player.displayClientMessage(general.line("kneel"), false);
        }
        return InteractionResult.CONSUME;
    }

    // ---- boss bar ----

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
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    // ---- misc ----

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return canFight() && !isRecruited() && role == WarRole.RAID;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !isRecruited();
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("General", getGeneral().ordinal());
        tag.putBoolean("Yielded", hasYielded());
        tag.putBoolean("Recruited", isRecruited());
        tag.putInt("Raid", raidId);
        if (hallPos != null) {
            tag.put("Hall", NbtUtils.writeBlockPos(hallPos));
        }
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("Wound", woundTicks);
        tag.putBoolean("Enraged", enraged);
        tag.putBoolean("DuelOffered", duelOffered);
        tag.putInt("Role", role.ordinal());
        if (foe != null) {
            tag.putInt("Foe", foe.ordinal());
        }
        tag.putBoolean("Following", following);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(GENERAL, General.byId(tag.getInt("General")).ordinal());
        entityData.set(YIELDED, tag.getBoolean("Yielded"));
        entityData.set(RECRUITED, tag.getBoolean("Recruited"));
        raidId = tag.contains("Raid") ? tag.getInt("Raid") : -1;
        hallPos = tag.contains("Hall") ? NbtUtils.readBlockPos(tag.getCompound("Hall")) : null;
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        woundTicks = tag.getInt("Wound");
        enraged = tag.getBoolean("Enraged");
        duelOffered = tag.getBoolean("DuelOffered");
        role = WarRole.byId(tag.getInt("Role"));
        foe = tag.contains("Foe") ? Kingdom.byId(tag.getInt("Foe")) : null;
        following = tag.getBoolean("Following");
        bossBar.setName(getDisplayName());
        bossBar.setVisible(canFight() && !isRecruited());
        if (isRecruited() && hallPos != null) {
            restrictTo(hallPos, 32);
        }
    }
}
