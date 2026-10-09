package com.pfkfks.flightsuit.planet;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Capsule Corp spaceship (DESIGN.md 4-16, M15). Lifts off the launch pad with its pilot aboard, and at
 * the top of the climb SpaceTravel takes it across space; a new ship then comes down at the other end.
 * Landed on a planet it is the base there: right-click to fly home or link to a suit at home.
 * It flies itself - no physics, no gravity; the pilot can't get out mid-flight.
 */
public class SpaceshipEntity extends Entity {
    public static final int LANDED = 0;
    public static final int ASCENT = 1;
    public static final int DESCENT = 2;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(SpaceshipEntity.class, EntityDataSerializers.INT);
    public static final int ASCENT_TICKS = 140;

    private @Nullable UUID owner;
    /** Where this flight goes: a planet, or null for home. While landed: the planet it sits on (null at home). */
    private @Nullable Planet planet;
    private double groundY;
    private int phaseTicks;
    private @Nullable UUID pendingRider;
    private boolean letOut;

    public SpaceshipEntity(EntityType<? extends SpaceshipEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static SpaceshipEntity create(ServerLevel level, @Nullable UUID owner, @Nullable Planet planet, double x, double y, double z, float yaw) {
        SpaceshipEntity ship = new SpaceshipEntity(ModEntities.SPACESHIP.get(), level);
        ship.owner = owner;
        ship.planet = planet;
        ship.moveTo(x, y, z, yaw, 0.0F);
        return ship;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, LANDED);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean inFlight() {
        return phase() != LANDED;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public @Nullable Planet planet() {
        return planet;
    }

    /** Lift off, bound for {@code destination} (null = home). */
    public void launch(@Nullable Planet destination) {
        planet = destination;
        phaseTicks = 0;
        entityData.set(PHASE, ASCENT);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3.0F, 0.5F);
    }

    /** Come down from up high onto {@code ground}, picking up {@code rider} on the way (they were moved here). */
    public void descend(double ground, @Nullable UUID rider) {
        groundY = ground;
        pendingRider = rider;
        phaseTicks = 0;
        entityData.set(PHASE, DESCENT);
    }

    /** SpaceTravel lets the pilot out (landing, or the jump across space); otherwise nobody leaves mid-flight. */
    public void letOut() {
        letOut = true;
        ejectPassengers();
        letOut = false;
    }

    public boolean mayLeave() {
        return letOut || !inFlight();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (inFlight()) {
                for (int i = 0; i < 4; i++) {
                    level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5D) * 1.4D, getY() - 0.1D,
                            getZ() + (random.nextDouble() - 0.5D) * 1.4D, 0.0D, -0.3D, 0.0D);
                    level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5D) * 2.0D, getY() - 0.4D,
                            getZ() + (random.nextDouble() - 0.5D) * 2.0D, 0.0D, -0.05D, 0.0D);
                }
            }
            return;
        }
        phaseTicks++;
        switch (phase()) {
            case ASCENT -> {
                double speed = Math.min(1.6D, 0.04D + phaseTicks * 0.012D);
                setPos(getX(), getY() + speed, getZ());
                if (phaseTicks % 20 == 0) {
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 2.0F, 0.5F);
                }
                if (phaseTicks >= ASCENT_TICKS || getY() > level().getMaxBuildHeight() + 32) {
                    SpaceTravel.cross(this);
                }
            }
            case DESCENT -> {
                if (pendingRider != null && level() instanceof ServerLevel server && server.getPlayerByUUID(pendingRider) instanceof ServerPlayer rider) {
                    if (rider.getVehicle() == this || rider.distanceToSqr(this) < 64.0D * 64.0D && rider.startRiding(this, true)) {
                        pendingRider = null;
                    }
                }
                double above = getY() - groundY;
                double speed = Mth.clamp(above * 0.035D, 0.06D, 1.2D);
                if (above <= speed || phaseTicks > 1200) {
                    setPos(getX(), groundY, getZ());
                    entityData.set(PHASE, LANDED);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0F, 0.6F);
                    SpaceTravel.landed(this);
                } else {
                    setPos(getX(), getY() - speed, getZ());
                }
            }
            default -> {
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || inFlight()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            SpaceTravel.shipMenu(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Can't be shot down; a creative player knocking it (sneaking) packs it away. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player player && player.isCreative() && player.isShiftKeyDown()) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !inFlight();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.5D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        planet = Planet.byId(tag.getString("Planet"));
        groundY = tag.getDouble("Ground");
        // A flight cut short by a restart just finishes landing where it is.
        entityData.set(PHASE, tag.getInt("Phase") == DESCENT ? DESCENT : LANDED);
        if (phase() == DESCENT && groundY > getY()) {
            groundY = getY();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("Planet", planet == null ? "" : planet.id());
        tag.putDouble("Ground", groundY);
        tag.putInt("Phase", phase());
    }
}
