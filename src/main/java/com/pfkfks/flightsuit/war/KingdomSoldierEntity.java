package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.ai.MarchOnVillageGoal;
import com.pfkfks.flightsuit.war.ai.RaiderTargetGoal;
import com.pfkfks.flightsuit.war.ai.YieldedGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * A Three Kingdoms foot soldier (DESIGN.md 4-11, M10): swordsman, spearman or archer, in their kingdom's
 * colours. Comes with a raid, marches on the village hall and fights whoever stands in the way - residents,
 * the player, the suits. Archers of a fire attack (화공) shoot burning arrows that can set the village alight.
 *
 * When the army gives up they kneel and lay down their arms, waiting to be recruited or let go.
 */
public class KingdomSoldierEntity extends Monster implements RangedAttackMob, RaidMember {
    public enum Type {
        SWORD, SPEAR, ARCHER;

        private static final Type[] VALUES = values();

        public static Type byId(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SWORD;
        }
    }

    private static final EntityDataAccessor<Integer> KINGDOM = SynchedEntityData.defineId(KingdomSoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(KingdomSoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> YIELDED = SynchedEntityData.defineId(KingdomSoldierEntity.class, EntityDataSerializers.BOOLEAN);

    public static final String FIRE_ARROW_TAG = "flightsuit_fire_arrow";

    private int raidId = -1;
    private @Nullable BlockPos hallPos;
    private boolean fireArrows;

    public KingdomSoldierEntity(EntityType<? extends KingdomSoldierEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 6;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, WarTuning.SOLDIER_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    /** A fresh soldier for a raid (or by hand: raid -1, no hall). */
    public static KingdomSoldierEntity create(ServerLevel level, Kingdom kingdom, Type type, int raidId,
                                              @Nullable BlockPos hall, boolean fireArrows) {
        KingdomSoldierEntity soldier = new KingdomSoldierEntity(ModEntities.KINGDOM_SOLDIER.get(), level);
        soldier.setup(kingdom, type, raidId, hall, fireArrows);
        return soldier;
    }

    private void setup(Kingdom kingdom, Type type, int raidId, @Nullable BlockPos hall, boolean fireArrows) {
        entityData.set(KINGDOM, kingdom.ordinal());
        entityData.set(TYPE, type.ordinal());
        this.raidId = raidId;
        this.hallPos = hall == null ? null : hall.immutable();
        this.fireArrows = fireArrows;
        setItemSlot(EquipmentSlot.MAINHAND, switch (type) {
            case SWORD -> new ItemStack(Items.IRON_SWORD);
            case SPEAR -> new ItemStack(Items.TRIDENT);
            case ARCHER -> new ItemStack(Items.BOW);
        });
        double health = switch (type) {
            case SWORD -> WarTuning.SOLDIER_HEALTH;
            case SPEAR -> WarTuning.SPEARMAN_HEALTH;
            case ARCHER -> WarTuning.ARCHER_HEALTH;
        };
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        // The trident's +8 makes a spearman hit hard already; the sword's +5 sits on top of 1.
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(type == Type.SPEAR ? 0.0D : 1.0D);
        }
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.flightsuit.kingdom_soldier." + type.name().toLowerCase(java.util.Locale.ROOT),
                kingdom.displayName()));
    }

    /** Spawn egg: a random soldier of a random kingdom, marching on the village it was dropped in. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                  @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (getMainHandItem().isEmpty()) {
            VillageHallBlockEntity hall = Villages.containing(level.getLevel(), blockPosition());
            setup(Kingdom.byId(random.nextInt(Kingdom.values().length)), Type.byId(random.nextInt(Type.values().length)), -1,
                    hall == null ? null : hall.getBlockPos(), random.nextBoolean());
        }
        return result;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KINGDOM, Kingdom.SHU.ordinal());
        entityData.define(TYPE, Type.SWORD.ordinal());
        entityData.define(YIELDED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new YieldedGoal(this, this::hasYielded));
        goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0D, 30, 16.0F) {
            @Override
            public boolean canUse() {
                return getSoldierType() == Type.ARCHER && !hasYielded() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false) {
            @Override
            public boolean canUse() {
                return getSoldierType() != Type.ARCHER && !hasYielded() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !hasYielded() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new MarchOnVillageGoal(this, () -> hasYielded() ? null : hallPos));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, KingdomSoldierEntity.class, GeneralEntity.class) {
            @Override
            public boolean canUse() {
                return !hasYielded() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new RaiderTargetGoal(this, this::hasYielded));
    }

    // ---- state ----

    public Kingdom getKingdom() {
        return Kingdom.byId(entityData.get(KINGDOM));
    }

    public Type getSoldierType() {
        return Type.byId(entityData.get(TYPE));
    }

    public boolean hasYielded() {
        return entityData.get(YIELDED);
    }

    @Override
    public int raidId() {
        return raidId;
    }

    @Override
    public boolean isNoThreat() {
        return hasYielded();
    }

    public @Nullable BlockPos getHallPos() {
        return hallPos;
    }

    /** The army gave up: weapon down, on one knee. */
    public void yieldNow() {
        if (hasYielded()) {
            return;
        }
        entityData.set(YIELDED, true);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        setTarget(null);
        getNavigation().stop();
        setAggressive(false);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(hasYielded() ? null : target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (hasYielded() && attacker instanceof net.minecraft.world.entity.animal.IronGolem) {
            return false;
        }
        // No friendly fire inside one army (stray arrows, sweeping spears).
        if (attacker instanceof RaidMember member && !member.isNoThreat() && member.raidId() == raidId && raidId >= 0) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // ---- bow ----

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), power);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2D, dz, 1.6F, 10.0F);
        if (fireArrows) {
            arrow.setSecondsOnFire(100);
            arrow.addTag(FIRE_ARROW_TAG);
        }
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    // ---- loot / misc ----

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (raidId < 0) {
            return;
        }
        if (getSoldierType() == Type.ARCHER) {
            spawnAtLocation(new ItemStack(Items.ARROW, 2 + random.nextInt(4)));
        }
        spawnAtLocation(new ItemStack(Items.IRON_NUGGET, 1 + random.nextInt(3)));
        if (random.nextFloat() < 0.4F) {
            spawnAtLocation(new ItemStack(Items.BREAD));
        }
        if (random.nextFloat() < 0.1F) {
            spawnAtLocation(new ItemStack(Items.EMERALD));
        }
    }

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
        return !hasYielded();
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    // ---- saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kingdom", getKingdom().ordinal());
        tag.putInt("SoldierType", getSoldierType().ordinal());
        tag.putBoolean("Yielded", hasYielded());
        tag.putInt("Raid", raidId);
        tag.putBoolean("FireArrows", fireArrows);
        if (hallPos != null) {
            tag.put("Hall", NbtUtils.writeBlockPos(hallPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KINGDOM, Kingdom.byId(tag.getInt("Kingdom")).ordinal());
        entityData.set(TYPE, Type.byId(tag.getInt("SoldierType")).ordinal());
        entityData.set(YIELDED, tag.getBoolean("Yielded"));
        raidId = tag.contains("Raid") ? tag.getInt("Raid") : -1;
        fireArrows = tag.getBoolean("FireArrows");
        hallPos = tag.contains("Hall") ? NbtUtils.readBlockPos(tag.getCompound("Hall")) : null;
    }
}
