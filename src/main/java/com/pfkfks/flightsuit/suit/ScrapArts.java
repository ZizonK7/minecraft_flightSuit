package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Scrap Mark 0 (M18, the user's "a suit like Tony's in the cave"): iron and copper, a coal boiler for a battery.
 * Hold coal and use it to stoke the boiler. No beams on a scrap suit (first M18 test): the right hand is a
 * flamethrower (right click held - the cone is drawn by the clients, client/ClientWeapons), X spits a fan of hot
 * iron scraps, C is a rocket jump that cracks the ground on landing, V an overdrive that burns coal for speed and
 * punch, B a flare that lights the night and marks what's out there. Flying, the boiler trails smoke.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ScrapArts {
    /** Lights the flare set: where, in which level, until when (removed afterwards). */
    private record Light(ServerLevel level, BlockPos pos, long until) {
    }

    private static final List<Light> LIGHTS = new ArrayList<>();

    private ScrapArts() {
    }

    // ---------------------------------------------------------------- the boiler

    /** Coal, charcoal or a coal block used with the Mark 0 chestplate on: into the boiler. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getItemStack();
        int worth = held.is(Items.COAL) || held.is(Items.CHARCOAL) ? SuitTuning.SCRAP_COAL_ENERGY
                : held.is(Items.COAL_BLOCK) ? SuitTuning.SCRAP_COAL_ENERGY * 9 : 0;
        if (worth == 0 || SuitWeapons.armedClass(player) != SuitClass.SCRAP) {
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (SuitEnergy.get(chest) + worth / 4 > SuitEnergy.capacity(chest)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.boiler_full"), true);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            return;
        }
        SuitEnergy.receive(chest, worth);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ServerLevel level = player.serverLevel();
        Vec3 back = player.position().add(0.0D, 1.1D, 0.0D).subtract(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.3D));
        level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, back.x, back.y + 0.3D, back.z, 4, 0.1D, 0.1D, 0.1D, 0.01D);
        level.playSound(null, player.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.5F, 1.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.translatable("message.flightsuit.boiler_stoked", SuitEnergy.get(chest), SuitEnergy.capacity(chest)), true);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, SuitClass suitClass) {
        tickLights(player.serverLevel());
        if (suitClass != SuitClass.SCRAP) {
            state.rocketArmed = false;
            state.overdriveUntil = 0L;
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        // The boiler smokes while it works: flying, firing, overdriving.
        boolean working = player.getAbilities().flying || state.firing != SuitWeapons.FIRE_NONE || state.overdriveUntil > now;
        if (working && player.tickCount % 3 == 0) {
            Vec3 back = player.position().add(0.0D, 1.3D, 0.0D).subtract(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.35D));
            level.sendParticles(state.overdriveUntil > now ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, back.x, back.y, back.z, 1,
                    0.05D, 0.05D, 0.05D, 0.01D);
            if (state.overdriveUntil > now && player.tickCount % 6 == 0) {
                level.sendParticles(ParticleTypes.FLAME, back.x, back.y - 0.1D, back.z, 2, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }
        tickRocket(player, state, level);
        if (state.overdriveUntil > 0L && state.overdriveUntil <= now) {
            state.overdriveUntil = 0L;
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.8F);
            SuitWeapons.sendStatus(player, state);
        }
    }

    // ---------------------------------------------------------------- flamethrower (right click held)

    /** The right palm, where the fire comes from (the clients draw from the same spot). */
    static Vec3 palm(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.6D)).add(right.scale(0.35D)).add(0.0D, -0.3D, 0.0D);
    }

    static void startFlame(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    static void stopFlame(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    /**
     * Burning: coal every tick, and every few ticks everything in the cone ahead takes a lick of fire and catches.
     * Nothing but flesh burns - the suit's owner has a village to keep (so no fire on blocks).
     */
    static void tickFlame(ServerPlayer player, SuitWeapons.State state) {
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FLAME_DRAIN)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        ServerLevel level = player.serverLevel();
        if (state.firingTicks % 7 == 1) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        if (state.firingTicks % 14 == 3) {
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.4F, 0.6F);
        }
        if (state.firingTicks % SuitTuning.FLAME_HIT_INTERVAL != 0) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 from = palm(player);
        double cos = Math.cos(Math.toRadians(SuitTuning.FLAME_ARC_DEG / 2.0D));
        float damage = SuitTuning.FLAME_DAMAGE * SuitWeapons.overdrive(state, player);
        LivingEntity aimed = SuitWeapons.aim(player, SuitTuning.FLAME_REACH).target();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SuitTuning.FLAME_REACH),
                entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity)
                        && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player || entity == aimed)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(from);
            if (to.length() - target.getBbWidth() / 2.0D > SuitTuning.FLAME_REACH || to.normalize().dot(look) < cos
                    || !player.hasLineOfSight(target)) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.setSecondsOnFire(SuitTuning.FLAME_BURN_SECONDS);
        }
    }

    // ---------------------------------------------------------------- scrap shot (X)

    /** A fan of hot iron scraps out of the left forearm: each pellet its own hit, a wall of them close up, nothing far off. */
    static void scrapShot(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.SCRAP_SHOT_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.SCRAP_SHOT_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 from = player.getEyePosition().add(look.scale(0.6D)).subtract(right.scale(0.35D)).add(0.0D, -0.3D, 0.0D);
        float damage = SuitTuning.SCRAP_SHOT_DAMAGE * SuitWeapons.overdrive(state, player);
        for (int i = 0; i < SuitTuning.SCRAP_SHOT_PELLETS; i++) {
            Vec3 spread = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian())
                    .scale(SuitTuning.SCRAP_SHOT_SPREAD);
            Vec3 dir = look.add(spread).normalize();
            Vec3 far = from.add(dir.scale(SuitTuning.SCRAP_SHOT_RANGE));
            BlockHitResult block = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, from, end, new AABB(from, end).inflate(0.5D),
                    entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                            && !SuitWeapons.isFriendly(player, entity));
            if (hit != null) {
                end = hit.getLocation();
                LivingEntity target = (LivingEntity) hit.getEntity();
                target.invulnerableTime = 0;
                if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                    target.knockback(0.35D, -dir.x, -dir.z);
                }
            }
            // Each scrap drawn flying to where it stopped: a hot spark with a short tail.
            KiFx.shot(level, (byte) KiShots.Style.FIRE.ordinal(), 0xFFC860, 0.18F, from, dir, 2.2D, Math.max(0.5D, end.distanceTo(from)));
        }
        KiFx.burst(level, from.add(look.scale(0.3D)), KiFx.FIRE, 0.9F, 5, KiFx.FLASH);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 3, 0.1D, 0.1D, 0.1D, 0.02D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
        player.setDeltaMovement(player.getDeltaMovement().subtract(look.scale(0.12D)));
        player.hurtMarked = true;
        player.swing(InteractionHand.OFF_HAND, true);
    }

    // ---------------------------------------------------------------- rocket jump (C)

    static void rocketJump(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready) {
            SuitWeapons.cooldownMessage(player, state.skill2Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.ROCKET_JUMP_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.ROCKET_JUMP_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        player.setDeltaMovement(forward.scale(SuitTuning.ROCKET_JUMP_FORWARD).add(0.0D, SuitTuning.ROCKET_JUMP_UP, 0.0D));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        state.rocketArmed = true;
        Vec3 feet = player.position();
        KiFx.burst(level, feet.add(0.0D, 0.2D, 0.0D), KiFx.FIRE, 1.6F, 8, KiFx.BLAST);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y, feet.z, 10, 0.3D, 0.1D, 0.3D, 0.03D);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.6F);
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
    }

    /** Armed by the jump: the landing cracks the ground round the boots. */
    private static void tickRocket(ServerPlayer player, SuitWeapons.State state, ServerLevel level) {
        if (!state.rocketArmed) {
            return;
        }
        if (player.getAbilities().flying) {
            state.rocketArmed = false;
            return;
        }
        if (!player.onGround() || player.getDeltaMovement().y > 0.1D) {
            return;
        }
        state.rocketArmed = false;
        player.fallDistance = 0.0F;
        HulkbusterArts.slam(level, player, SuitTuning.ROCKET_LAND_RADIUS, SuitTuning.ROCKET_LAND_DAMAGE * SuitWeapons.overdrive(state, player),
                entity -> !SuitWeapons.isFriendly(player, entity) && (SuitWeapons.isHostile(player, entity) || entity.getLastHurtByMob() == player)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity));
        BlockState under = level.getBlockState(player.blockPosition().below());
        if (!under.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1D,
                    player.getZ(), 24, 1.0D, 0.1D, 1.0D, 0.15D);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    // ---------------------------------------------------------------- overdrive (V)

    static void overdrive(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.ultReady) {
            SuitWeapons.cooldownMessage(player, state.ultReady - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.OVERDRIVE_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.ultReady = now + SuitTuning.OVERDRIVE_COOLDOWN_TICKS;
        state.overdriveUntil = now + SuitTuning.OVERDRIVE_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        KiFx.burst(level, player.position().add(0.0D, 1.0D, 0.0D), KiFx.FIRE, 2.0F, 12, KiFx.RING);
        KiFx.aura(level, player, KiFx.FIRE, SuitTuning.OVERDRIVE_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SuitTuning.OVERDRIVE_TICKS, 1, false, false, true));
        player.displayClientMessage(Component.translatable("message.flightsuit.overdrive_on"), true);
    }

    /** The flight speed factor while the overdrive burns (SuitServerEvents). */
    public static float flightFactor(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.overdriveUntil > player.level().getGameTime() ? SuitTuning.OVERDRIVE_FLIGHT : 1.0F;
    }

    // ---------------------------------------------------------------- flare (B)

    static void flare(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FLARE_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.FLARE_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        ServerLevel level = player.serverLevel();
        // Up it goes: the highest free block within FLARE_HEIGHT above the head.
        BlockPos at = player.blockPosition().above(2);
        for (int i = 2; i < SuitTuning.FLARE_HEIGHT; i++) {
            BlockPos next = player.blockPosition().above(i + 1);
            if (!level.getBlockState(next).isAir()) {
                break;
            }
            at = next;
        }
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5D));
        Vec3 top = Vec3.atCenterOf(at);
        KiFx.shot(level, (byte) KiShots.Style.FIRE.ordinal(), KiFx.RED, 0.4F, hand, top.subtract(hand).normalize(), 0.8D, top.distanceTo(hand));
        light(level, at, now + SuitTuning.FLARE_TICKS);
        KiFx.burst(level, top, KiFx.RED, 1.2F, SuitTuning.FLARE_TICKS, KiFx.ORB);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, at, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.8F);
        // Everything out there shows itself in the red light.
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SuitTuning.FLARE_RADIUS),
                entity -> entity.isAlive() && SuitWeapons.isHostile(player, entity) && !SuitWeapons.isFriendly(player, entity))) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, SuitTuning.FLARE_TICKS, 0, false, false, false));
        }
        player.displayClientMessage(Component.translatable("message.flightsuit.flare_up"), true);
    }

    // ---------------------------------------------------------------- lights (the flare)

    /** Puts an invisible light source at {@code pos} (if it's air) until {@code until}; removed again by tickLights. */
    public static boolean light(ServerLevel level, BlockPos pos, long until) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
        LIGHTS.add(new Light(level, pos, until));
        return true;
    }

    private static void tickLights(ServerLevel level) {
        if (LIGHTS.isEmpty() || level.getGameTime() % 20L != 7L) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Light> it = LIGHTS.iterator();
        while (it.hasNext()) {
            Light light = it.next();
            if (light.level() != level) {
                continue;
            }
            if (light.until() <= now || !light.level().getBlockState(light.pos()).is(Blocks.LIGHT)) {
                if (light.level().getBlockState(light.pos()).is(Blocks.LIGHT)) {
                    light.level().removeBlock(light.pos(), false);
                }
                it.remove();
            }
        }
    }
}
