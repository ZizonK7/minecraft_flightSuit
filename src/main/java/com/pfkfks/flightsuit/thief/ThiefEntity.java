package com.pfkfks.flightsuit.thief;

import com.pfkfks.flightsuit.block.SecuritySensorBlockEntity;
import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.WornSuit;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * One of Batman's crew on a job (DESIGN.md 4-14, M14). Comes in at night crouched and invisible, goes from
 * chest to chest taking a little from each and leaving a bat mark, and slips out again. Guards on night watch,
 * a player close by or an armed security sensor can spot them - more easily in the light. Spotted: Batman throws
 * smoke and an EMP and runs, Catwoman bolts, Robin fights a while and then runs. Beaten, they don't die: they
 * drop what they took plus a gadget and vanish in smoke. Never saved - a night's job ends at dawn either way.
 */
public class ThiefEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPOTTED = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);
    /** A job that drags on this long is given up (they slip away with whatever they have). */
    private static final int MAX_TICKS = 20 * 60 * 10;

    private @Nullable BlockPos hall;
    private final List<BlockPos> targets = new ArrayList<>();
    private int targetIndex;
    private final List<ItemStack> loot = new ArrayList<>();
    private int fightTicks;
    private int fleeTicks;
    private int jobTicks;
    private boolean gone;
    /** Spawned by "/flightsuit thief spawn": not part of tonight's visit. */
    private boolean test;
    /** The chest or barrel standing open right now (closed again if they're caught or slip away). */
    private @Nullable BlockPos openAt;

    public ThiefEntity(EntityType<? extends ThiefEntity> type, Level level) {
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
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    public static ThiefEntity create(ServerLevel level, ThiefType type, BlockPos hall, List<BlockPos> targets) {
        ThiefEntity thief = new ThiefEntity(ModEntities.THIEF.get(), level);
        thief.entityData.set(TYPE, type.ordinal());
        thief.hall = hall.immutable();
        thief.targets.addAll(targets);
        AttributeInstance health = thief.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(type.health());
        }
        AttributeInstance speed = thief.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(type.speed());
        }
        thief.setHealth(thief.getMaxHealth());
        thief.setCustomName(type.displayName());
        thief.setCustomNameVisible(false);
        thief.hide();
        return thief;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, 0);
        entityData.define(SPOTTED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, true) {
            @Override
            public boolean canUse() {
                return isSpotted() && fightTicks > 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isSpotted() && fightTicks > 0 && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(3, new FleeGoal());
        goalSelector.addGoal(4, new HeistGoal());
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return fightTicks > 0 && super.canUse();
            }
        });
    }

    public ThiefType getThiefType() {
        return ThiefType.byId(entityData.get(TYPE));
    }

    public boolean isSpotted() {
        return entityData.get(SPOTTED);
    }

    public @Nullable BlockPos getHall() {
        return hall;
    }

    boolean isTest() {
        return test;
    }

    void markTest() {
        test = true;
    }

    private void closeLid() {
        if (openAt != null) {
            ThiefManager.setLid(level(), openAt, false);
            openAt = null;
        }
    }

    void addLoot(ItemStack stack) {
        loot.add(stack);
    }

    private void hide() {
        addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
    }

    // ---------------------------------------------------------------- watching out

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || gone) {
            return;
        }
        if (++jobTicks > MAX_TICKS || hall == null) {
            escape(server);
            return;
        }
        if (!isSpotted()) {
            if (tickCount % 20 == 0) {
                hide();
            }
            if (tickCount % 10 == 0) {
                lookOut(server);
            }
        } else if (fightTicks > 0 && --fightTicks == 0) {
            setTarget(null);
        }
    }

    /** Guards on watch, a player close by, an armed sensor: any of them spots a hidden thief. Light helps them. */
    private void lookOut(ServerLevel server) {
        BlockPos pos = blockPosition();
        boolean lit = server.getBrightness(LightLayer.BLOCK, pos) >= ThiefTuning.LIT;
        double guardSight = lit ? ThiefTuning.GUARD_SIGHT_LIT : ThiefTuning.GUARD_SIGHT_DARK;
        for (ResidentEntity guard : server.getEntitiesOfClass(ResidentEntity.class, getBoundingBox().inflate(guardSight),
                r -> r.getJob().isFighter() && !r.isDowned() && !r.isSleeping() && !r.isBaby() && !r.isWanderer())) {
            if (guard.distanceTo(this) <= guardSight && guard.hasLineOfSight(this)) {
                spot(server, guard, Component.translatable("thief.flightsuit.seen_by_guard", guard.getName()));
                return;
            }
        }
        double playerSight = lit ? ThiefTuning.PLAYER_SIGHT_LIT : ThiefTuning.PLAYER_SIGHT_DARK;
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(playerSight),
                p -> !p.isSpectator() && !p.isSleeping())) {
            if (player.distanceTo(this) <= playerSight && player.hasLineOfSight(this)) {
                spot(server, player, Component.translatable("thief.flightsuit.seen_by_player", player.getName()));
                return;
            }
        }
        for (PowerGrid.Node node : PowerGrid.nodesNear(server, pos, PowerGrid.Role.CONSUMER)) {
            if (node instanceof SecuritySensorBlockEntity sensor && sensor.isArmed()
                    && sensor.getBlockPos().closerToCenterThan(position(), ThiefTuning.SENSOR_RANGE)) {
                sensor.trip();
                spot(server, null, Component.translatable("thief.flightsuit.seen_by_sensor"));
                return;
            }
        }
    }

    /** Found out: the village alarm, the fighters come, and each of the crew does their thing. */
    public void spot(ServerLevel server, @Nullable LivingEntity by, Component how) {
        if (isSpotted() || gone) {
            return;
        }
        entityData.set(SPOTTED, true);
        removeEffect(MobEffects.INVISIBILITY);
        ThiefType type = getThiefType();
        VillageHallBlockEntity village = Villages.hallAt(server, hall);
        if (village != null) {
            village.raiseAlarm(blockPosition(), Component.translatable("thief.flightsuit.alarm", type.displayName()));
        }
        say(server, Component.translatable("thief.flightsuit.spotted", type.displayName(), how).withStyle(ChatFormatting.LIGHT_PURPLE));
        ThiefManager.onSpotted(server, this);
        LivingEntity nearest = by;
        for (ResidentEntity fighter : server.getEntitiesOfClass(ResidentEntity.class, getBoundingBox().inflate(24.0D),
                r -> r.getJob().isFighter() && !r.isDowned() && !r.isBaby() && !r.isWanderer() && !r.isSleeping())) {
            fighter.setTarget(this);
            if (nearest == null || fighter.distanceToSqr(this) < nearest.distanceToSqr(this)) {
                nearest = fighter;
            }
        }
        switch (type) {
            case BATMAN -> {
                SmokeBombItem.cloud(server, getX(), getY(), getZ());
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0D),
                        e -> !(e instanceof ThiefEntity))) {
                    near.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                }
                emp(server, ThiefTuning.EMP_RANGE);
                say(server, line("emp"));
            }
            case CATWOMAN -> {
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
                say(server, line("bolt"));
            }
            case ROBIN -> {
                fightTicks = ThiefTuning.ROBIN_FIGHT_TICKS;
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
                if (nearest != null) {
                    setTarget(nearest);
                } else {
                    fightTicks = 0;
                }
                say(server, line("fight"));
            }
        }
    }

    /** Batman's EMP: power blocks around drained (station storage unlocks), worn suits lose a share of their charge. */
    public void emp(ServerLevel server, double range) {
        BlockPos pos = blockPosition();
        for (PowerGrid.Role role : PowerGrid.Role.values()) {
            for (PowerGrid.Node node : PowerGrid.nodesNear(server, pos, role)) {
                BlockPos at = node.self().getBlockPos();
                if (!at.closerToCenterThan(position(), range)) {
                    continue;
                }
                node.energy().consume(node.energy().getEnergyStored());
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5D, at.getY() + 0.7D, at.getZ() + 0.5D, 12, 0.4D, 0.4D, 0.4D, 0.2D);
            }
        }
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range))) {
            boolean hit = false;
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.getItem() instanceof SuitArmorItem) {
                    SuitEnergy.set(stack, (int) (SuitEnergy.get(stack) * (1.0F - ThiefTuning.EMP_SUIT_DRAIN)));
                    hit = true;
                }
            }
            if (hit) {
                player.displayClientMessage(Component.translatable("thief.flightsuit.emp_hit").withStyle(ChatFormatting.AQUA), true);
            }
        }
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0D, getZ(), 40, 1.5D, 1.0D, 1.5D, 0.3D);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5F, 1.4F);
    }

    // ---------------------------------------------------------------- getting away

    /** Out of the village and gone, with whatever they took. */
    void escape(ServerLevel server) {
        if (gone) {
            return;
        }
        gone = true;
        closeLid();
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
        ThiefManager.onEscaped(server, this);
        discard();
    }

    /** Heads out of the village, a stretch at a time; past its edge they're away. */
    private void headOut(double speed) {
        if (!(level() instanceof ServerLevel server) || hall == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(hall);
        Vec3 away = position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        if (away.length() > VillageTuning.RADIUS + 12) {
            escape(server);
            return;
        }
        if (tickCount % 20 == 0 || getNavigation().isDone() && tickCount % 5 == 0) {
            Vec3 goal = position().add(away.normalize().scale(16.0D));
            getNavigation().moveTo(goal.x, getY(), goal.z, speed);
        }
    }

    private final class FleeGoal extends Goal {
        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isSpotted() && (fightTicks <= 0 || getTarget() == null);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            headOut(getThiefType() == ThiefType.CATWOMAN ? 1.5D : 1.35D);
            if (++fleeTicks > 20 * 60 && level() instanceof ServerLevel server) {
                escape(server);
            }
        }
    }

    /** Chest to chest, quietly; when the list is done, out the way they came. */
    private final class HeistGoal extends Goal {
        private int walkTicks;
        private int lootTicks;
        private boolean open;

        HeistGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isSpotted() && hall != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            closeLid();
            open = false;
            lootTicks = 0;
        }

        @Override
        public void tick() {
            if (!(level() instanceof ServerLevel server)) {
                return;
            }
            if (targetIndex >= targets.size()) {
                headOut(0.9D);
                return;
            }
            BlockPos target = targets.get(targetIndex);
            if (!server.isLoaded(target)) {
                next();
                return;
            }
            Vec3 at = Vec3.atCenterOf(target);
            if (distanceToSqr(at) > 2.4D * 2.4D) {
                if (walkTicks % 20 == 0 || getNavigation().isDone() && walkTicks % 5 == 0) {
                    getNavigation().moveTo(at.x, target.getY(), at.z, 0.85D);
                }
                if (++walkTicks > 20 * 30) {
                    next();
                }
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(at.x, at.y, at.z);
            if (!open) {
                open = true;
                openAt = target;
                ThiefManager.setLid(server, target, true);
            }
            if (++lootTicks >= ThiefTuning.LOOT_TICKS) {
                ThiefManager.robLive(server, ThiefEntity.this, target);
                closeLid();
                next();
            }
        }

        private void next() {
            targetIndex++;
            walkTicks = 0;
            lootTicks = 0;
            open = false;
        }
    }

    // ---------------------------------------------------------------- beaten, hurt, talk

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        // Only someone actually hitting them gives them away (not a cactus or a fall).
        if (hurt && !isSpotted() && source.getEntity() instanceof LivingEntity by && level() instanceof ServerLevel server) {
            spot(server, by, Component.translatable("thief.flightsuit.seen_by_player", by.getName()));
        }
        return hurt;
    }

    /** Beaten: drops everything taken and a gadget, and is gone in a puff of smoke. */
    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(level() instanceof ServerLevel server)) {
            super.die(source);
            return;
        }
        if (gone) {
            return;
        }
        gone = true;
        closeLid();
        setHealth(1.0F);
        int recovered = 0;
        for (ItemStack stack : loot) {
            recovered++;
            spawnAtLocation(stack);
        }
        loot.clear();
        spawnAtLocation(ThiefManager.reward(getThiefType()));
        SmokeBombItem.cloud(server, getX(), getY(), getZ());
        say(server, line("caught"));
        ThiefManager.onCaught(server, this, recovered);
        discard();
    }

    private Component line(String key) {
        return Component.translatable("general.flightsuit.says", getThiefType().displayName(),
                Component.translatable("thief.flightsuit." + getThiefType().id() + "." + key));
    }

    private void say(ServerLevel server, Component line) {
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(32.0D))) {
            player.sendSystemMessage(line);
        }
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
    public boolean isSilent() {
        return !isSpotted() || super.isSilent();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Thief", getThiefType().ordinal());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, ThiefType.byId(tag.getInt("Thief")).ordinal());
    }
}
