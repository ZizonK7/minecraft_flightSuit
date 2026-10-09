package com.pfkfks.flightsuit.cleaner;

import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Robot-vacuum cleaner (ported from minebutler's Guard Cleaner). Lives on a {@link CleanerDockBlockEntity};
 * twice a day (or when you tap it) it sweeps the dock's areas row by row, deletes hostile mobs it finds and
 * vacuums up whatever they dropped, then goes home and empties its hopper into the dock's bin.
 *
 * Tap (right-click) to start a run now; sneak + right-click to pick it up. Only players can hurt it -
 * hitting it pops it back into its item.
 */
public class CleanerRobotEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DOCKED = SynchedEntityData.defineId(CleanerRobotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double PICKUP_RADIUS = 1.4D;

    private BlockPos dockPos;
    private long lastRoutineKey = -1L;
    private boolean returnRequested;
    private boolean manualRunRequested;
    private final SimpleContainer hopper = new SimpleContainer(27);

    public CleanerRobotEntity(EntityType<? extends CleanerRobotEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static CleanerRobotEntity spawnAt(ServerLevel level, CleanerDockBlockEntity dock) {
        CleanerRobotEntity robot = new CleanerRobotEntity(ModEntities.CLEANER_ROBOT.get(), level);
        robot.dockPos = dock.getBlockPos();
        Vec3 point = dock.dockPoint();
        robot.moveTo(point.x, point.y, point.z, 0.0F, 0.0F);
        robot.entityData.set(DOCKED, true);
        level.addFreshEntity(robot);
        return robot;
    }

    /** The robot registered to the dock at {@code dockPos}, if it's loaded nearby. */
    public static CleanerRobotEntity findFor(ServerLevel level, BlockPos dockPos) {
        List<CleanerRobotEntity> robots = level.getEntitiesOfClass(CleanerRobotEntity.class,
                new net.minecraft.world.phys.AABB(dockPos).inflate(96.0D), robot -> dockPos.equals(robot.dockPos));
        return robots.isEmpty() ? null : robots.get(0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DOCKED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CleanerPatrolGoal(this));
        this.goalSelector.addGoal(2, new CleanerReturnGoal(this));
    }

    // ---------------------------------------------------------------- state

    public BlockPos getDockPos() {
        return dockPos;
    }

    public CleanerDockBlockEntity getDock() {
        if (dockPos == null || !level().isLoaded(dockPos)) {
            return null;
        }
        return level().getBlockEntity(dockPos) instanceof CleanerDockBlockEntity dock ? dock : null;
    }

    public boolean isDocked() {
        return entityData.get(DOCKED);
    }

    public void setDocked(boolean docked) {
        entityData.set(DOCKED, docked);
    }

    public long getLastRoutineKey() {
        return lastRoutineKey;
    }

    public void setLastRoutineKey(long key) {
        lastRoutineKey = key;
    }

    public boolean consumeManualRunRequest() {
        boolean requested = manualRunRequested;
        manualRunRequested = false;
        return requested;
    }

    public void requestReturn() {
        returnRequested = true;
    }

    public boolean isReturnRequested() {
        return returnRequested;
    }

    public void clearReturnRequest() {
        returnRequested = false;
    }

    public SimpleContainer getHopper() {
        return hopper;
    }

    public boolean isHopperNearlyFull() {
        int used = 0;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            if (!hopper.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used >= hopper.getContainerSize() - 3;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && !isDocked()) {
            vacuumItems();
        }
    }

    private void vacuumItems() {
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(PICKUP_RADIUS),
                item -> item.isAlive() && !item.hasPickUpDelay());
        for (ItemEntity item : items) {
            ItemStack remainder = hopper.addItem(item.getItem().copy());
            if (remainder.getCount() != item.getItem().getCount()) {
                take(item, item.getItem().getCount() - remainder.getCount());
                if (remainder.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(remainder);
                }
                level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.6F);
            }
        }
    }

    // ---------------------------------------------------------------- interaction / damage

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide) {
            if (player.isShiftKeyDown()) {
                popIntoItem(player);
            } else {
                requestManualRun(player);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Tap to clean now. Creative players' runs are free so it can be tried without building power. */
    private void requestManualRun(Player player) {
        CleanerDockBlockEntity dock = getDock();
        String problem = null;
        if (dock == null) {
            problem = "message.flightsuit.cleaner_no_dock";
        } else if (dock.getAreas().isEmpty()) {
            problem = "message.flightsuit.cleaner_no_areas";
        } else if (!player.isCreative() && dock.energy().getEnergyStored() < CleanerDockBlockEntity.ROUTINE_COST) {
            problem = "message.flightsuit.cleaner_low_power";
        }
        if (problem != null) {
            player.displayClientMessage(Component.translatable(problem), true);
            return;
        }
        if (player.isCreative()) {
            dock.energy().generate(CleanerDockBlockEntity.ROUTINE_COST);
        }
        manualRunRequested = true;
        level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.6F, 1.6F);
        player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_run_requested"), true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!level().isClientSide && source.getEntity() instanceof Player player) {
            popIntoItem(player);
            return true;
        }
        return false;
    }

    /** Back into its item (the hopper's contents spill out). */
    private void popIntoItem(Player player) {
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            ItemStack stack = hopper.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        ItemStack item = new ItemStack(ModItems.CLEANER_ROBOT.get());
        if (!player.getInventory().add(item)) {
            spawnAtLocation(item);
        }
        level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
        discard();
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
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isDocked();
    }

    // ---------------------------------------------------------------- save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (dockPos != null) {
            tag.putLong("Dock", dockPos.asLong());
        }
        tag.putLong("LastRoutine", lastRoutineKey);
        tag.putBoolean("Docked", isDocked());
        tag.put("Hopper", hopper.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dockPos = tag.contains("Dock") ? BlockPos.of(tag.getLong("Dock")) : null;
        lastRoutineKey = tag.getLong("LastRoutine");
        setDocked(tag.getBoolean("Docked"));
        hopper.fromTag(tag.getList("Hopper", 10));
    }
}
