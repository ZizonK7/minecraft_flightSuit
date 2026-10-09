package com.pfkfks.flightsuit.car;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EnergyCellItem;
import com.pfkfks.flightsuit.suit.SuitTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Capsule Corp style hover car (DESIGN.md 4-10): floats about a block above whatever is under it - ground or
 * water - and glides up and over steps of 3-4 blocks by reading the terrain ahead. Its whole point is
 * efficiency: it burns {@link #DRIVE_COST} FE per tick against a suit's 8-20.
 *
 * Driving follows the vanilla boat model: the riding player's client simulates the car (W/S throttle and
 * brake/reverse, A/D steer) and the server just accepts the vehicle moves; with no driver the server
 * keeps it hovering in place. Sneak + right-click packs it back into its capsule.
 */
public class HoverCarEntity extends Entity {
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(HoverCarEntity.class, EntityDataSerializers.INT);

    public static final int CAPACITY = 50_000;
    public static final int DRIVE_COST = 1;
    private static final double HOVER_HEIGHT = 0.9D;
    private static final double MAX_CLIMB = 4.2D;
    private static final double MAX_SPEED = 1.1D;
    private static final double MAX_REVERSE = 0.3D;
    private static final double ACCEL = 0.05D;
    private static final float TURN_RATE = 4.5F;
    private static final int RECHARGE_PER_SECOND = 2_000;

    /** Forward speed along the car's yaw (driver-side simulation state). */
    private double speed;
    /** Client-side banking angle for the renderer. */
    public float bank;
    public float bankO;
    private Vec3 lastServerPos;
    /** Yaw change this tick, handed to the driver in positionRider (like the vanilla boat) so their view interpolates. */
    private float deltaRotation;
    // Smoothing for server position updates on clients that aren't driving (vanilla boat style).
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public HoverCarEntity(EntityType<? extends HoverCarEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.blocksBuilding = true;
    }

    public static HoverCarEntity spawn(Level level, Vec3 pos, float yaw, int energy) {
        HoverCarEntity car = new HoverCarEntity(ModEntities.HOVER_CAR.get(), level);
        car.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        car.setEnergy(energy);
        level.addFreshEntity(car);
        return car;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ENERGY, CAPACITY);
    }

    public int getEnergy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int energy) {
        entityData.set(ENERGY, Mth.clamp(energy, 0, CAPACITY));
    }

    public double getSpeed() {
        return speed;
    }

    // ---------------------------------------------------------------- riding

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.25D;
    }

    /**
     * Turns the driver with the car here - after the driver's own tick has stored their previous yaw - so the
     * camera interpolates smoothly between frames instead of jumping a few degrees each tick.
     */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (deltaRotation != 0.0F) {
            passenger.setYRot(passenger.getYRot() + deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
            if (passenger instanceof LivingEntity living) {
                living.yBodyRot += deltaRotation;
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 10;
    }

    /** Glide toward server updates when someone else (or no one) drives; ignore them while we drive. */
    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps;
            double y = getY() + (lerpY - getY()) / lerpSteps;
            double z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
            setRot(getYRot(), getXRot());
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof EnergyCellItem) {
            if (!level().isClientSide) {
                int before = getEnergy();
                setEnergy(before + SuitTuning.ENERGY_CELL_CHARGE);
                if (getEnergy() > before && !player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level().playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.6F, 1.6F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (held.isEmpty() && getPassengers().isEmpty()) {
                if (!level().isClientSide) {
                    packIntoCapsule(player);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && getPassengers().isEmpty()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void packIntoCapsule(Player player) {
        ItemStack capsule = HoverCarCapsuleItem.withEnergy(new ItemStack(ModItems.HOVER_CAR_CAPSULE.get()), getEnergy());
        if (!player.getInventory().add(capsule)) {
            spawnAtLocation(capsule);
        }
        poof();
        discard();
    }

    /** Hoi-Poi capsule pop. */
    public void poof() {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5D, getZ(), 30, 1.0D, 0.5D, 1.2D, 0.05D);
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5D, getZ(), 20, 1.0D, 0.5D, 1.2D, 0.05D);
        }
        level().playSound(null, blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0F, 0.6F);
        level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 1.6F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // ---------------------------------------------------------------- simulation

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        deltaRotation = 0.0F;
        if (isControlledByLocalInstance()) {
            drive();
        }
        if (level().isClientSide) {
            bankO = bank;
            LivingEntity driver = getControllingPassenger();
            float turning = driver == null ? 0.0F : driver.xxa;
            bank += ((float) (-turning * 18.0F * Math.min(1.0D, Math.abs(speed) / 0.5D)) - bank) * 0.2F;
            if (getEnergy() > 0) {
                spawnThrusterParticles();
            }
        } else {
            serverUpkeep();
        }
    }

    private void drive() {
        LivingEntity driver = getControllingPassenger();
        float forward = driver == null ? 0.0F : driver.zza;
        float strafe = driver == null ? 0.0F : driver.xxa;
        boolean powered = getEnergy() > 0;

        if (strafe != 0.0F) {
            // Full turn rate while moving, a gentle pivot when standing still.
            float rate = Math.abs(speed) > 0.02D ? TURN_RATE * (float) Math.signum(speed) : TURN_RATE * 0.5F;
            deltaRotation = -strafe * rate;
            setYRot(getYRot() + deltaRotation);
        }
        if (powered && forward > 0.0F) {
            speed = Math.min(MAX_SPEED, speed + ACCEL);
        } else if (forward < 0.0F) {
            speed = Math.max(-MAX_REVERSE, speed - ACCEL * 1.5D);
        } else {
            speed *= 0.97D;
        }
        if (!powered) {
            speed *= 0.9D;
        }

        Vec3 dir = Vec3.directionFromRotation(0.0F, getYRot());
        double groundHere = groundHeight(position(), 0.5D);
        double targetY = groundHere + (powered ? HOVER_HEIGHT : 0.05D);
        if (powered && Math.abs(speed) > 0.01D) {
            // Read the terrain ahead so the car rises *before* reaching a step instead of hitting it.
            Vec3 ahead = position().add(dir.scale(Math.signum(speed) * (1.2D + Math.abs(speed) * 5.0D)));
            double groundAhead = groundHeight(ahead, MAX_CLIMB + 0.5D);
            if (groundAhead > groundHere && groundAhead - groundHere <= MAX_CLIMB) {
                targetY = Math.max(targetY, groundAhead + HOVER_HEIGHT);
            }
        }
        double bob = powered ? Math.sin(tickCount * 0.12D) * 0.01D : 0.0D;
        double vy = Mth.clamp((targetY - getY()) * 0.3D, -0.6D, 0.7D) + bob;
        setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) {
            speed *= 0.3D;
        }
    }

    /**
     * Top of the first solid block or liquid surface at or below {@code from.y + scanAbove} - the "ground"
     * the car floats over. Water and lava count, so the car skims across them.
     */
    private double groundHeight(Vec3 from, double scanAbove) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(from.x), Mth.floor(from.y + scanAbove), Mth.floor(from.z));
        int bottom = Mth.floor(from.y) - 12;
        while (pos.getY() >= bottom) {
            BlockState state = level().getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return pos.getY() + state.getFluidState().getHeight(level(), pos);
            }
            VoxelShape shape = state.getCollisionShape(level(), pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
            pos.move(0, -1, 0);
        }
        return bottom;
    }

    /** Server: pay for driving (judged from actual movement) and trickle-charge from nearby power blocks. */
    private void serverUpkeep() {
        Vec3 now = position();
        if (lastServerPos != null && getControllingPassenger() != null
                && now.subtract(lastServerPos).horizontalDistanceSqr() > 0.0004D) {
            Player driver = (Player) getControllingPassenger();
            if (!driver.getAbilities().instabuild) {
                setEnergy(getEnergy() - DRIVE_COST);
            }
        }
        lastServerPos = now;
        if (tickCount % 20 == 0 && getEnergy() < CAPACITY) {
            int want = Math.min(RECHARGE_PER_SECOND, CAPACITY - getEnergy());
            for (PowerGrid.Role role : new PowerGrid.Role[]{PowerGrid.Role.STORAGE, PowerGrid.Role.PRODUCER}) {
                for (PowerGrid.Node node : PowerGrid.nodesNear(level(), blockPosition(), role)) {
                    if (want <= 0) {
                        break;
                    }
                    int got = node.energy().extractEnergy(want, false);
                    setEnergy(getEnergy() + got);
                    want -= got;
                }
            }
        }
    }

    private void spawnThrusterParticles() {
        Vec3 dir = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        for (int fb = -1; fb <= 1; fb += 2) {
            for (int lr = -1; lr <= 1; lr += 2) {
                if (random.nextInt(3) != 0) {
                    continue;
                }
                Vec3 pad = position().add(dir.scale(0.7D * fb)).add(side.scale(0.5D * lr));
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, pad.x, pad.y + 0.02D, pad.z, 0.0D, -0.08D, 0.0D);
            }
        }
        if (Math.abs(speed) > 0.3D && random.nextInt(2) == 0) {
            Vec3 back = position().subtract(dir.scale(1.3D)).add(0.0D, 0.3D, 0.0D);
            level().addParticle(ParticleTypes.CLOUD, back.x, back.y, back.z, -dir.x * 0.1D, 0.0D, -dir.z * 0.1D);
        }
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setEnergy(tag.getInt("Energy"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Energy", getEnergy());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.flightsuit.hover_car");
    }
}
