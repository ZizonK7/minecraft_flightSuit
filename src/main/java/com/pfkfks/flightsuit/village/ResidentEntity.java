package com.pfkfks.flightsuit.village;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.ResidentScreenS2CPacket;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.village.ai.BuilderWorkGoal;
import com.pfkfks.flightsuit.village.ai.FarmerWorkGoal;
import com.pfkfks.flightsuit.village.ai.GuardHurtByTargetGoal;
import com.pfkfks.flightsuit.village.ai.GuardMeleeGoal;
import com.pfkfks.flightsuit.village.ai.GuardPatrolGoal;
import com.pfkfks.flightsuit.village.ai.GuardTargetGoal;
import com.pfkfks.flightsuit.village.ai.ResidentDownedGoal;
import com.pfkfks.flightsuit.village.ai.ResidentFleeGoal;
import com.pfkfks.flightsuit.village.ai.ResidentShelterGoal;
import com.pfkfks.flightsuit.village.ai.ResidentSleepGoal;
import com.pfkfks.flightsuit.village.ai.ResidentStrollGoal;
import com.pfkfks.flightsuit.village.ai.SoldierRallyGoal;
import com.pfkfks.flightsuit.village.ai.WandererGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A villager of your own (DESIGN.md 4-12): a named person with a job, a bed, a daily meal and a mood. Comes
 * in as a wanderer (unemployed clothes) and joins if you take them in; you give them a job by talking to them.
 *
 * Never dies outright: at zero health they go down, and unless someone gives them first aid (right-click
 * with food) within VillageTuning.DOWNED_TICKS they die for good.
 */
public class ResidentEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WANDERER = SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.BOOLEAN);
    /** Seconds left while downed (0 = up), for the name tag and the lying-down pose. */
    private static final EntityDataAccessor<Integer> DOWNED = SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.INT);

    public static final int ACTION_ACCEPT = 0;
    public static final int ACTION_DISMISS = 1;
    public static final int ACTION_SET_JOB = 2;
    public static final int ACTION_APPROVE = 3;
    public static final int ACTION_TAKE_BLUEPRINT = 4;
    /** arg = queue index, -1 = the hall expansion. */
    public static final int ACTION_CANCEL = 5;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75F, 0.05F, 0.05F), 1.0F);

    private @Nullable BlockPos hallPos;
    private @Nullable UUID ownerId;
    private @Nullable BlockPos homeBed;
    private boolean fed = true;
    private int mood = 60;
    private long leaveAt;
    private boolean leaving;
    private long leftAt;
    private int downedTicks;
    /** Set when the downed timer ran out, so die() lets the death through. */
    private boolean dying;
    /** The farmer's harvest on the way to the storage. */
    private final SimpleContainer pack = new SimpleContainer(9);
    /**
     * Stars (1..5) per job, by job ordinal: what this person is good at. Most jobs come out 1-3, one or two
     * are a gift (4-5) - so it matters who you make the farmer.
     */
    private final int[] talents = new int[ResidentJob.values().length];

    private static final UUID GUARD_HEALTH = UUID.fromString("6b1c2b9e-7f43-4a3e-9d57-1f7e0b8a2c11");
    private static final UUID GUARD_DAMAGE = UUID.fromString("0d5e8a41-3c2f-4b6d-8e9a-5a7c4f2b1d36");

    public ResidentEntity(EntityType<? extends ResidentEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new ResidentDownedGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new GuardMeleeGoal(this));
        goalSelector.addGoal(2, new ResidentFleeGoal(this));
        goalSelector.addGoal(3, new ResidentShelterGoal(this));
        goalSelector.addGoal(3, new SoldierRallyGoal(this));
        goalSelector.addGoal(4, new ResidentSleepGoal(this));
        goalSelector.addGoal(5, new FarmerWorkGoal(this));
        goalSelector.addGoal(5, new BuilderWorkGoal(this));
        goalSelector.addGoal(5, new GuardPatrolGoal(this));
        goalSelector.addGoal(6, new WandererGoal(this));
        goalSelector.addGoal(7, new ResidentStrollGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new GuardHurtByTargetGoal(this));
        targetSelector.addGoal(2, new GuardTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(JOB, ResidentJob.NONE.ordinal());
        entityData.define(WANDERER, false);
        entityData.define(DOWNED, 0);
    }

    // ---- arriving ----

    public static ResidentEntity spawnWanderer(ServerLevel level, VillageHallBlockEntity hall, BlockPos at) {
        ResidentEntity wanderer = new ResidentEntity(ModEntities.RESIDENT.get(), level);
        wanderer.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        wanderer.becomeWandererOf(hall);
        level.addFreshEntity(wanderer);
        hall.announceArrival(wanderer);
        return wanderer;
    }

    private void becomeWandererOf(@Nullable VillageHallBlockEntity hall) {
        rollTalents();
        entityData.set(WANDERER, true);
        setJob(ResidentJob.NONE);
        hallPos = hall != null ? hall.getBlockPos() : null;
        ownerId = hall != null ? hall.getOwner() : null;
        setCustomName(Component.literal(ResidentNames.pick(random, hall != null ? hall.takenNames() : Set.of())));
        leaveAt = level().getGameTime() + VillageTuning.WANDERER_STAY_TICKS;
    }

    /** From a spawn egg: a wanderer heading for the village they were dropped in (if any). */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                  @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (hallPos == null && !hasCustomName()) {
            VillageHallBlockEntity hall = Villages.containing(level.getLevel(), blockPosition());
            becomeWandererOf(hall);
            if (hall != null) {
                hall.announceArrival(this);
            }
        }
        return result;
    }

    // ---- state ----

    public ResidentJob getJob() {
        return ResidentJob.byId(entityData.get(JOB));
    }

    public void setJob(ResidentJob job) {
        if (getJob() == ResidentJob.FARMER && job != ResidentJob.FARMER) {
            unloadPack();
        }
        entityData.set(JOB, job.ordinal());
        setItemSlot(EquipmentSlot.MAINHAND, job.tool());
        applyJobBonus();
    }

    private void rollTalents() {
        ResidentJob[] jobs = ResidentJob.values();
        for (ResidentJob job : jobs) {
            talents[job.ordinal()] = job == ResidentJob.NONE ? 0 : 1 + random.nextInt(3);
        }
        int gifts = random.nextFloat() < 0.4F ? 2 : 1;
        for (int i = 0; i < gifts; i++) {
            talents[1 + random.nextInt(jobs.length - 1)] = 4 + random.nextInt(2);
        }
    }

    public int talent(ResidentJob job) {
        return job == ResidentJob.NONE ? 0 : Mth.clamp(talents[job.ordinal()], 1, 5);
    }

    public int[] talents() {
        return talents.clone();
    }

    /** A talented guard or soldier is tougher and hits harder (VillageTuning.GUARD_*_PER_STAR). */
    private void applyJobBonus() {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (health == null || damage == null) {
            return;
        }
        health.removeModifier(GUARD_HEALTH);
        damage.removeModifier(GUARD_DAMAGE);
        if (getJob().isFighter()) {
            int stars = talent(getJob()) - 1;
            health.addPermanentModifier(new AttributeModifier(GUARD_HEALTH, "Guard talent",
                    VillageTuning.GUARD_HEALTH_PER_STAR * stars, AttributeModifier.Operation.ADDITION));
            damage.addPermanentModifier(new AttributeModifier(GUARD_DAMAGE, "Guard talent",
                    VillageTuning.GUARD_DAMAGE_PER_STAR * stars, AttributeModifier.Operation.ADDITION));
        }
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    public boolean isWanderer() {
        return entityData.get(WANDERER);
    }

    public boolean isLeaving() {
        return leaving;
    }

    public boolean isDowned() {
        return level().isClientSide ? entityData.get(DOWNED) > 0 : downedTicks > 0;
    }

    public int getDownedSeconds() {
        return entityData.get(DOWNED);
    }

    public @Nullable BlockPos getHallPos() {
        return hallPos;
    }

    public @Nullable VillageHallBlockEntity hall() {
        return Villages.hallAt(level(), hallPos);
    }

    public boolean isInOwnVillage(BlockPos pos) {
        VillageHallBlockEntity hall = hall();
        return hall != null && hall.contains(pos);
    }

    public @Nullable BlockPos getHomeBed() {
        return homeBed;
    }

    public int getMood() {
        return mood;
    }

    public SimpleContainer pack() {
        return pack;
    }

    public boolean isNightTime() {
        long time = level().getDayTime() % 24000L;
        return time >= 12500L && time < 23500L;
    }

    public boolean isBedHead(BlockPos pos) {
        if (!level().isLoaded(pos)) {
            return true;
        }
        BlockState state = level().getBlockState(pos);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /** The morning meal and the day's mood (VillageHallBlockEntity.morning). */
    void morning(boolean fed, int recentDeaths, int safety) {
        this.fed = fed;
        int value = 50 + (fed ? 20 : -25) + (homeBed != null ? 15 : -15) - 15 * recentDeaths + (safety >= 75 ? 10 : 0);
        mood = Mth.clamp(value, 0, 100);
    }

    // ---- ticking ----

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (downedTicks > 0) {
            tickDowned();
            return;
        }
        if (tickCount % 20 == 0) {
            checkHome();
            if (isWanderer()) {
                checkDeparture();
            }
        }
    }

    private void checkHome() {
        if (isWanderer()) {
            return;
        }
        if (homeBed != null && !isBedHead(homeBed)) {
            homeBed = null;
        }
        if (homeBed == null && tickCount % 100 == 0) {
            VillageHallBlockEntity hall = hall();
            if (hall != null) {
                homeBed = hall.claimBed(this);
            }
        }
    }

    private void checkDeparture() {
        long now = level().getGameTime();
        if (!leaving && now > leaveAt) {
            startLeaving();
        }
        if (leaving) {
            VillageHallBlockEntity hall = hall();
            boolean outside = hall == null || !hall.contains(blockPosition());
            if (outside || now - leftAt > 20 * 30) {
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0D, getZ(), 12, 0.3D, 0.5D, 0.3D, 0.02D);
                }
                discard();
            }
        }
    }

    public void startLeaving() {
        if (!leaving) {
            leaving = true;
            leftAt = level().getGameTime();
        }
    }

    // ---- downed / death ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (isDowned() && !bypass) {
            return false;
        }
        Entity attacker = source.getEntity();
        // The owner's own suit fire (beams, missiles) and companion suits never hurt the people they protect.
        if (!bypass && (attacker instanceof SuitCompanionEntity
                || attacker != null && attacker.getUUID().equals(ownerId) && source.getDirectEntity() != attacker)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && attacker instanceof Enemy) {
            VillageHallBlockEntity hall = hall();
            if (hall != null) {
                hall.raiseAlarm(blockPosition(), attacker.getName());
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !dying && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            goDown(source.getEntity());
            return;
        }
        super.die(source);
        if (!level().isClientSide && !isWanderer()) {
            VillageHallBlockEntity hall = hall();
            if (hall != null) {
                hall.announceDeath(this);
            }
        }
    }

    private void goDown(@Nullable Entity attacker) {
        downedTicks = VillageTuning.DOWNED_TICKS;
        entityData.set(DOWNED, downedTicks / 20);
        if (isSleeping()) {
            stopSleeping();
        }
        getNavigation().stop();
        setTarget(null);
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32.0D), mob -> mob.getTarget() == this)) {
            mob.setTarget(null);
        }
        VillageHallBlockEntity hall = hall();
        if (hall != null && !isWanderer()) {
            hall.announceDowned(this, attacker);
        }
    }

    private void tickDowned() {
        downedTicks--;
        if (downedTicks % 20 == 0) {
            entityData.set(DOWNED, Math.max(1, downedTicks / 20));
            if (level() instanceof ServerLevel server) {
                server.sendParticles(BLOOD, getX(), getY() + 0.3D, getZ(), 4, 0.4D, 0.1D, 0.4D, 0.0D);
            }
        }
        if (downedTicks <= 0) {
            dying = true;
            kill();
        }
    }

    public void revive() {
        downedTicks = 0;
        entityData.set(DOWNED, 0);
        setHealth(VillageTuning.REVIVE_HEALTH);
        playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + 1.2D, getZ(), 5, 0.4D, 0.3D, 0.4D, 0.0D);
        }
        VillageHallBlockEntity hall = hall();
        if (hall != null && !isWanderer()) {
            hall.announceRevived(this);
        }
    }

    // ---- talking to them ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (isDowned()) {
            if (held.getFoodProperties(this) != null) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                revive();
            } else {
                player.displayClientMessage(Component.translatable("message.flightsuit.resident_first_aid_hint",
                        getName(), getDownedSeconds()).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }
        if (ownerId == null || hall() == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.resident_no_village", getName()), true);
            return InteractionResult.CONSUME;
        }
        if (!ownerId.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.flightsuit.resident_not_yours", getName()), true);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            sendScreen(serverPlayer);
        }
        getNavigation().stop();
        getLookControl().setLookAt(player);
        return InteractionResult.CONSUME;
    }

    /** A choice made on the resident screen (ResidentActionC2SPacket). */
    public void handleAction(ServerPlayer player, int action, int arg) {
        if (ownerId == null || !ownerId.equals(player.getUUID()) || distanceToSqr(player) > 100.0D || isDowned()) {
            return;
        }
        VillageHallBlockEntity hall = hall();
        if (hall == null) {
            return;
        }
        switch (action) {
            case ACTION_ACCEPT -> {
                if (isWanderer() && !leaving) {
                    entityData.set(WANDERER, false);
                    fed = true;
                    mood = 60;
                    homeBed = hall.claimBed(this);
                    hall.announceJoined(this);
                    hall.refreshStats();
                }
            }
            case ACTION_DISMISS -> {
                if (isWanderer()) {
                    startLeaving();
                    hall.refreshStats();
                }
            }
            case ACTION_SET_JOB -> {
                if (!isWanderer()) {
                    setJob(ResidentJob.byId(arg));
                    hall.refreshStats();
                }
            }
            case ACTION_APPROVE, ACTION_TAKE_BLUEPRINT, ACTION_CANCEL -> {
                if (getJob() == ResidentJob.ARCHITECT) {
                    architectAction(player, hall, action, arg);
                }
            }
            default -> {
            }
        }
    }

    /** The architect's "건축" tab: say yes to a suggestion, ask for a blueprint, or call off an order. */
    private void architectAction(ServerPlayer player, VillageHallBlockEntity hall, int action, int arg) {
        List<ItemStack> give = new ArrayList<>();
        Component reply;
        if (action == ACTION_APPROVE) {
            reply = hall.works().approve(arg, give);
        } else if (action == ACTION_TAKE_BLUEPRINT) {
            Blueprint blueprint = Blueprint.values()[Mth.clamp(arg, 0, Blueprint.values().length - 1)];
            if (blueprint.stage() > hall.getStage()) {
                reply = Component.translatable("message.flightsuit.order_locked", Component.translatable(blueprint.translationKey()));
            } else {
                give.add(BlueprintItem.of(blueprint));
                reply = Component.translatable("message.flightsuit.blueprint_given", Component.translatable(blueprint.translationKey()));
            }
        } else {
            hall.works().cancel(arg);
            reply = Component.translatable("message.flightsuit.order_cancelled");
        }
        for (ItemStack stack : give) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.displayClientMessage(reply, true);
        sendScreen(player);
    }

    private void sendScreen(ServerPlayer player) {
        VillageHallBlockEntity hall = hall();
        CompoundTag works = hall != null && getJob() == ResidentJob.ARCHITECT && !isWanderer() ? hall.works().screenData() : null;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ResidentScreenS2CPacket(getId(), mood, fed, homeBed != null, talents(), works));
    }

    private void unloadPack() {
        VillageHallBlockEntity hall = hall();
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack stack = pack.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack rest = hall != null ? hall.store(stack) : stack;
                if (!rest.isEmpty()) {
                    spawnAtLocation(rest);
                }
                pack.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        MutableComponent name = super.getDisplayName().copy();
        Component role = Component.translatable(isWanderer() ? "job.flightsuit.wanderer" : getJob().translationKey());
        name.append(Component.literal(" · ").append(role).withStyle(ChatFormatting.GRAY));
        if (isDowned()) {
            name.append(Component.literal(" ").append(Component.translatable("tag.flightsuit.downed", getDownedSeconds()))
                    .withStyle(ChatFormatting.RED));
        }
        return name;
    }

    // ---- misc ----

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ---- saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Job", getJob().ordinal());
        tag.putBoolean("Wanderer", isWanderer());
        tag.putInt("Downed", downedTicks);
        if (hallPos != null) {
            tag.put("Hall", NbtUtils.writeBlockPos(hallPos));
        }
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        if (homeBed != null) {
            tag.put("HomeBed", NbtUtils.writeBlockPos(homeBed));
        }
        tag.putBoolean("Fed", fed);
        tag.putInt("Mood", mood);
        tag.putLong("LeaveAt", leaveAt);
        tag.putBoolean("Leaving", leaving);
        tag.put("Pack", pack.createTag());
        tag.putIntArray("Talents", talents);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(JOB, ResidentJob.byId(tag.getInt("Job")).ordinal());
        entityData.set(WANDERER, tag.getBoolean("Wanderer"));
        downedTicks = tag.getInt("Downed");
        entityData.set(DOWNED, downedTicks > 0 ? Math.max(1, downedTicks / 20) : 0);
        hallPos = tag.contains("Hall") ? NbtUtils.readBlockPos(tag.getCompound("Hall")) : null;
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        homeBed = tag.contains("HomeBed") ? NbtUtils.readBlockPos(tag.getCompound("HomeBed")) : null;
        fed = !tag.contains("Fed") || tag.getBoolean("Fed");
        mood = tag.contains("Mood") ? tag.getInt("Mood") : 60;
        leaveAt = tag.getLong("LeaveAt");
        leaving = tag.getBoolean("Leaving");
        if (leaving) {
            leftAt = level().getGameTime();
        }
        pack.fromTag(tag.getList("Pack", Tag.TAG_COMPOUND));
        int[] saved = tag.getIntArray("Talents");
        if (saved.length == 0) {
            // From before talents existed.
            rollTalents();
        } else {
            System.arraycopy(saved, 0, talents, 0, Math.min(saved.length, talents.length));
        }
        applyJobBonus();
    }
}
