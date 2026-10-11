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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Scrap Mark 0 (M18, the user's "a suit like Tony's in the cave"): iron and copper, a coal boiler for a battery.
 * Hold coal and use it to stoke the boiler; the repulsor is weak, and the suit's four moves are a flamethrower (X),
 * a rocket jump that cracks the ground on landing (C), an overdrive that burns coal for speed and punch (V) and a
 * flare that lights the night and marks what's out there (B). Flying, the boiler trails smoke.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ScrapArts {
    /** Lights the flare and the light orb set: where, in which level, until when (removed afterwards). */
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
            state.flameTicks = 0;
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
        tickFlame(player, state, level);
        tickRocket(player, state, level);
        if (state.overdriveUntil > 0L && state.overdriveUntil <= now) {
            state.overdriveUntil = 0L;
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.8F);
            SuitWeapons.sendStatus(player, state);
        }
    }

    // ---------------------------------------------------------------- flamethrower (X)

    static void flamethrower(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill1Ready || state.flameTicks > 0) {
            SuitWeapons.cooldownMessage(player, state.skill1Ready - now);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.FLAME_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill1Ready = now + SuitTuning.FLAME_COOLDOWN_TICKS;
        state.flameTicks = SuitTuning.FLAME_TICKS;
        SuitWeapons.sendStatus(player, state);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static void tickFlame(ServerPlayer player, SuitWeapons.State state, ServerLevel level) {
        if (state.flameTicks <= 0) {
            return;
        }
        state.flameTicks--;
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 palm = player.getEyePosition().add(look.scale(0.6D)).add(right.scale(0.35D)).add(0.0D, -0.3D, 0.0D);
        // A tongue of fire: flame particles thrown along the look, widening as they go.
        for (int i = 0; i < 4; i++) {
            double along = 0.4D + level.random.nextDouble() * 1.2D;
            Vec3 spread = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).scale(0.08D);
            Vec3 v = look.scale(0.35D + along * 0.15D).add(spread);
            level.sendParticles(ParticleTypes.FLAME, palm.x, palm.y, palm.z, 0, v.x, v.y, v.z, 1.0D);
        }
        if (state.flameTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, palm.x + look.x * 2.0D, palm.y + look.y * 2.0D, palm.z + look.z * 2.0D, 1,
                    0.3D, 0.3D, 0.3D, 0.01D);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
        if (state.flameTicks % 2 != 0) {
            return;
        }
        double cos = Math.cos(Math.toRadians(SuitTuning.FLAME_ARC_DEG / 2.0D));
        float damage = SuitTuning.FLAME_DAMAGE * SuitWeapons.overdrive(state, player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SuitTuning.FLAME_REACH),
                entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity) && (entity instanceof Enemy || entity.getLastHurtByMob() == player)
                        && !com.pfkfks.flightsuit.war.RaidMember.isNoThreat(entity))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
            if (to.length() - target.getBbWidth() / 2.0D > SuitTuning.FLAME_REACH || to.normalize().dot(look) < cos) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.setSecondsOnFire(SuitTuning.FLAME_BURN_SECONDS);
        }
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
        level.sendParticles(ParticleTypes.FLAME, feet.x, feet.y, feet.z, 20, 0.3D, 0.1D, 0.3D, 0.08D);
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
                entity -> !SuitWeapons.isFriendly(player, entity) && (entity instanceof Enemy || entity.getLastHurtByMob() == player)
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
                entity -> entity.isAlive() && entity instanceof Enemy && !SuitWeapons.isFriendly(player, entity))) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, SuitTuning.FLARE_TICKS, 0, false, false, false));
        }
        player.displayClientMessage(Component.translatable("message.flightsuit.flare_up"), true);
    }

    // ---------------------------------------------------------------- lights (the flare, Mark 2's light orb)

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
