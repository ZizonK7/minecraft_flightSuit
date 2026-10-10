package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.Predicate;

/**
 * Suit skills added after the M13 test, shared by the wearer and by companion suits flying the same class:
 * - Mark 1 / Mark 50 C: the <b>unibeam</b> from the arc reactor in the chest - a second's charge, then a wide
 *   beam that goes through everything in a line;
 * - Mark 2 C: <b>cryo nova</b> - a burst of cold that freezes everything around stock-still (Stasis);
 * - Mark 3 Z: <b>shadow step</b> (Overwatch's Reaper) - the phantom doesn't fly; Z shows where it would go (behind
 *   the monster it looks at, or where it looks), a click goes - through a swirl of cards at both ends;
 * - Mark 4 companions: the spin attack (the wearer's is HeroArts.spinAttack).
 */
public final class SuitSkills {
    private static final DustParticleOptions UNIBEAM_GLOW = new DustParticleOptions(new Vector3f(0.75F, 0.95F, 1.0F), 2.2F);
    private static final DustParticleOptions UNIBEAM_CORE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F);

    private SuitSkills() {
    }

    // ---------------------------------------------------------------- unibeam

    /** C on Mark 1: starts the charge (the beam goes off a second later, wherever the wearer looks then). */
    static void startUnibeam(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready || state.unibeamCharge > 0) {
            cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.UNIBEAM_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.UNIBEAM_COOLDOWN_TICKS;
        state.unibeamCharge = SuitTuning.UNIBEAM_CHARGE_TICKS;
        SuitWeapons.sendStatus(player, state);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** Per tick while charging: the chest glows brighter; at the end, the beam. */
    static void tickUnibeam(ServerPlayer player, SuitWeapons.State state) {
        if (state.unibeamCharge <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 chest = chest(player);
        int charged = SuitTuning.UNIBEAM_CHARGE_TICKS - state.unibeamCharge;
        level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 1 + charged / 5, 0.15D, 0.15D, 0.15D, 0.01D);
        if (--state.unibeamCharge == 0) {
            unibeam(level, player, chest, player.getLookAngle(), SuitTuning.UNIBEAM_DAMAGE,
                    entity -> !SuitWeapons.isFriendly(player, entity));
        }
    }

    /** The arc reactor's centre, a little in front of the chest. */
    public static Vec3 chest(LivingEntity wearer) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, wearer.getYRot());
        return wearer.position().add(0.0D, wearer.getBbHeight() * 0.7D, 0.0D).add(forward.scale(0.35D));
    }

    /**
     * The beam: a straight line up to the first solid block (UNIBEAM_RANGE at most); every living thing within
     * reach of the line that {@code hits} allows takes the damage and is thrown back. Blocks aren't broken.
     */
    public static void unibeam(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, float damage, Predicate<LivingEntity> hits) {
        beam(level, shooter, from, dir, SuitTuning.UNIBEAM_RANGE, 1.6D, damage, 0.8D, UNIBEAM_GLOW, UNIBEAM_CORE, hits);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.6F);
    }

    /**
     * Any straight beam (the unibeam, and since M17 the Kamehameha, Galick Gun and the rest - StolenSkill): up to the
     * first solid block, {@code range} at most; everything {@code hits} allows within {@code width} of the line takes
     * the damage and {@code knockback}. Drawn as a {@code glow} with a {@code core}, bursting where it ends. Returns
     * where it ended.
     */
    public static Vec3 beam(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double range, double width, float damage,
                            double knockback, DustParticleOptions glow, DustParticleOptions core, Predicate<LivingEntity> hits) {
        return beam(level, shooter, from, dir, range, width, victim -> damage, knockback, glow, core, hits,
                width < 0.7D ? com.pfkfks.flightsuit.fx.KiFx.BEAM_THIN : com.pfkfks.flightsuit.fx.KiFx.BEAM_PLAIN);
    }

    /**
     * A beam whose damage depends on who it hits (the Special Beam Cannon bites deeper into bosses). Since the M17
     * test it's drawn by the clients (fx.KiFx) in the {@code glow} colour, {@code style} being the look of it.
     */
    public static Vec3 beam(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double range, double width,
                            java.util.function.ToDoubleFunction<LivingEntity> damage, double knockback, DustParticleOptions glow,
                            DustParticleOptions core, Predicate<LivingEntity> hits, byte style) {
        Vec3 aim = dir.normalize();
        Vec3 far = from.add(aim.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        double length = end.distanceTo(from);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(width + 1.0D),
                entity -> entity != shooter && entity.isAlive() && hits.test(entity))) {
            Vec3 centre = victim.getBoundingBox().getCenter();
            double along = centre.subtract(from).dot(aim);
            if (along < 0.0D || along > length + victim.getBbWidth()) {
                continue;
            }
            Vec3 closest = from.add(aim.scale(along));
            double reach = width + victim.getBbWidth() / 2.0D;
            if (closest.distanceToSqr(centre) > reach * reach) {
                continue;
            }
            if (KiGuard.blocks(victim, shooter, from)) {
                continue;
            }
            victim.invulnerableTime = 0;
            victim.hurt(shooter instanceof ServerPlayer player ? shooter.damageSources().playerAttack(player)
                    : shooter.damageSources().mobAttack(shooter), (float) damage.applyAsDouble(victim));
            if (knockback > 0.0D) {
                victim.knockback(knockback, -aim.x, -aim.z);
            }
        }
        // The gathered ball (if any) goes into the beam; the beam and the burst where it ends are drawn by the clients.
        com.pfkfks.flightsuit.fx.KiFx.stopCharge(level, shooter);
        com.pfkfks.flightsuit.fx.KiFx.beam(level, from, end, com.pfkfks.flightsuit.fx.KiFx.fromRgb(glow.getColor()),
                (float) Math.max(0.25D, width * 0.75D), style);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return end;
    }

    // ---------------------------------------------------------------- cryo nova

    /** C on Mark 2. */
    static void cryoNova(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready) {
            cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CRYO_NOVA_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.CRYO_NOVA_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        int frozen = cryoNova(player.serverLevel(), player, entity -> !SuitWeapons.isFriendly(player, entity)
                && (entity instanceof net.minecraft.world.entity.monster.Enemy || entity == player.getLastHurtMob()));
        player.displayClientMessage(Component.translatable("message.flightsuit.cryo_nova", frozen), true);
    }

    /** A ring of cold round {@code centre}: hurts and freezes (Stasis) whatever {@code hits} allows; returns how many. */
    public static int cryoNova(ServerLevel level, LivingEntity centre, Predicate<LivingEntity> hits) {
        double radius = SuitTuning.CRYO_NOVA_RADIUS;
        int frozen = 0;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(radius),
                entity -> entity != centre && entity.isAlive() && entity.distanceTo(centre) <= radius && hits.test(entity))) {
            victim.invulnerableTime = 0;
            victim.hurt(centre instanceof ServerPlayer player ? centre.damageSources().playerAttack(player)
                    : centre.damageSources().mobAttack(centre), SuitTuning.CRYO_NOVA_DAMAGE);
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 60);
            if (Stasis.hold(level, victim, SuitTuning.CRYO_NOVA_HOLD_TICKS, true)) {
                frozen++;
            }
        }
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0D;
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2.0D / 24.0D;
                level.sendParticles(ParticleTypes.SNOWFLAKE, centre.getX() + Math.cos(angle) * r, centre.getY() + 0.3D,
                        centre.getZ() + Math.sin(angle) * r, 2, 0.1D, 0.2D, 0.1D, 0.02D);
            }
        }
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, centre.getX(), centre.getY() + 1.0D, centre.getZ(), 30, 1.5D, 0.8D, 1.5D, 0.1D);
        level.playSound(null, centre.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, centre.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
        return frozen;
    }

    // ---------------------------------------------------------------- shadow step

    /**
     * Where a shadow step would land: the spot, the monster it goes behind (null = just a place), and whether it's
     * out in the open air (the phantom stands on a card there for a moment, then floats down).
     */
    public record StepTarget(Vec3 spot, @org.jetbrains.annotations.Nullable LivingEntity behind, boolean air) {
    }

    /** The step's cost for a spot this far off (M17: 100 FE + 7.5 a block - 400 at 40 blocks). */
    public static int stepCost(double distance) {
        return (int) Math.round(SuitTuning.SHADOW_STEP_BASE_COST + distance * SuitTuning.SHADOW_STEP_COST_PER_BLOCK);
    }

    public static double clampStepRange(double range) {
        return Math.max(SuitTuning.SHADOW_STEP_MIN_RANGE, Math.min(SuitTuning.SHADOW_STEP_RANGE, range));
    }

    /**
     * Where the wearer would blink to (client and server alike - the client shows it while aiming), looking out to
     * {@code range} blocks (SHADOW_STEP_RANGE at most; the mouse wheel sets it while aiming). After the M17 rework:
     * 1. a monster in the look: behind it;
     * 2. a block: on top of it if there's room to stand there (ledges, roofs), else just short of the face it hit;
     *    a block's top face: right there;
     * 3. nothing (the sky): the ground under the end of the look if there's any within SHADOW_STEP_GROUND_SEARCH
     *    blocks, else out there in the air (a card to stand on, then a slow fall).
     * Null if there is nowhere to stand there.
     */
    public static @org.jetbrains.annotations.Nullable StepTarget stepTarget(Level level, Player player, double range) {
        range = clampStepRange(range);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = eye.add(look.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof Enemy && entity instanceof LivingEntity && entity.isAlive() && entity.isPickable());
        if (hit != null) {
            LivingEntity behind = (LivingEntity) hit.getEntity();
            Vec3 back = behind.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            back = back.lengthSqr() < 1.0E-4D ? look.multiply(1.0D, 0.0D, 1.0D) : back;
            Vec3 spot = standable(level, player, behind.position().subtract(back.normalize().scale(behind.getBbWidth() / 2.0D + 1.2D)));
            return spot == null ? null : new StepTarget(spot, behind, false);
        }
        if (block.getType() != HitResult.Type.MISS) {
            BlockPos pos = block.getBlockPos();
            if (block.getDirection() == net.minecraft.core.Direction.UP) {
                Vec3 spot = standable(level, player, block.getLocation());
                return spot == null ? null : new StepTarget(spot, null, false);
            }
            // A ledge or a roof: up onto it, if there's room to stand on top.
            Vec3 top = onTop(level, player, pos, block.getLocation());
            if (top != null) {
                return new StepTarget(top, null, false);
            }
            Vec3 spot = standable(level, player, end.subtract(look.scale(0.6D)));
            return spot == null ? null : new StepTarget(spot, null, false);
        }
        // The open sky: the ground under the end of the look, or the air itself.
        BlockPos at = BlockPos.containing(far);
        for (int dy = 0; dy <= SuitTuning.SHADOW_STEP_GROUND_SEARCH; dy++) {
            BlockPos feet = at.below(dy);
            Vec3 spot = new Vec3(far.x, feet.getY(), far.z);
            if (room(level, player, spot) && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                return new StepTarget(spot, null, false);
            }
            if (!room(level, player, spot)) {
                break;
            }
        }
        Vec3 air = new Vec3(far.x, far.y - player.getEyeHeight(), far.z);
        return room(level, player, air) ? new StepTarget(air, null, true) : null;
    }

    /** The spot on top of the block at {@code pos} (or of the column above it) where {@code who} fits, if any. */
    private static @org.jetbrains.annotations.Nullable Vec3 onTop(Level level, LivingEntity who, BlockPos pos, Vec3 hitAt) {
        BlockPos top = pos;
        // Up to two blocks of wall above the face it hit (a 2-3 block ledge), then the top.
        for (int i = 0; i < 3 && !level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty(); i++) {
            top = top.above();
        }
        if (!level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty()) {
            return null;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(top).getCollisionShape(level, top);
        double y = top.getY() + (shape.isEmpty() ? 0.0D : shape.max(net.minecraft.core.Direction.Axis.Y));
        // Stand where the look hit, pulled in over the block so the feet are on it.
        double x = Math.max(top.getX() + 0.3D, Math.min(top.getX() + 0.7D, hitAt.x));
        double z = Math.max(top.getZ() + 0.3D, Math.min(top.getZ() + 0.7D, hitAt.z));
        Vec3 spot = new Vec3(x, y, z);
        return room(level, who, spot) ? spot : null;
    }

    /** {@code who} fits at {@code at} (feet there). */
    private static boolean room(Level level, LivingEntity who, Vec3 at) {
        return level.noCollision(who, who.getDimensions(who.getPose()).makeBoundingBox(at));
    }

    /**
     * Mark 3, aimed with Z and confirmed with a click (PhantomAimClient, which also sends the range it showed): a
     * swirl of cards opens where they stand and where they are going, and half a second later they step through.
     */
    public static void startShadowStep(ServerPlayer player, double range) {
        SuitWeapons.State state = SuitWeapons.state(player);
        long now = player.level().getGameTime();
        if (now < state.stepReady || state.stepTicks > 0) {
            return;
        }
        StepTarget target = stepTarget(player.level(), player, range <= 0.0D ? SuitTuning.SHADOW_STEP_RANGE : range);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.shadow_step_blocked"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, stepCost(target.spot().distanceTo(player.position())))) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.stepReady = now + SuitTuning.SHADOW_STEP_COOLDOWN_TICKS;
        state.stepFrom = player.position();
        state.stepTo = target.spot();
        state.stepAir = target.air();
        state.stepYaw = player.getYRot();
        if (target.behind() != null) {
            Vec3 face = target.behind().position().subtract(target.spot());
            state.stepYaw = (float) (Math.atan2(face.z, face.x) * (180.0D / Math.PI)) - 90.0F;
        }
        state.stepTicks = SuitTuning.SHADOW_STEP_CHANNEL_TICKS;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SuitTuning.SHADOW_STEP_CHANNEL_TICKS, 3, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** Per tick while a step is under way: the two card swirls turning, then the step itself. */
    static void tickShadowStep(ServerPlayer player, SuitWeapons.State state) {
        if (state.stepTicks <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int turn = SuitTuning.SHADOW_STEP_CHANNEL_TICKS - state.stepTicks;
        if (turn == 0) {
            cardSwirl(level, state.stepFrom, 1.0F, SuitTuning.SHADOW_STEP_CHANNEL_TICKS + 3);
            cardSwirl(level, state.stepTo, 0.85F, SuitTuning.SHADOW_STEP_CHANNEL_TICKS + 3);
        }
        if (turn % 3 == 0) {
            for (Vec3 at : new Vec3[]{state.stepFrom, state.stepTo}) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y + 1.0D, at.z, 3, 0.4D, 0.5D, 0.4D, 0.05D);
            }
        }
        if (--state.stepTicks > 0) {
            return;
        }
        if (SuitWeapons.armedClass(player) != SuitClass.PHANTOM || !player.isAlive()) {
            return;
        }
        player.teleportTo(level, state.stepTo.x, state.stepTo.y, state.stepTo.z, state.stepYaw, player.getXRot());
        player.fallDistance = 0.0F;
        shadowEffects(level, state.stepFrom, state.stepTo);
        if (state.stepAir) {
            // Out in the open air: a card to stand on for a moment (tickCardPlatform), then a slow fall.
            state.cardPlatformUntil = level.getGameTime() + SuitTuning.CARD_PLATFORM_TICKS;
            state.cardPlatformAt = state.stepTo;
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
    }

    /** Standing on a card in mid-air after stepping into the sky: held still in a ring of cards, then a slow fall. */
    static void tickCardPlatform(ServerPlayer player, SuitWeapons.State state) {
        if (state.cardPlatformUntil <= 0L) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now >= state.cardPlatformUntil || !player.isAlive() || SuitWeapons.armedClass(player) != SuitClass.PHANTOM) {
            endCardPlatform(player, state);
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SuitTuning.CARD_PLATFORM_FALL_TICKS, 0, false, true));
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        if (now % 2 == 0) {
            Vec3 at = player.position();
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D + now * 0.2D;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x + Math.cos(angle) * 0.7D, at.y - 0.05D, at.z + Math.sin(angle) * 0.7D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.sendParticles(ModParticles.CARD_SWIRL.get(), at.x, at.y - 0.1D, at.z, 0, now * 0.3D, 0.5D, 6.0D, 1.0D);
        }
    }

    /** Off the card (also on logging out, so nobody is left floating). */
    static void endCardPlatform(ServerPlayer player, SuitWeapons.State state) {
        if (state.cardPlatformUntil > 0L) {
            state.cardPlatformUntil = 0L;
            player.setNoGravity(false);
        }
    }

    /**
     * Cards wrapping round {@code at} (the phantom's departure and arrival, after the 4th test round): three
     * staggered rings of six, body high, that open out, turn and close in over {@code life} ticks
     * (client/CardSwirlParticle). {@code size} scales the rings.
     */
    public static void cardSwirl(ServerLevel level, Vec3 at, float size, int life) {
        for (int i = 0; i < 18; i++) {
            int ring = i / 6;
            double angle = (i % 6) * Math.PI / 3.0D + ring * 0.5D;
            level.sendParticles(ModParticles.CARD_SWIRL.get(), at.x, at.y + 0.15D + ring * 0.7D, at.z, 0,
                    angle, (1.05D + ring * 0.1D) * size, life, 1.0D);
        }
    }

    /** Cards flung out from {@code at}, spinning (the moment of the step). */
    public static void cardBurst(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 16; i++) {
            level.sendParticles(ModParticles.CARD_SWIRL.get(), at.x, at.y + 0.4D + (i % 3) * 0.55D, at.z, 0,
                    i * Math.PI / 8.0D, -2.6D, 12.0D, 1.0D);
        }
    }

    /** The step's end: a burst of cards and a wisp of shadow where it left and where it arrives, the whoosh at both. */
    public static void shadowEffects(ServerLevel level, Vec3 from, Vec3 to) {
        for (Vec3 at : new Vec3[]{from, to}) {
            cardBurst(level, at);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.0D, at.z, 4, 0.25D, 0.5D, 0.25D, 0.01D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0D, at.z, 8, 0.3D, 0.7D, 0.3D, 0.05D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    /** The nearest spot at {@code near} (a little up or down) where {@code who} can stand: room to fit and ground under it. */
    public static Vec3 standable(Level level, LivingEntity who, Vec3 near) {
        BlockPos base = BlockPos.containing(near);
        for (int dy : new int[]{0, 1, -1, 2, -2, -3}) {
            BlockPos feet = base.offset(0, dy, 0);
            Vec3 at = new Vec3(near.x, feet.getY(), near.z);
            AABB box = who.getDimensions(who.getPose()).makeBoundingBox(at);
            if (level.noCollision(who, box) && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- companion spin

    /** A Mark 4 companion's spin attack: everything {@code hits} allows round it takes a cut and is thrown back. */
    public static void spin(ServerLevel level, LivingEntity centre, double radius, float damage, Predicate<LivingEntity> hits) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(radius),
                entity -> entity != centre && entity.isAlive() && entity.distanceTo(centre) <= radius && hits.test(entity))) {
            victim.hurt(centre.damageSources().mobAttack(centre), damage);
            Vec3 push = victim.position().subtract(centre.position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                victim.knockback(0.8D, -push.x, -push.z);
            }
        }
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0D;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.getX() + Math.cos(angle) * radius * 0.7D, centre.getY() + 1.0D,
                    centre.getZ() + Math.sin(angle) * radius * 0.7D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, centre.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.2F, 0.6F);
    }

    private static void cooldownMessage(ServerPlayer player, long ticksLeft) {
        player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown", String.format("%.1f", ticksLeft / 20.0F)), true);
    }
}
