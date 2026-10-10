package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Veronica (M17): the Hulkbuster's delivery pod. Called up, it drops out of the sky beside the wearer, slams into
 * the ground, and its front folds open - the pieces fly the last couple of blocks out of it onto the wearer
 * (SuitUpManager). Then it shuts and lifts off again. Purely for show: never saved, nothing collides with it.
 * The server moves it; clients only follow its phase (falling, open, leaving) to swing the door.
 */
public class VeronicaPodEntity extends Entity {
    public static final byte FALLING = 0;
    public static final byte OPEN = 1;
    public static final byte LEAVING = 2;
    /** Open this long after landing, then it lifts off. */
    private static final int OPEN_TICKS = 60;
    private static final int LEAVE_TICKS = 30;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(VeronicaPodEntity.class, EntityDataSerializers.BYTE);

    private Vec3 land = Vec3.ZERO;
    private double drop;
    private int fallTicks = 20;
    private int phaseAge;
    /** Client: ticks since the phase last changed (the door's swing). */
    private int clientPhaseAge;
    private byte clientPhase = FALLING;

    public VeronicaPodEntity(EntityType<? extends VeronicaPodEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** A pod that will land at {@code land} after falling {@code drop} blocks over {@code fallTicks}, door toward {@code yaw}. */
    public static VeronicaPodEntity drop(ServerLevel level, Vec3 land, double drop, int fallTicks, float yaw) {
        VeronicaPodEntity pod = new VeronicaPodEntity(ModEntities.VERONICA_POD.get(), level);
        pod.land = land;
        pod.drop = drop;
        pod.fallTicks = Math.max(1, fallTicks);
        pod.moveTo(land.x, land.y + drop, land.z, yaw, 0.0F);
        level.playSound(null, land.x, land.y + drop, land.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0F, 0.5F);
        return pod;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, FALLING);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    /** 0 shut .. 1 wide open, for the renderer. */
    public float doorOpen(float partialTick) {
        float t = (clientPhaseAge + partialTick) / 8.0F;
        return switch (clientPhase) {
            case OPEN -> Math.min(1.0F, t);
            case LEAVING -> Math.max(0.0F, 1.0F - t);
            default -> 0.0F;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            byte now = phase();
            if (now != clientPhase) {
                clientPhase = now;
                clientPhaseAge = 0;
            } else {
                clientPhaseAge++;
            }
            if (now == FALLING) {
                level().addParticle(ParticleTypes.FLAME, getX(), getY() + 3.0D, getZ(), 0.0D, 0.1D, 0.0D);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3.2D, getZ(), 0.0D, 0.05D, 0.0D);
            }
            return;
        }
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        phaseAge++;
        switch (phase()) {
            case FALLING -> {
                double t = Math.min(1.0D, phaseAge / (double) fallTicks);
                setPos(land.x, land.y + drop * (1.0D - t * t), land.z);
                if (t >= 1.0D) {
                    impact(server);
                    entityData.set(PHASE, OPEN);
                    phaseAge = 0;
                }
            }
            case OPEN -> {
                if (phaseAge >= OPEN_TICKS) {
                    entityData.set(PHASE, LEAVING);
                    phaseAge = 0;
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0F, 0.6F);
                }
            }
            default -> {
                // Shut, then up and away on its thrusters.
                if (phaseAge > 10) {
                    double up = (phaseAge - 10) * 0.25D * (phaseAge - 10) * 0.1D;
                    setPos(land.x, land.y + up, land.z);
                    server.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.1D, getZ(), 3, 0.3D, 0.0D, 0.3D, 0.02D);
                    if (phaseAge == 11) {
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5F, 0.6F);
                    }
                }
                if (phaseAge >= LEAVE_TICKS + 10) {
                    server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0D, getZ(), 10, 0.5D, 0.5D, 0.5D, 0.05D);
                    discard();
                }
            }
        }
        if (tickCount > 600) {
            discard();
        }
    }

    /** Thump: dust and broken ground round the pod, a boom, and anything right there thrown aside. */
    private void impact(ServerLevel level) {
        BlockPos below = BlockPos.containing(land).below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            ground = Blocks.DIRT.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), land.x, land.y + 0.2D, land.z, 60, 1.5D, 0.2D, 1.5D, 0.2D);
        level.sendParticles(ParticleTypes.EXPLOSION, land.x, land.y + 0.5D, land.z, 3, 1.0D, 0.3D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, land.x, land.y + 0.3D, land.z, 12, 1.2D, 0.2D, 1.2D, 0.01D);
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI / 10.0D;
            level.sendParticles(ParticleTypes.CLOUD, land.x + Math.cos(angle) * 1.2D, land.y + 0.2D, land.z + Math.sin(angle) * 1.2D, 0,
                    Math.cos(angle), 0.05D, Math.sin(angle), 0.5D);
        }
        level.playSound(null, land.x, land.y, land.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.6F);
        level.playSound(null, land.x, land.y, land.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.5F);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0D), e -> !(e instanceof Player))) {
            Vec3 push = near.position().subtract(land).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                near.knockback(1.0D, -push.x, -push.z);
            }
        }
    }

    /** Where the pieces come out of (the open front, chest high). */
    public static Vec3 hatch(Vec3 land, float yaw) {
        Vec3 front = Vec3.directionFromRotation(0.0F, yaw);
        return land.add(front.scale(0.9D)).add(0.0D, 1.2D, 0.0D);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
