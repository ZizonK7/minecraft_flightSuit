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
 * at their shoulder when they fly, popping back beside them when left behind. Since 2026-10-12 (user: "같이 다니는
 * 원숭이의 무기를 강화... 평소에는 권총 쏘다가 기관총") it fights at heel: a pistol shot now and then at whatever its
 * owner fights, and X turns it into a machine gun for a while. Never saved; gone when the suit comes off.
 */
public class MonkeyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> FLOATING = SynchedEntityData.defineId(MonkeyEntity.class, EntityDataSerializers.BOOLEAN);
    /** Gun up (aiming at something) - the pose; on the machine gun - shaking with it. */
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(MonkeyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MACHINE_GUN = SynchedEntityData.defineId(MonkeyEntity.class, EntityDataSerializers.BOOLEAN);

    private @Nullable UUID ownerId;
    private int hop;
    /** The machine gun's ticks left; ticks to the next shot; ticks the gun stays up after the last. */
    private int machineGunLeft;
    private int reload;
    private int aimLeft;

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
        monkey.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        monkey.arm(false);
        level.addFreshEntity(monkey);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4D, at.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
        return monkey;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FLOATING, false);
        entityData.define(AIMING, false);
        entityData.define(MACHINE_GUN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Gun up (for the model: arms forward). */
    public boolean aiming() {
        return entityData.get(AIMING);
    }

    /** On the machine gun (for the model: shaking with the recoil). */
    public boolean machineGun() {
        return entityData.get(MACHINE_GUN);
    }

    /** The gun in its hand (2026-10-12, 3D props from tools/ModelGen): the pistol, or the machine gun. */
    private void arm(boolean machine) {
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(machine
                ? com.pfkfks.flightsuit.registry.ModItems.MONKEY_MACHINE_GUN.get() : com.pfkfks.flightsuit.registry.ModItems.MONKEY_PISTOL.get()));
    }

    /** X: the machine gun for {@code ticks}. */
    public void startMachineGun(int ticks) {
        machineGunLeft = ticks;
        reload = 0;
        entityData.set(MACHINE_GUN, true);
        arm(true);
        if (level() instanceof ServerLevel level) {
            com.pfkfks.flightsuit.fx.KiFx.aura(level, this, com.pfkfks.flightsuit.fx.KiFx.FIRE, ticks);
            level.playSound(null, blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.6F);
            level.playSound(null, blockPosition(), SoundEvents.PANDA_AMBIENT, SoundSource.PLAYERS, 0.8F, 2.0F);
        }
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
                monkey -> monkey.isOwnedBy(owner) && monkey.isAlive()).stream().findFirst();
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
        tickHeel(getOwner());
    }

    // ---------------------------------------------------------------- at heel

    private void tickHeel(@Nullable Player owner) {
        if (owner == null || !owner.isAlive() || owner.level() != level()
                || SuitWeapons.armedClass(owner) != SuitClass.CANNONEER || !com.pfkfks.flightsuit.suit.WornSuit.of(owner).fullSet()) {
            poof();
            return;
        }
        tickGun(owner);
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

    // ---------------------------------------------------------------- the gun (2026-10-12)

    /** A pistol shot every so often at its owner's fight; on the machine gun, a stream of them. */
    private void tickGun(Player owner) {
        if (machineGunLeft > 0 && --machineGunLeft == 0) {
            entityData.set(MACHINE_GUN, false);
            arm(false);
        }
        if (aimLeft > 0 && --aimLeft == 0) {
            entityData.set(AIMING, false);
        }
        if (--reload > 0) {
            return;
        }
        boolean machine = machineGunLeft > 0;
        double range = machine ? SuitTuning.MONKEY_MG_RANGE : SuitTuning.MONKEY_PISTOL_RANGE;
        LivingEntity target = owner.getLastHurtMob();
        if (target == null || !foe(target) || target.distanceTo(this) > range || !hasLineOfSight(target)
                || owner.tickCount - owner.getLastHurtMobTimestamp() > 200) {
            target = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), this::foe).stream()
                    .filter(entity -> entity.distanceTo(this) <= range && hasLineOfSight(entity))
                    .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        if (target == null) {
            reload = 5;
            return;
        }
        reload = machine ? SuitTuning.MONKEY_MG_INTERVAL : SuitTuning.MONKEY_PISTOL_INTERVAL;
        aimLeft = 20;
        entityData.set(AIMING, true);
        lookAt(target, 360.0F, 360.0F);
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        shoot(owner, target, machine ? SuitTuning.MONKEY_MG_DAMAGE : SuitTuning.MONKEY_PISTOL_DAMAGE, machine);
    }

    /** One round: hitscan, a thin tracer from the muzzle to where it struck (like the Mark 1's shoulder guns). */
    private void shoot(Player owner, LivingEntity target, float damage, boolean machine) {
        ServerLevel level = (ServerLevel) level();
        Vec3 muzzle = position().add(0.0D, getBbHeight() * 0.65D, 0.0D).add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.3D));
        Vec3 dir = target.getBoundingBox().getCenter().subtract(muzzle).normalize();
        if (machine) {
            // A little spray.
            dir = dir.add((random.nextDouble() - 0.5D) * 0.06D, (random.nextDouble() - 0.5D) * 0.06D, (random.nextDouble() - 0.5D) * 0.06D).normalize();
        }
        Vec3 far = muzzle.add(dir.scale(SuitTuning.MONKEY_MG_RANGE + 4.0D));
        net.minecraft.world.phys.BlockHitResult block = level.clip(new net.minecraft.world.level.ClipContext(muzzle, far,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : block.getLocation();
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, this, muzzle, end,
                new net.minecraft.world.phys.AABB(muzzle, end).inflate(0.4D), entity -> entity instanceof LivingEntity living && foe(living));
        int colour = 0xFFE9A0;
        if (hit != null) {
            end = hit.getLocation();
            LivingEntity victim = (LivingEntity) hit.getEntity();
            victim.invulnerableTime = 0;
            victim.hurt(owner instanceof ServerPlayer player ? damageSources().playerAttack(player) : damageSources().mobAttack(this), damage);
            com.pfkfks.flightsuit.fx.KiFx.burst(level, end, colour, 0.4F, 3, com.pfkfks.flightsuit.fx.KiFx.SPARK);
        }
        com.pfkfks.flightsuit.fx.KiFx.beam(level, muzzle, end, colour, machine ? 0.05F : 0.06F, 3, com.pfkfks.flightsuit.fx.KiFx.BEAM_THIN,
                null, com.pfkfks.flightsuit.fx.KiFx.AT_POS, null);
        com.pfkfks.flightsuit.fx.KiFx.burst(level, muzzle, colour, machine ? 0.3F : 0.35F, 2, com.pfkfks.flightsuit.fx.KiFx.FLASH);
        swing(InteractionHand.MAIN_HAND);
        level.playSound(null, blockPosition(), machine ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, machine ? 0.35F : 0.5F, machine ? 1.9F + random.nextFloat() * 0.2F : 1.8F);
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
        return !floating();
    }

    @Override
    protected void pushEntities() {
        if (!floating()) {
            super.pushEntities();
        }
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
