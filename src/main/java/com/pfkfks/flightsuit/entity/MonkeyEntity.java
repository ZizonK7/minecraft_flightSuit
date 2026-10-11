package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.suit.KiShots;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * M18 Cannon Shooter's monkey (CannonArts): at heel while the full Mark 6 is worn - hopping after its owner, floating
 * at their shoulder when they fly, popping back beside them when left behind - and the body of three of the suit's
 * moves: planted as a turret (Monkey Militia), charging down a line (Monkey Rush). Never saved; gone when the suit
 * comes off, and the move's monkeys go up in a puff when the move ends.
 */
public class MonkeyEntity extends PathfinderMob {
    public static final byte HEEL = 0;
    public static final byte TURRET = 1;
    public static final byte RUSH = 2;

    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(MonkeyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> FLOATING = SynchedEntityData.defineId(MonkeyEntity.class, EntityDataSerializers.BOOLEAN);

    private @Nullable UUID ownerId;
    /** TURRET / RUSH: ticks left; RUSH: the way it runs and whom it has already bowled over. */
    private int left;
    private Vec3 rushDir = Vec3.ZERO;
    private final Set<LivingEntity> rushHit = new HashSet<>();
    private int hop;

    public MonkeyEntity(EntityType<? extends MonkeyEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    /** The owner's monkey at heel, put down beside them. */
    public static MonkeyEntity heel(ServerLevel level, Player owner) {
        MonkeyEntity monkey = new MonkeyEntity(com.pfkfks.flightsuit.registry.ModEntities.MONKEY.get(), level);
        monkey.ownerId = owner.getUUID();
        Vec3 side = Vec3.directionFromRotation(0.0F, owner.getYRot() + 90.0F);
        Vec3 at = owner.position().add(side.scale(0.9D));
        monkey.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        level.addFreshEntity(monkey);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4D, at.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
        return monkey;
    }

    /** A turret for {@code ticks} at {@code at}. */
    public static MonkeyEntity turret(ServerLevel level, Player owner, Vec3 at, int ticks) {
        MonkeyEntity monkey = new MonkeyEntity(com.pfkfks.flightsuit.registry.ModEntities.MONKEY.get(), level);
        monkey.ownerId = owner.getUUID();
        monkey.entityData.set(MODE, TURRET);
        monkey.left = ticks;
        monkey.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
        level.addFreshEntity(monkey);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4D, at.z, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        return monkey;
    }

    /** One of the rush: runs {@code dir} from {@code at} for {@code ticks}. */
    public static MonkeyEntity rush(ServerLevel level, Player owner, Vec3 at, Vec3 dir, int ticks) {
        MonkeyEntity monkey = new MonkeyEntity(com.pfkfks.flightsuit.registry.ModEntities.MONKEY.get(), level);
        monkey.ownerId = owner.getUUID();
        monkey.entityData.set(MODE, RUSH);
        monkey.left = ticks;
        monkey.rushDir = new Vec3(dir.x, 0.0D, dir.z).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(-monkey.rushDir.x, monkey.rushDir.z)));
        monkey.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        monkey.setYBodyRot(yaw);
        monkey.setYHeadRot(yaw);
        level.addFreshEntity(monkey);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3D, at.z, 4, 0.2D, 0.1D, 0.2D, 0.02D);
        return monkey;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MODE, HEEL);
        entityData.define(FLOATING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public byte mode() {
        return entityData.get(MODE);
    }

    /** Floating at the shoulder (for the renderer: legs tucked). */
    public boolean floating() {
        return entityData.get(FLOATING);
    }

    public @Nullable Player getOwner() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(ownerId);
    }

    /** The owner's own monkey (not the moves'), if one of theirs is loaded near them. */
    public static Optional<MonkeyEntity> of(ServerLevel level, Player owner) {
        return level.getEntitiesOfClass(MonkeyEntity.class, owner.getBoundingBox().inflate(64.0D),
                monkey -> monkey.isOwnedBy(owner) && monkey.mode() == HEEL).stream().findFirst();
    }

    private boolean foe(LivingEntity entity) {
        Player owner = getOwner();
        if (!entity.isAlive() || entity == this || entity == owner || entity instanceof MonkeyEntity) {
            return false;
        }
        if (owner instanceof ServerPlayer player && SuitWeapons.isFriendly(player, entity)) {
            return false;
        }
        return ((owner != null ? SuitWeapons.isHostile(owner, entity) : entity instanceof Enemy) || owner != null && entity.getLastHurtByMob() == owner)
                && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        Player owner = getOwner();
        switch (mode()) {
            case TURRET -> tickTurret(owner);
            case RUSH -> tickRush(owner);
            default -> tickHeel(owner);
        }
    }

    // ---------------------------------------------------------------- at heel

    private void tickHeel(@Nullable Player owner) {
        if (owner == null || !owner.isAlive() || owner.level() != level()
                || SuitWeapons.armedClass(owner) != SuitClass.CANNONEER || !com.pfkfks.flightsuit.suit.WornSuit.of(owner).fullSet()) {
            poof();
            return;
        }
        double distance = distanceTo(owner);
        boolean ownerFlying = owner.getAbilities().flying || !owner.onGround() && owner.fallDistance < 0.1F && !owner.isInWater();
        if (distance > 24.0D) {
            // Left behind: it just turns up again at their side.
            Vec3 side = Vec3.directionFromRotation(0.0F, owner.getYRot() + 90.0F);
            Vec3 at = owner.position().add(side.scale(0.9D));
            moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0.0F;
            ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4D, at.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
            return;
        }
        boolean floating = ownerFlying && owner.getAbilities().flying;
        if (floating != floating()) {
            entityData.set(FLOATING, floating);
            setNoGravity(floating);
        }
        if (floating) {
            // Riding the air by their left shoulder.
            Vec3 side = Vec3.directionFromRotation(0.0F, owner.getYRot() + 90.0F);
            Vec3 want = owner.position().add(side.scale(0.8D)).add(0.0D, 1.1D, 0.0D).subtract(Vec3.directionFromRotation(0.0F, owner.getYRot()).scale(0.4D));
            Vec3 to = want.subtract(position());
            setDeltaMovement(to.scale(0.35D));
            getNavigation().stop();
            setYRot(owner.getYRot());
            setYBodyRot(owner.getYRot());
            fallDistance = 0.0F;
            return;
        }
        fallDistance = 0.0F;
        if (distance > 2.6D) {
            if (tickCount % 5 == 0) {
                getNavigation().moveTo(owner, distance > 8.0D ? 1.5D : 1.1D);
            }
            // A hop every few steps.
            if (onGround() && ++hop % 9 == 0) {
                setDeltaMovement(getDeltaMovement().add(0.0D, 0.32D, 0.0D));
            }
        } else if (tickCount % 10 == 0) {
            getNavigation().stop();
        }
        if (random.nextInt(600) == 0) {
            playSound(SoundEvents.PANDA_AMBIENT, 0.5F, 1.9F);
        }
    }

    // ---------------------------------------------------------------- Monkey Militia (turret)

    private void tickTurret(@Nullable Player owner) {
        if (--left <= 0 || owner == null || owner.level() != level()) {
            poof();
            return;
        }
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
        if (tickCount % SuitTuning.MILITIA_INTERVAL != 0) {
            return;
        }
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SuitTuning.MILITIA_RANGE), this::foe)
                .stream().filter(this::hasLineOfSight).min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) {
            return;
        }
        getLookControl().setLookAt(target, 60.0F, 60.0F);
        Vec3 muzzle = position().add(0.0D, getBbHeight() * 0.7D, 0.0D);
        Vec3 dir = target.getBoundingBox().getCenter().subtract(muzzle).normalize();
        swing(InteractionHand.MAIN_HAND);
        KiShots.fire((ServerLevel) level(), owner, KiShots.Style.FIRE, muzzle.add(dir.scale(0.4D)), dir, 1.8D, SuitTuning.MILITIA_RANGE + 4.0D,
                SuitTuning.MILITIA_DAMAGE, 0.6D, this::foe);
    }

    // ---------------------------------------------------------------- Monkey Rush

    private void tickRush(@Nullable Player owner) {
        if (--left <= 0 || owner == null || owner.level() != level()) {
            poof();
            return;
        }
        getNavigation().stop();
        Vec3 step = rushDir.scale(0.7D);
        // Up a step if a block's in the way; stopped dead by a wall.
        setDeltaMovement(step.x, onGround() && horizontalCollision ? 0.42D : getDeltaMovement().y, step.z);
        hurtMarked = true;
        if (horizontalCollision && !onGround()) {
            left = Math.min(left, 2);
        }
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1D, getZ(), 1, 0.1D, 0.0D, 0.1D, 0.01D);
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SuitTuning.RUSH_WIDTH * 0.5D, 0.5D, SuitTuning.RUSH_WIDTH * 0.5D),
                entity -> foe(entity) && !rushHit.contains(entity))) {
            rushHit.add(victim);
            victim.invulnerableTime = 0;
            victim.hurt(owner instanceof ServerPlayer player ? damageSources().playerAttack(player) : damageSources().mobAttack(this), SuitTuning.RUSH_DAMAGE);
            victim.knockback(1.0D, -rushDir.x, -rushDir.z);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
            victim.hurtMarked = true;
            swing(InteractionHand.MAIN_HAND);
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    /** Gone in a puff of smoke. */
    public void poof() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.4D, getZ(), 8, 0.2D, 0.3D, 0.2D, 0.02D);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Its owner's own blasts never touch it; otherwise it's a small thing that can be knocked about.
        if (source.getEntity() instanceof Player player && isOwnedBy(player)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return mode() == HEEL && !floating();
    }

    @Override
    protected void pushEntities() {
        if (mode() == HEEL && !floating()) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isNoAi() {
        return mode() != HEEL;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PANDA_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.PANDA_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return 1.8F;
    }
}
