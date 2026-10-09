package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.planet.PlanetStory;
import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Dragon Ball Earth character (DESIGN.md 4-16, M15). NPCs (Bulma) only talk; Goku talks at Kame House and
 * fights beside you at the crater; the Saiyans are boss fights with their signature moves (ki blasts, Raditz's
 * dash, Nappa's blast wave, Vegeta's Galick Gun); Saibamen swarm and blow themselves up. Bosses and Goku don't
 * die - beaten, they withdraw (the story moves on). Never saved: DbzEarth puts people back while someone's near.
 */
public class DbzFighterEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIGHTING = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private @Nullable BlockPos home;
    private int blastCooldown = 40;
    private int specialCooldown = 160;
    private int charging;
    private boolean gone;

    public DbzFighterEntity(EntityType<? extends DbzFighterEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        bossBar.setVisible(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    /** @param fighting Goku: fighting beside you (else waiting at Kame House to talk) */
    public static DbzFighterEntity create(ServerLevel level, DbzCharacter type, BlockPos home, boolean fighting) {
        DbzFighterEntity fighter = new DbzFighterEntity(ModEntities.DBZ_FIGHTER.get(), level);
        fighter.entityData.set(TYPE, type.ordinal());
        fighter.entityData.set(FIGHTING, type.isFoe() || fighting);
        fighter.home = home.immutable();
        set(fighter, Attributes.MAX_HEALTH, type.health());
        set(fighter, Attributes.ATTACK_DAMAGE, Math.max(1.0D, type.damage()));
        set(fighter, Attributes.MOVEMENT_SPEED, type.speed());
        fighter.setHealth(fighter.getMaxHealth());
        fighter.setCustomName(type.displayName());
        fighter.setCustomNameVisible(type.role() != DbzCharacter.Role.MINION);
        if (type.role() == DbzCharacter.Role.NPC || !fighter.isFighting()) {
            fighter.restrictTo(home, 6);
        }
        if (type.role() == DbzCharacter.Role.BOSS) {
            fighter.bossBar.setName(type.displayName());
            fighter.bossBar.setVisible(true);
        }
        fighter.refreshDimensions();
        return fighter;
    }

    private static void set(DbzFighterEntity fighter, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = fighter.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, 0);
        entityData.define(FIGHTING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true) {
            @Override
            public boolean canUse() {
                return isFighting() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isFighting() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, DbzFighterEntity.class) {
            @Override
            public boolean canUse() {
                return isFighting() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::isEnemy) {
            @Override
            public boolean canUse() {
                return isFighting() && super.canUse();
            }
        });
    }

    public DbzCharacter getCharacter() {
        return DbzCharacter.byId(entityData.get(TYPE));
    }

    public boolean isFighting() {
        return entityData.get(FIGHTING);
    }

    public @Nullable BlockPos getHome() {
        return home;
    }

    /** Saiyans and Saibamen go for players (and their suits) and Goku; Goku goes for them. */
    public boolean isEnemy(LivingEntity other) {
        if (!other.isAlive() || other == this) {
            return false;
        }
        DbzCharacter me = getCharacter();
        if (other instanceof DbzFighterEntity fighter) {
            return me.isFoe() != fighter.getCharacter().isFoe() && fighter.isFighting();
        }
        if (!me.isFoe()) {
            return false;
        }
        if (other instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return other instanceof SuitCompanionEntity;
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
        return super.getDimensions(pose).scale(getCharacter().scale());
    }

    // ---------------------------------------------------------------- moves

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || !isFighting()) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (blastCooldown > 0) {
            blastCooldown--;
        }
        if (specialCooldown > 0) {
            specialCooldown--;
        }
        if (charging > 0) {
            chargeTick(server, target);
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        boolean sees = getSensing().hasLineOfSight(target);
        DbzCharacter me = getCharacter();
        switch (me) {
            case RADITZ -> {
                if (specialCooldown <= 0 && distance > 10.0D) {
                    dash(server, target);
                    specialCooldown = 100;
                } else if (blastCooldown <= 0 && sees && distance > 3.0D && distance < 24.0D) {
                    kiBlast(server, target, 6.0F, getHealth() < getMaxHealth() / 2 ? 2 : 1);
                    blastCooldown = 60;
                }
            }
            case NAPPA -> {
                if (specialCooldown <= 0 && distance < 7.0D) {
                    blastWave(server, 6.0D, 11.0F);
                    specialCooldown = 120;
                } else if (blastCooldown <= 0 && sees && distance < 24.0D) {
                    kiBlast(server, target, 8.0F, 1);
                    blastCooldown = 80;
                }
            }
            case VEGETA -> {
                if (specialCooldown <= 0 && sees && distance < 30.0D) {
                    charging = 40;
                    say(server, me.line("galick"));
                } else if (blastCooldown <= 0 && sees && distance < 26.0D) {
                    kiBlast(server, target, 7.0F, getHealth() < getMaxHealth() / 2 ? 3 : 2);
                    blastCooldown = 50;
                }
                if (specialCooldown <= 0 && distance > 12.0D && !sees) {
                    dash(server, target);
                    specialCooldown = 60;
                }
            }
            case GOKU -> {
                if (specialCooldown <= 0 && sees && distance < 28.0D) {
                    charging = 30;
                    say(server, me.line("kamehameha"));
                } else if (blastCooldown <= 0 && sees && distance > 4.0D && distance < 22.0D) {
                    kiBlast(server, target, 6.0F, 1);
                    blastCooldown = 70;
                }
            }
            case SAIBAMAN -> {
                if (getHealth() < getMaxHealth() * 0.35F && distance < 2.5D) {
                    selfDestruct(server);
                }
            }
            default -> {
            }
        }
    }

    /** Vegeta's Galick Gun / Goku's Kamehameha: gathering energy, then a wide beam. */
    private void chargeTick(ServerLevel server, @Nullable LivingEntity target) {
        getNavigation().stop();
        boolean vegeta = getCharacter() == DbzCharacter.VEGETA;
        ParticleOptions glow = vegeta ? ParticleTypes.WITCH : ParticleTypes.SOUL_FIRE_FLAME;
        server.sendParticles(glow, getX(), getY() + 1.0D, getZ(), 6, 0.6D, 0.6D, 0.6D, 0.05D);
        if (target != null) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (--charging > 0) {
            return;
        }
        specialCooldown = vegeta ? 240 : 160;
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 from = getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        Vec3 to = from.add(dir.scale(30.0D));
        float damage = vegeta ? 18.0F : 14.0F;
        for (int i = 0; i <= 60; i++) {
            Vec3 at = from.add(dir.scale(i * 0.5D));
            server.sendParticles(vegeta ? ParticleTypes.DRAGON_BREATH : ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.25D, 0.25D, 0.25D, 0.0D);
        }
        AABB lane = new AABB(from, to).inflate(1.5D);
        for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, lane, this::isEnemy)) {
            Vec3 rel = hit.position().add(0.0D, hit.getBbHeight() / 2.0D, 0.0D).subtract(from);
            double along = rel.dot(dir);
            if (along > 0.0D && rel.subtract(dir.scale(along)).length() < 1.8D) {
                hit.hurt(damageSources().mobAttack(this), damage);
                server.sendParticles(ParticleTypes.EXPLOSION, hit.getX(), hit.getY() + 1.0D, hit.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, vegeta ? 0.6F : 0.9F);
    }

    private void kiBlast(ServerLevel server, LivingEntity target, float damage, int count) {
        swing(InteractionHand.MAIN_HAND);
        for (int n = 0; n < count; n++) {
            LivingEntity victim = n == 0 ? target : nearestOtherEnemy(server, target);
            if (victim == null) {
                victim = target;
            }
            Vec3 from = getEyePosition();
            Vec3 to = victim.getEyePosition();
            Vec3 step = to.subtract(from);
            int points = Math.max(4, (int) (step.length() * 2.0D));
            for (int i = 0; i <= points; i++) {
                Vec3 at = from.add(step.scale(i / (double) points));
                server.sendParticles(getCharacter() == DbzCharacter.GOKU ? ParticleTypes.END_ROD : ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            victim.hurt(damageSources().mobAttack(this), damage);
            server.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y - 0.5D, to.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 1.5F);
    }

    private @Nullable LivingEntity nearestOtherEnemy(ServerLevel server, LivingEntity not) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20.0D), this::isEnemy)) {
            double d = other.distanceToSqr(this);
            if (other != not && d < bestDist) {
                best = other;
                bestDist = d;
            }
        }
        return best;
    }

    /** Saiyans close in at speed: a blur, and they're beside you. */
    private void dash(ServerLevel server, LivingEntity target) {
        Vec3 to = target.position().subtract(position()).normalize().scale(Math.min(distanceTo(target) - 2.0D, 10.0D)).add(position());
        server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0D, getZ(), 10, 0.3D, 0.6D, 0.3D, 0.05D);
        if (randomTeleport(to.x, target.getY(), to.z, false)) {
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
        }
    }

    /** Nappa: a blast wave from where he stands (no blocks broken). */
    private void blastWave(ServerLevel server, double radius, float damage) {
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isEnemy)) {
            hit.hurt(damageSources().mobAttack(this), damage);
            Vec3 push = hit.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(1.4D);
                hit.push(push.x, 0.6D, push.z);
                hit.hurtMarked = true;
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
        say(server, getCharacter().line("blast"));
    }

    private void selfDestruct(ServerLevel server) {
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0D), this::isEnemy)) {
            hit.hurt(damageSources().mobAttack(this), 8.0F);
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 1.2F);
        gone = true;
        discard();
    }

    private void say(ServerLevel server, Component line) {
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0D))) {
            player.sendSystemMessage(line);
        }
    }

    // ---------------------------------------------------------------- damage, defeat, talk

    @Override
    public boolean hurt(DamageSource source, float amount) {
        DbzCharacter me = getCharacter();
        if (!isFighting() || source.getEntity() instanceof DbzFighterEntity other && other.getCharacter().isFoe() == me.isFoe()) {
            return false;
        }
        // Goku only takes hits from the Saiyans' side.
        if (me == DbzCharacter.GOKU && !(source.getEntity() instanceof DbzFighterEntity)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Bosses and Goku don't die: beaten, they withdraw - the Saiyans' defeat moves the story on. */
    @Override
    public void die(DamageSource source) {
        DbzCharacter me = getCharacter();
        if (level().isClientSide || me == DbzCharacter.SAIBAMAN || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(level() instanceof ServerLevel server)) {
            super.die(source);
            return;
        }
        if (gone) {
            return;
        }
        gone = true;
        setHealth(1.0F);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 20, 0.4D, 0.8D, 0.4D, 0.03D);
        say(server, me.line("beaten"));
        if (me.role() == DbzCharacter.Role.BOSS) {
            PlanetStory.onBossBeaten(server, this, source.getEntity());
        }
        discard();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || isFighting()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PlanetStory.talk(serverPlayer, getCharacter());
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
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Character", getCharacter().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, DbzCharacter.byId(tag.getInt("Character")).ordinal());
    }
}
