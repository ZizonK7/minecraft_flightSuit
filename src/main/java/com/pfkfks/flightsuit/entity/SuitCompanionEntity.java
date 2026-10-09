package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.entity.ai.CompanionCombatGoal;
import com.pfkfks.flightsuit.entity.ai.CompanionFollowGoal;
import com.pfkfks.flightsuit.entity.ai.CompanionTargetGoal;
import com.pfkfks.flightsuit.entity.ai.SuitMoveControl;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitUpManager;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * An empty suit acting on its own (DESIGN.md 3-2 "동료 모드"): left behind when the player steps out of it,
 * it follows its owner, defends them with repulsors and fists, and can be boarded again.
 *
 * The suit pieces live in the entity's real armor slots, so the vanilla armor layer draws them with the
 * same models as when worn and their energy / durability carry over untouched. "Health" is the pieces'
 * durability: hits wear the pieces down instead of killing the entity, and a breaking piece sends the suit
 * home for repairs.
 */
public class SuitCompanionEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(SuitCompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> POWERED = SynchedEntityData.defineId(SuitCompanionEntity.class, EntityDataSerializers.BOOLEAN);
    /** In repulsor flight (hovering / flying), as decided by the server - drives the hover pose on clients. */
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(SuitCompanionEntity.class, EntityDataSerializers.BOOLEAN);
    /** Back split open for the owner to step in (boarding sequence). */
    private static final EntityDataAccessor<Boolean> OPENING = SynchedEntityData.defineId(SuitCompanionEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int IDLE_DRAIN = 1;
    public static final int MOVE_DRAIN = 2;
    private static final double PARK_DISTANCE = 64.0D;

    private UUID ownerId;
    /** Set while flying to the owner to be worn again. */
    private boolean boarding;
    private int boardingTicks;
    /** >= 0 while flying off home; counts up. */
    private int homeTicks = -1;
    private Vec3 homeDirection = Vec3.ZERO;
    private int aimTicks;
    private LivingEntity commandTarget;
    /** Streaking in from far away (summoned / left far behind) - flies straight through, no AI. */
    private boolean arriving;
    /** Held in place by the boarding sequence while the owner steps in. */
    private boolean held;
    /** Client: ticks since the back opened, for the opening animation. */
    public int openTicks;

    public SuitCompanionEntity(EntityType<? extends SuitCompanionEntity> type, Level level) {
        super(type, level);
        this.moveControl = new SuitMoveControl(this);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            // Always drop the real pieces, undamaged by the drop roll, if the entity is ever removed by death.
            this.setDropChance(slot, 2.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static SuitCompanionEntity spawn(ServerPlayer owner, Map<EquipmentSlot, ItemStack> parts, Vec3 pos, float yaw) {
        SuitCompanionEntity suit = new SuitCompanionEntity(ModEntities.SUIT_COMPANION.get(), owner.level());
        suit.ownerId = owner.getUUID();
        for (Map.Entry<EquipmentSlot, ItemStack> entry : parts.entrySet()) {
            suit.setItemSlot(entry.getKey(), entry.getValue());
        }
        suit.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        suit.setYHeadRot(yaw);
        suit.setYBodyRot(yaw);
        owner.level().addFreshEntity(suit);
        return suit;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AIMING, false);
        this.entityData.define(POWERED, true);
        this.entityData.define(FLYING, true);
        this.entityData.define(OPENING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CompanionCombatGoal(this));
        this.goalSelector.addGoal(2, new CompanionFollowGoal(this));
        this.targetSelector.addGoal(1, new CompanionTargetGoal(this));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers());
    }

    // ---------------------------------------------------------------- state

    public UUID getOwnerId() {
        return ownerId;
    }

    public Player getOwner() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    public boolean isOwnedBy(Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    public boolean isPowered() {
        return entityData.get(POWERED);
    }

    public boolean isFlying() {
        return entityData.get(FLYING);
    }

    public boolean isAiming() {
        return entityData.get(AIMING);
    }

    /** Busy with something that overrides normal follow/fight behavior. */
    public boolean isBusy() {
        return boarding || arriving || held || homeTicks >= 0 || !isPowered();
    }

    public boolean isBoarding() {
        return boarding;
    }

    public void startBoarding() {
        boarding = true;
        boardingTicks = 0;
        setTarget(null);
    }

    public boolean isOpening() {
        return entityData.get(OPENING);
    }

    /** Boarding sequence: hold still, facing {@code yaw}, with the back split open. */
    public void holdOpen(float yaw) {
        held = true;
        boarding = false;
        setDeltaMovement(Vec3.ZERO);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        entityData.set(OPENING, true);
    }

    /** Boarding interrupted: close up and go back to being a companion. */
    public void release() {
        held = false;
        entityData.set(OPENING, false);
    }

    /** Fly in from wherever it is now (far away) instead of popping in next to the owner. */
    public void startArrival() {
        arriving = true;
        noPhysics = true;
        setTarget(null);
    }

    public boolean isArriving() {
        return arriving;
    }

    public void setCommandTarget(LivingEntity target) {
        this.commandTarget = target;
        setTarget(target);
    }

    public LivingEntity getCommandTarget() {
        if (commandTarget != null && !commandTarget.isAlive()) {
            commandTarget = null;
        }
        return commandTarget;
    }

    public void markAiming() {
        aimTicks = 12;
        entityData.set(AIMING, true);
    }

    public Map<EquipmentSlot, ItemStack> takeParts() {
        Map<EquipmentSlot, ItemStack> parts = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty()) {
                parts.put(slot, stack);
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        return parts;
    }

    /** Spends suit energy from the chest piece (creative owners pay nothing, like when worn). */
    public boolean drain(int amount) {
        ItemStack chest = getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SuitArmorItem)) {
            return false;
        }
        Player owner = getOwner();
        if (owner != null && owner.getAbilities().instabuild) {
            return true;
        }
        int energy = SuitEnergy.get(chest);
        if (energy < amount) {
            SuitEnergy.set(chest, 0);
            return false;
        }
        SuitEnergy.set(chest, energy - amount);
        return true;
    }

    /** Hand position for repulsor blasts (right palm, roughly). */
    public Vec3 palmPosition() {
        Vec3 look = getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return getEyePosition().add(look.scale(0.7D)).add(right.scale(0.35D)).add(0.0D, -0.3D, 0.0D);
    }

    /** The pieces it's made of, without removing them. */
    public Map<EquipmentSlot, ItemStack> partsView() {
        Map<EquipmentSlot, ItemStack> parts = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (!getItemBySlot(slot).isEmpty()) {
                parts.put(slot, getItemBySlot(slot));
            }
        }
        return parts;
    }

    /** Leaves for its own station (else the main one): lifts off along that bearing, then docks (or drops a capsule). */
    public void goHome() {
        if (homeTicks >= 0) {
            return;
        }
        Player owner = getOwner();
        Vec3 toStation = owner instanceof ServerPlayer serverOwner ? SuitUpManager.homeDirection(serverOwner, partsView(), position()) : null;
        homeDirection = toStation == null ? new Vec3(0.0D, 1.0D, 0.0D) : toStation.normalize();
        homeTicks = 0;
        boarding = false;
        setTarget(null);
        level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            openTicks = isOpening() ? openTicks + 1 : 0;
            if (isFlying()) {
                spawnThrusterParticles();
                // Contrail when streaking in fast, so an arriving suit is visible from far off.
                if (position().subtract(xo, yo, zo).lengthSqr() > 0.5D) {
                    level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.8D, getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
            return;
        }
        if (aimTicks > 0 && --aimTicks == 0) {
            entityData.set(AIMING, false);
        }
        tickPower();
        entityData.set(FLYING, isPowered() && isNoGravity());
        if (held) {
            setDeltaMovement(Vec3.ZERO);
        } else if (homeTicks >= 0) {
            tickHomeFlight();
        } else if (arriving) {
            tickArrival();
        } else if (boarding) {
            tickBoarding();
        }
    }

    /** Rocket in toward the owner's side, fast while far, easing off as it closes in; normal AI resumes nearby. */
    private void tickArrival() {
        Player owner = getOwner();
        if (owner == null || !isPowered()) {
            arriving = false;
            noPhysics = false;
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        Vec3 spot = owner.position().add(-forward.z * 1.5D, 0.5D, forward.x * 1.5D);
        Vec3 to = spot.subtract(position());
        double distance = to.length();
        if (distance < 6.0D) {
            arriving = false;
            noPhysics = false;
            setDeltaMovement(to.normalize().scale(0.3D));
            level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 0.8F);
            return;
        }
        double speed = Math.max(0.6D, Math.min(3.0D, distance * 0.12D));
        Vec3 velocity = to.scale(speed / distance);
        setDeltaMovement(velocity);
        float yaw = (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void tickPower() {
        // Parked: owner gone or far away and nothing to do - land and idle without spending power.
        Player owner = getOwner();
        boolean active = getTarget() != null || boarding || arriving || held || homeTicks >= 0
                || (owner != null && distanceToSqr(owner) < PARK_DISTANCE * PARK_DISTANCE);
        if (!active) {
            setNoGravity(false);
            return;
        }
        if (isPowered() && !isNoGravity()) {
            setNoGravity(true);
        }
        boolean moving = getDeltaMovement().lengthSqr() > 0.01D;
        boolean powered = drain(moving ? MOVE_DRAIN : IDLE_DRAIN);
        if (powered != isPowered()) {
            entityData.set(POWERED, powered);
            setNoGravity(powered);
            if (!powered) {
                setTarget(null);
                level().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.8F, 0.8F);
                if (owner != null) {
                    owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flightsuit.companion_no_power"), true);
                }
            }
        }
    }

    private void tickHomeFlight() {
        homeTicks++;
        // Lift off first, then streak away toward the station.
        Vec3 dir = homeTicks < 8 ? new Vec3(0.0D, 1.0D, 0.0D) : homeDirection.add(0.0D, 0.25D, 0.0D).normalize();
        setDeltaMovement(dir.scale(Math.min(2.0D, 0.2D + homeTicks * 0.08D)));
        if (homeTicks >= 30) {
            Player owner = getOwner();
            SuitUpManager.storeReturningSuit(owner instanceof ServerPlayer serverOwner ? serverOwner : null, this, takeParts());
            discard();
        }
    }

    private void tickBoarding() {
        Player owner = getOwner();
        if (!(owner instanceof ServerPlayer serverOwner) || !owner.isAlive() || WornSuit.of(owner).any()) {
            boarding = false;
            return;
        }
        boardingTicks++;
        // Line up just in front of the owner, facing the same way - back toward them, ready to open.
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        Vec3 spot = owner.position().add(forward.scale(1.8D));
        getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 3.0D);
        getLookControl().setLookAt(spot.add(forward.scale(10.0D)));
        boolean inPlace = position().distanceTo(spot) < 0.5D;
        if (inPlace || boardingTicks > 100 || SuitUpManager.isFallingOrAirborne(serverOwner)) {
            SuitUpManager.boardCompanion(serverOwner, this);
        }
    }

    private void spawnThrusterParticles() {
        double yaw = Math.toRadians(yBodyRot);
        Vec3 side = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        for (int s = -1; s <= 1; s += 2) {
            Vec3 foot = position().add(side.scale(0.12D * s));
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, foot.x, foot.y + 0.05D, foot.z, 0.0D, -0.15D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- damage = durability

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (level().isClientSide || isInvulnerableTo(source)) {
            return false;
        }
        Player owner = getOwner();
        if (owner != null && (source.getEntity() == owner || source.getEntity() instanceof SuitCompanionEntity)) {
            return false;
        }
        boolean hurt = super.hurt(source, Math.min(amount, 1.0F)); // flash + knockback + retaliation, no real health loss
        setHealth(getMaxHealth());
        if (hurt) {
            wearPieces(amount);
        }
        return hurt;
    }

    private void wearPieces(float amount) {
        int wear = Math.max(1, (int) (amount / 4.0F));
        boolean broken = false;
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            if (stack.isDamageableItem()) {
                int damage = Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + wear);
                stack.setDamageValue(damage);
                broken |= SuitArmorItem.isBroken(stack);
            }
        }
        if (broken) {
            Player owner = getOwner();
            if (owner != null) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.flightsuit.companion_damaged"), true);
            }
            goHome();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Owner right-clicks the empty suit: step into it.
        if (hand == InteractionHand.MAIN_HAND && isOwnedBy(player) && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer && !WornSuit.of(player).any()) {
                SuitUpManager.boardCompanion(serverPlayer, this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner");
        }
    }
}
