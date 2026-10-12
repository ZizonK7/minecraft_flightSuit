package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.network.EscapeS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.SpaceTravel;
import com.pfkfks.flightsuit.planet.SpaceshipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mark 2's emergency escape (B, 2026-10-12 - the explorer's way out, DESIGN.md 5장 "게임 흐름 2차"): a flash where
 * it takes off (MarkArts.flash), then the suit flies the wearer home - to the suit's own station (else the main one)
 * in this dimension, or on a planet to the landed spaceship. Up first (straight up through rock when underground, the
 * laser cutting the way), across at cruising height, then down in front of the station. Hits still land on the way.
 * <p>
 * Movement is the client's (client/EscapeClient flies the route this sends); the server cuts the rock, watches for
 * arrival and for a flight that's got nowhere for a while, and jumps a very long trip ahead.
 */
public final class EscapeFlight {
    private EscapeFlight() {
    }

    /** True while the suit is flying this player home (the hover drain is off meanwhile - SuitServerEvents). */
    public static boolean active(ServerPlayer player) {
        SuitWeapons.State state = SuitWeapons.existing(player);
        return state != null && state.escapeTo != null;
    }

    /** B: off we go - or, pressed again on the way, the wearer takes over. */
    static void press(ServerPlayer player, SuitWeapons.State state) {
        if (state.escapeTo != null) {
            end(player, state, "message.flightsuit.escape_cancel");
            return;
        }
        long now = player.level().getGameTime();
        if (now < state.skill3Ready) {
            SuitWeapons.cooldownMessage(player, state.skill3Ready - now);
            return;
        }
        if (!WornSuit.of(player).canFly()) {
            player.displayClientMessage(Component.translatable("message.flightsuit.escape_need_set"), true);
            return;
        }
        if (RemoteLink.isActive(player) || player.isPassenger()) {
            player.displayClientMessage(Component.translatable("space.flightsuit.busy"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 home = home(player, level);
        if (home == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.escape_no_home"), true);
            return;
        }
        if (home.distanceTo(player.position()) < 6.0D) {
            player.displayClientMessage(Component.translatable("message.flightsuit.escape_already_home"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.ESCAPE_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill3Ready = now + SuitTuning.ESCAPE_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        state.escapeTo = home;
        state.escapeDim = level.dimension();
        state.escapeCruise = cruise(player, level, home);
        state.escapeTicks = 0;
        state.escapeJumped = false;
        state.escapeDescending = false;
        state.escapeBest = Double.MAX_VALUE;
        state.escapeStuck = 0;

        Vec3 at = player.position().add(0.0D, 1.0D, 0.0D);
        MarkArts.flash(player, state, at);
        SuitServerEvents.grantFlightNow(player);
        player.setDeltaMovement(0.0D, SuitTuning.ESCAPE_CLIMB_SPEED, 0.0D);
        player.hurtMarked = true;
        send(player, state);
        KiFx.burst(level, player.position(), KiFx.KAME, 2.0F, 12, KiFx.RING);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
        player.displayClientMessage(Component.translatable("message.flightsuit.escape_start", (int) Math.round(home.distanceTo(player.position()))), true);
    }

    static void tick(ServerPlayer player, SuitWeapons.State state, @Nullable SuitClass suitClass) {
        if (state.escapeTo == null) {
            return;
        }
        if (suitClass != SuitClass.STEALTH || !WornSuit.of(player).canFly() || !player.isAlive() || player.isPassenger()
                || !player.level().dimension().equals(state.escapeDim) || RemoteLink.isActive(player)) {
            end(player, state, "message.flightsuit.escape_cut");
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 to = state.escapeTo;
        Vec3 pos = player.position();
        double horizontal = Math.sqrt(Mth.square(to.x - pos.x) + Mth.square(to.z - pos.z));
        double distance = pos.distanceTo(to);
        player.fallDistance = 0.0F;
        if (distance < 1.5D || horizontal < 2.5D && player.onGround() && Math.abs(pos.y - to.y) < 3.0D) {
            arrive(player, state, false);
            return;
        }
        if (++state.escapeTicks > SuitTuning.ESCAPE_MAX_TICKS) {
            arrive(player, state, true);
            return;
        }
        boolean descending = horizontal <= SuitTuning.ESCAPE_DESCENT_RADIUS;
        if (descending != state.escapeDescending) {
            state.escapeDescending = descending;
            state.escapeBest = Double.MAX_VALUE;
            state.escapeStuck = 0;
        }
        boolean climbing = !descending && pos.y < state.escapeCruise - 3;
        if (climbing && !cutAhead(player, level)) {
            end(player, state, "message.flightsuit.escape_blocked");
            state.skill3Ready = level.getGameTime() + SuitTuning.ESCAPE_FAILED_COOLDOWN_TICKS;
            SuitWeapons.sendStatus(player, state);
            return;
        }
        // A very long way at cruising height: a sonic boom, and the suit is most of the way there.
        if (!descending && !state.escapeJumped && pos.y >= state.escapeCruise - 6 && horizontal > SuitTuning.ESCAPE_JUMP_OVER) {
            state.escapeJumped = true;
            Vec3 flat = new Vec3(to.x - pos.x, 0.0D, to.z - pos.z).normalize();
            Vec3 ahead = new Vec3(to.x, state.escapeCruise, to.z).subtract(flat.scale(SuitTuning.ESCAPE_JUMP_TO));
            boom(level, pos);
            player.teleportTo(ahead.x, ahead.y, ahead.z);
            boom(level, ahead);
            state.escapeBest = Double.MAX_VALUE;
            return;
        }
        // Getting nowhere: a roof over the station or a cliff on the way down is done in one go; anywhere else the
        // way is shut.
        double score = descending ? distance : horizontal + Math.max(0.0D, state.escapeCruise - pos.y);
        if (score < state.escapeBest - 0.3D) {
            state.escapeBest = score;
            state.escapeStuck = 0;
        } else if (++state.escapeStuck > SuitTuning.ESCAPE_STUCK_TICKS) {
            if (descending) {
                arrive(player, state, true);
            } else {
                end(player, state, "message.flightsuit.escape_blocked");
                state.skill3Ready = level.getGameTime() + SuitTuning.ESCAPE_FAILED_COOLDOWN_TICKS;
                SuitWeapons.sendStatus(player, state);
            }
            return;
        }
        if (state.escapeTicks % 4 == 0) {
            KiFx.burst(level, pos.add(0.0D, 0.4D, 0.0D), KiFx.KAME, 0.6F, 6, KiFx.SPARK);
        }
    }

    // ---------------------------------------------------------------- where home is

    /** The suit's own station (else the main one) in this dimension, or on a planet the landed ship; null: nowhere. */
    private static @Nullable Vec3 home(ServerPlayer player, ServerLevel level) {
        List<ItemStack> pieces = new ArrayList<>();
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            pieces.add(player.getItemBySlot(slot));
        }
        MainStation.Link link = SuitHome.of(pieces);
        if (!MainStation.isInPlayerDimension(player, link)) {
            link = MainStation.get(player);
        }
        if (MainStation.isInPlayerDimension(player, link)) {
            level.getChunkAt(link.pos());
            BlockState block = level.getBlockState(link.pos());
            if (block.getBlock() instanceof SuitStationBlock) {
                Direction facing = block.getValue(SuitStationBlock.FACING);
                BlockPos front = link.pos().relative(facing, 2);
                return new Vec3(front.getX() + 0.5D, front.getY(), front.getZ() + 0.5D);
            }
        }
        Planet planet = Planet.of(level.dimension());
        if (planet == null) {
            return null;
        }
        List<SpaceshipEntity> ships = level.getEntitiesOfClass(SpaceshipEntity.class, player.getBoundingBox().inflate(256.0D),
                ship -> !ship.inFlight() && player.getUUID().equals(ship.owner()));
        if (!ships.isEmpty()) {
            SpaceshipEntity ship = ships.get(0);
            return new Vec3(ship.getX() + ship.getBbWidth() / 2.0D + 1.5D, ship.getY(), ship.getZ());
        }
        BlockPos site = SpaceTravel.landingPoint(level, planet, player);
        return new Vec3(site.getX() + 4.5D, site.getY(), site.getZ() + 0.5D);
    }

    /** Cruising height: well over the ground at both ends; under the roof in a dimension that has one. */
    private static int cruise(ServerPlayer player, ServerLevel level, Vec3 home) {
        int max = level.getMaxBuildHeight() - 8;
        if (level.dimensionType().hasCeiling()) {
            return Math.min(max, (int) Math.max(player.getY(), home.y) + 3);
        }
        BlockPos homePos = BlockPos.containing(home);
        level.getChunkAt(homePos);
        int ground = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, homePos.getX(), homePos.getZ()));
        return Math.min(max, Math.max(ground + SuitTuning.ESCAPE_CRUISE_ABOVE, SuitTuning.ESCAPE_CRUISE_FLOOR));
    }

    // ---------------------------------------------------------------- on the way

    /**
     * Climbing: the laser cuts whatever is over the head, a few blocks ahead of where the server sees the wearer (the
     * client is a little ahead). Nothing dropped - it's a way out, not a mine. False: something it can't cut.
     */
    private static boolean cutAhead(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX + 0.01D);
        int maxX = Mth.floor(box.maxX - 0.01D);
        int minZ = Mth.floor(box.minZ + 0.01D);
        int maxZ = Mth.floor(box.maxZ - 0.01D);
        int fromY = Mth.floor(box.maxY - 0.01D);
        boolean cut = false;
        for (int y = fromY; y <= fromY + SuitTuning.ESCAPE_DRILL_AHEAD; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState block = level.getBlockState(pos);
                    if (block.isAir() || block.getCollisionShape(level, pos).isEmpty()) {
                        continue;
                    }
                    if (block.getDestroySpeed(level, pos) < 0.0F || level.getBlockEntity(pos) != null || !level.mayInteract(player, pos)) {
                        return false;
                    }
                    level.destroyBlock(pos, false, player);
                    cut = true;
                }
            }
        }
        if (cut && player.tickCount % 3 == 0) {
            Vec3 head = player.getEyePosition();
            KiFx.beam(level, head, head.add(0.0D, SuitTuning.ESCAPE_DRILL_AHEAD, 0.0D), KiFx.KAME, 0.6F, 4, KiFx.BEAM_SPIRAL, null, KiFx.AT_POS, null);
        }
        return true;
    }

    private static void boom(ServerLevel level, Vec3 at) {
        KiFx.burst(level, at, KiFx.WHITE, 4.0F, 10, KiFx.RING);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    // ---------------------------------------------------------------- the end of it

    /** Home. {@code skip}: the last stretch couldn't be flown (a roof, a timeout) - set down there in one go. */
    private static void arrive(ServerPlayer player, SuitWeapons.State state, boolean skip) {
        Vec3 to = state.escapeTo;
        ServerLevel level = player.serverLevel();
        if (skip) {
            boom(level, player.position());
            player.teleportTo(to.x, to.y, to.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        KiFx.burst(level, to.add(0.0D, 0.1D, 0.0D), KiFx.KAME, 2.0F, 10, KiFx.RING);
        level.playSound(null, BlockPos.containing(to), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.4F);
        end(player, state, "message.flightsuit.escape_home");
    }

    private static void end(ServerPlayer player, SuitWeapons.State state, String message) {
        state.escapeTo = null;
        state.escapeDescending = false;
        send(player, state);
        player.displayClientMessage(Component.translatable(message), true);
    }

    private static void send(ServerPlayer player, SuitWeapons.State state) {
        Vec3 to = state.escapeTo;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                to == null ? EscapeS2CPacket.off() : new EscapeS2CPacket(true, to, state.escapeCruise));
    }
}
