package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shenron (DESIGN.md 4-16 드래곤볼, after the user asked for a model instead of particles): a long-bodied dragon
 * that rises out of the gathered Dragon Balls, winding up into the sky in two turns, arches over and looks
 * down at whoever summoned him, waits for the wish, then dissolves into light from the tail up.
 *
 * The body follows one fixed path (spine) in his own frame - +Z toward the summoner, y up, in blocks: a coil
 * around the balls rising from 3 to 24 blocks, then a neck curving over the top to the head, 21 blocks up and
 * 8 out in front. While he rises the head travels along it and the body follows (headAt), so he spirals up.
 * Both sides use the same path: the renderer draws it, the client's particles mark the dissolving edge.
 * Never saved; DragonBalls sends him away after the wish (or when the two minutes run out).
 */
public class ShenronEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(ShenronEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEAVING = SynchedEntityData.defineId(ShenronEntity.class, EntityDataSerializers.INT);

    public static final int RISE_TICKS = 90;
    public static final int LEAVE_TICKS = 60;
    /** One body segment, in blocks along the spine. */
    public static final double SEGMENT = 1.6D;
    /** A /summon'ed one, that nobody sends away, leaves after this long. */
    private static final int MAX_AGE = 20 * 150;

    private static final double TURNS = 2.0D;
    private static final double COIL = 0.84D;
    private static final Vec3 HEAD = new Vec3(0.0D, 21.5D, 8.5D);
    private static final int SAMPLES = 400;
    /** Arc length at each of SAMPLES + 1 evenly spaced spine parameters (the path at rest). */
    private static final double[] ARC = new double[SAMPLES + 1];
    /** Length of the whole body, tail to the back of the head. */
    public static final double LENGTH;

    static {
        Vec3 last = spine(0.0D, 0.0F);
        for (int i = 1; i <= SAMPLES; i++) {
            Vec3 p = spine(i / (double) SAMPLES, 0.0F);
            ARC[i] = ARC[i - 1] + p.distanceTo(last);
            last = p;
        }
        LENGTH = ARC[SAMPLES];
    }

    private int age;

    public ShenronEntity(EntityType<? extends ShenronEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Rises at {@code at} (the middle of the balls), facing {@code yaw}. */
    public static ShenronEntity summon(ServerLevel level, Vec3 at, float yaw) {
        ShenronEntity dragon = new ShenronEntity(ModEntities.SHENRON.get(), level);
        dragon.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        level.addFreshEntity(dragon);
        return dragon;
    }

    // ---------------------------------------------------------------- the path

    /**
     * A point on the spine, {@code s} = 0 at the tail to 1 at the head, in his own frame. Breathes a little with
     * {@code age}: the coil sways and the head bobs.
     */
    public static Vec3 spine(double s, float age) {
        if (s <= COIL) {
            return coil(s / COIL, age);
        }
        // The neck: from the top of the coil (behind him), up over and down to the head in front.
        Vec3 p0 = coil(1.0D, age);
        Vec3 tangent = p0.subtract(coil(0.995D, age)).normalize();
        Vec3 p3 = HEAD.add(0.0D, Math.sin(age * 0.05D) * 0.4D, 0.0D);
        Vec3 p1 = p0.add(tangent.scale(6.0D));
        Vec3 p2 = p3.add(0.0D, 4.0D, -4.0D);
        double t = (s - COIL) / (1.0D - COIL);
        double u = 1.0D - t;
        return p0.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(p3.scale(t * t * t));
    }

    /** The coil: two turns around the balls, widest at the bottom, ending right behind him. */
    private static Vec3 coil(double t, float age) {
        double theta = Math.PI * 2.0D * TURNS * t + Math.PI + 0.06D * Math.sin(age * 0.05D + t * 6.0D);
        double radius = 6.0D - 2.0D * t + 0.25D * Math.sin(age * 0.07D + t * 9.0D);
        double y = 3.0D + 21.0D * t + 0.3D * Math.sin(age * 0.06D + t * 5.0D);
        return new Vec3(radius * Math.sin(theta), y, radius * Math.cos(theta));
    }

    /** The spine point {@code arc} blocks from the tail end. */
    public static Vec3 along(double arc, float age) {
        return spine(paramAt(arc), age);
    }

    private static double paramAt(double arc) {
        if (arc <= 0.0D) {
            return 0.0D;
        }
        if (arc >= LENGTH) {
            return 1.0D;
        }
        int lo = 0, hi = SAMPLES;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (ARC[mid] < arc) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double f = (arc - ARC[lo]) / Math.max(1.0E-9D, ARC[hi] - ARC[lo]);
        return (lo + f) / SAMPLES;
    }

    /** How far along the path the head has come: he rises out of the balls, slowing as he arrives. */
    public static double headAt(float age) {
        double x = Math.min(1.0D, age / RISE_TICKS);
        return LENGTH * (1.0D - (1.0D - x) * (1.0D - x));
    }

    /** His own frame to the world (y up; +Z is where he faces). */
    public Vec3 toWorld(Vec3 local) {
        double yaw = Math.toRadians(getYRot());
        double x = local.x * Math.cos(yaw) - local.z * Math.sin(yaw);
        double z = local.x * Math.sin(yaw) + local.z * Math.cos(yaw);
        return position().add(x, local.y, z);
    }

    // ---------------------------------------------------------------- state

    public float age(float partialTick) {
        return age + partialTick;
    }

    public boolean isLeaving() {
        return entityData.get(LEAVING) >= 0;
    }

    /** 0 while he stays, then up to 1 as he dissolves. */
    public float leaving(float partialTick) {
        int since = entityData.get(LEAVING);
        return since < 0 ? 0.0F : Mth.clamp((age + partialTick - since) / LEAVE_TICKS, 0.0F, 1.0F);
    }

    /** The wish is granted (or the time is up): he speaks once more and turns to light. */
    public void depart() {
        if (isLeaving()) {
            return;
        }
        entityData.set(AGE, age);
        entityData.set(LEAVING, age);
        level().playSound(null, getX(), getY() + 20.0D, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 4.0F, 0.5F);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(AGE, 0);
        entityData.define(LEAVING, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (AGE.equals(key) && level().isClientSide) {
            age = entityData.get(AGE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            sparkle();
            return;
        }
        if (age % 20 == 0) {
            entityData.set(AGE, age);
        }
        if (!isLeaving() && age > MAX_AGE) {
            depart();
        }
        if (isLeaving() && age - entityData.get(LEAVING) > LEAVE_TICKS) {
            discard();
        }
    }

    /** Light pouring out where his tail leaves the balls as he rises; the dissolving edge as he goes. */
    private void sparkle() {
        if (age < RISE_TICKS) {
            Vec3 tail = toWorld(along(0.0D, age));
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.END_ROD, tail.x + (random.nextDouble() - 0.5D), tail.y + (random.nextDouble() - 0.5D),
                        tail.z + (random.nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
            }
        }
        float leave = leaving(0.0F);
        if (leave <= 0.0F) {
            return;
        }
        double edge = LENGTH * Math.min(1.0D, leave * 1.15D);
        Vec3 at = toWorld(along(edge, age));
        for (int i = 0; i < 8; i++) {
            level().addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.GLOW, at.x + (random.nextDouble() - 0.5D) * 3.0D,
                    at.y + (random.nextDouble() - 0.5D) * 3.0D, at.z + (random.nextDouble() - 0.5D) * 3.0D,
                    (random.nextDouble() - 0.5D) * 0.1D, 0.08D, (random.nextDouble() - 0.5D) * 0.1D);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(18.0D, 2.0D, 18.0D).expandTowards(0.0D, 34.0D, 0.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
