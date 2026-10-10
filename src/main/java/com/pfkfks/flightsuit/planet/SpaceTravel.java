package com.pfkfks.flightsuit.planet;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SpaceTravelS2CPacket;
import com.pfkfks.flightsuit.suit.MainStation;
import com.pfkfks.flightsuit.suit.RemoteLink;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Flying between worlds (DESIGN.md 4-16, M15): launch pad → climb → across space (a short cinematic while the
 * pilot is moved) → a new ship comes down at the other end. On a planet the landed ship is the base: home, or
 * a remote link to the suit docked at the main station back home (DESIGN.md 4-15: 차원을 넘는 원격).
 *
 * Chat buttons run "/spaceship launch <planet>", "/spaceship return", "/spaceship remote" (no cheats needed;
 * each checks the player is at their pad or ship).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class SpaceTravel {
    public static final int LAUNCH_COST = 100_000;
    /** How long the "crossing space" picture stays up on the pilot's screen. */
    public static final int CROSSING_TICKS = 80;
    private static final int DESCENT_HEIGHT = 70;

    private SpaceTravel() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spaceship")
                .then(Commands.literal("launch").then(Commands.argument("planet", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Planet.values()).map(Planet::id), builder))
                        .executes(ctx -> launch(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "planet")) ? 1 : 0)))
                .then(Commands.literal("return").executes(ctx -> goHome(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("remote").executes(ctx -> remote(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
    }

    /** Nobody climbs out of a ship in flight. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        Entity rider = event.getEntityMounting();
        // Server only (the client can't see letOut and would keep the pilot stuck aboard); and never against
        // the game itself - a pilot who dies or leaves must come off.
        if (rider.level().isClientSide || !rider.isAlive() || rider.isRemoved()) {
            return;
        }
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SpaceshipEntity ship && !ship.mayLeave()) {
            event.setCanceled(true);
        }
    }

    /** The live pilot aboard (not a stale player object left by a death or a relog). */
    static @Nullable ServerPlayer pilotOf(SpaceshipEntity ship) {
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof ServerPlayer player && player.isAlive() && !player.isRemoved()
                    && player.server.getPlayerList().getPlayer(player.getUUID()) == player) {
                return player;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- menus

    public static void padMenu(ServerPlayer player, LaunchPadBlockEntity pad) {
        int stored = pad.energy().getEnergyStored();
        player.sendSystemMessage(Component.translatable("space.flightsuit.pad", stored, LAUNCH_COST).withStyle(ChatFormatting.AQUA));
        if (!pad.canLaunch()) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.pad_charging", stored * 100 / LAUNCH_COST).withStyle(ChatFormatting.GRAY));
            return;
        }
        MutableComponent line = Component.empty();
        for (Planet planet : Planet.values()) {
            if (!PlanetStory.canFlyTo(player, planet)) {
                continue;
            }
            line.append(button(Component.translatable("space.flightsuit.button_launch", planet.displayName()), "/spaceship launch " + planet.id(),
                    Component.translatable("space.flightsuit.hint." + planet.id()), ChatFormatting.GOLD)).append(" ");
        }
        player.sendSystemMessage(line);
    }

    public static void shipMenu(ServerPlayer player, SpaceshipEntity ship) {
        if (ship.owner() != null && !ship.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("space.flightsuit.not_yours"), true);
            return;
        }
        Planet planet = ship.planet();
        player.sendSystemMessage(Component.translatable("space.flightsuit.ship", planet != null ? planet.displayName()
                : Component.translatable("space.flightsuit.home")).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.empty()
                .append(button(Component.translatable("space.flightsuit.button_return"), "/spaceship return",
                        Component.translatable("space.flightsuit.button_return_hint"), ChatFormatting.GOLD))
                .append("  ")
                .append(button(Component.translatable("space.flightsuit.button_remote"), "/spaceship remote",
                        Component.translatable("space.flightsuit.button_remote_hint"), ChatFormatting.AQUA)));
    }

    private static MutableComponent button(Component label, String command, Component hint, ChatFormatting color) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hint)));
    }

    // ---------------------------------------------------------------- launch and return

    private static boolean launch(ServerPlayer player, String planetId) {
        Planet planet = Planet.byId(planetId);
        ServerLevel level = player.serverLevel();
        if (planet == null || player.server.getLevel(planet.dimension()) == null || !PlanetStory.canFlyTo(player, planet)) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.no_planet").withStyle(ChatFormatting.RED));
            return false;
        }
        if (level.dimension() == planet.dimension()) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.already_there").withStyle(ChatFormatting.GRAY));
            return false;
        }
        LaunchPadBlockEntity pad = padNear(player);
        if (pad == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_pad").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!pad.canLaunch()) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.pad_charging",
                    pad.energy().getEnergyStored() * 100 / LAUNCH_COST).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!readyToFly(player)) {
            return false;
        }
        if (!level.canSeeSky(pad.getBlockPos().above(3))) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_sky").withStyle(ChatFormatting.GRAY));
            return false;
        }
        pad.spend();
        PlanetData data = PlanetData.get(player.server);
        data.traveller(player.getUUID()).home = new PlanetData.Home(level.dimension(), pad.getBlockPos());
        data.setDirty();
        BlockPos top = pad.getBlockPos().above();
        SpaceshipEntity ship = SpaceshipEntity.create(level, player.getUUID(), null, top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D, player.getYRot());
        level.addFreshEntity(ship);
        player.startRiding(ship, true);
        ship.launch(planet);
        player.sendSystemMessage(Component.translatable("space.flightsuit.liftoff", planet.displayName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static boolean goHome(ServerPlayer player) {
        SpaceshipEntity ship = ownShipNear(player);
        if (ship == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_ship").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!readyToFly(player)) {
            return false;
        }
        player.startRiding(ship, true);
        ship.launch(null);
        player.sendSystemMessage(Component.translatable("space.flightsuit.liftoff_home").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static boolean readyToFly(ServerPlayer player) {
        if (RemoteLink.isActive(player) || player.isPassenger()) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.busy").withStyle(ChatFormatting.GRAY));
            return false;
        }
        return true;
    }

    private static @Nullable LaunchPadBlockEntity padNear(ServerPlayer player) {
        BlockPos at = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 1, 3))) {
            if (player.level().getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) {
                return pad;
            }
        }
        return null;
    }

    private static @Nullable SpaceshipEntity ownShipNear(ServerPlayer player) {
        List<SpaceshipEntity> ships = player.level().getEntitiesOfClass(SpaceshipEntity.class, player.getBoundingBox().inflate(6.0D),
                ship -> !ship.inFlight() && player.getUUID().equals(ship.owner()));
        return ships.isEmpty() ? null : ships.get(0);
    }

    /** A second before the top of the climb: the star field starts, so the jump itself happens behind it. */
    static void beginCrossing(SpaceshipEntity ship) {
        ServerPlayer pilot = pilotOf(ship);
        if (pilot == null) {
            return;
        }
        Planet planet = ship.planet();
        Component where = planet != null ? planet.displayName() : Component.translatable("space.flightsuit.home");
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pilot), new SpaceTravelS2CPacket(where, CROSSING_TICKS + 20));
    }

    /**
     * Top of the climb: the pilot crosses space (the cinematic covers the move) and a new ship starts coming down
     * over the far side - the planet's landing site, or the launch pad back home.
     */
    static void cross(SpaceshipEntity ship) {
        ServerPlayer pilot = pilotOf(ship);
        ship.letOut();
        ship.discard();
        if (pilot == null) {
            return;
        }
        MinecraftServer server = pilot.server;
        Planet planet = ship.planet();
        ServerLevel target;
        BlockPos ground;
        if (planet != null) {
            target = server.getLevel(planet.dimension());
            if (target == null) {
                target = server.overworld();
            }
            ground = landingSite(target, planet, pilot);
        } else {
            PlanetData.Home home = PlanetData.get(server).traveller(pilot.getUUID()).home;
            ServerLevel homeLevel = home == null ? null : server.getLevel(home.dimension());
            if (homeLevel != null) {
                homeLevel.getChunkAt(home.pos());
            }
            if (homeLevel != null && homeLevel.getBlockEntity(home.pos()) instanceof LaunchPadBlockEntity) {
                target = homeLevel;
                ground = home.pos().above();
            } else {
                target = server.overworld();
                BlockPos spawn = target.getSharedSpawnPos();
                target.getChunkAt(spawn);
                ground = new BlockPos(spawn.getX(), target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
            }
        }
        double x = ground.getX() + 0.5D;
        double z = ground.getZ() + 0.5D;
        double y = ground.getY() + DESCENT_HEIGHT;
        pilot.teleportTo(target, x, y, z, pilot.getYRot(), 0.0F);
        pilot.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        SpaceshipEntity next = SpaceshipEntity.create(target, pilot.getUUID(), planet, x, y, z, pilot.getYRot());
        next.descend(ground.getY(), pilot.getUUID());
        target.addFreshEntity(next);
        pilot.startRiding(next, true);
    }

    /** Touchdown: the pilot steps out. At home the ship folds back into the pad; on a planet it stays as the base. */
    static void landed(SpaceshipEntity ship) {
        ServerPlayer pilot = pilotOf(ship);
        ship.letOut();
        if (pilot != null) {
            pilot.removeEffect(MobEffects.SLOW_FALLING);
            pilot.fallDistance = 0.0F;
            Vec3 out = ship.position().add(Vec3.directionFromRotation(0.0F, ship.getYRot()).scale(2.2D));
            int ground = ship.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(out.x), Mth.floor(out.z));
            pilot.teleportTo(out.x, Math.max(ground, ship.getY()), out.z);
        }
        Planet planet = ship.planet();
        if (planet == null) {
            ship.discard();
            if (pilot != null) {
                pilot.sendSystemMessage(Component.translatable("space.flightsuit.home_again").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        if (pilot != null) {
            pilot.sendSystemMessage(Component.translatable("space.flightsuit.landed", planet.displayName()).withStyle(ChatFormatting.GOLD));
            pilot.sendSystemMessage(Component.translatable("space.flightsuit.landed_hint").withStyle(ChatFormatting.GRAY));
            PlanetStory.onLanded(pilot, planet);
        }
    }

    /** The planet's landing site (found once: firm ground near the middle of the world), a few steps apart per pilot. */
    private static BlockPos landingSite(ServerLevel level, Planet planet, ServerPlayer pilot) {
        PlanetData data = PlanetData.get(level.getServer());
        BlockPos site = data.site(planet);
        if (site == null) {
            site = firmGround(level, 0, 0);
            data.setSite(planet, site);
        }
        int dx = Math.floorMod(pilot.getUUID().hashCode(), 9) - 4;
        int dz = Math.floorMod(pilot.getUUID().hashCode() >> 8, 9) - 4;
        int x = site.getX() + dx * 3;
        int z = site.getZ() + dz * 3;
        level.getChunkAt(new BlockPos(x, 0, z));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
            return site;
        }
        return new BlockPos(x, y, z);
    }

    /**
     * Walks out from (x, z) until the terrain stands above the sea - asking the generator (cheap, nothing gets
     * generated), so only the chunk finally chosen is loaded.
     */
    static BlockPos firmGround(ServerLevel level, int x0, int z0) {
        int bestX = x0;
        int bestZ = z0;
        search:
        for (int ring = 0; ring < 40; ring++) {
            int steps = Math.max(1, ring * 8);
            for (int step = 0; step < steps; step++) {
                double angle = step * Math.PI * 2.0D / steps;
                int x = x0 + (int) Math.round(Math.cos(angle) * ring * 32);
                int z = z0 + (int) Math.round(Math.sin(angle) * ring * 32);
                int base = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
                        level.getChunkSource().randomState());
                if (base > level.getSeaLevel() + 2) {
                    bestX = x;
                    bestZ = z;
                    break search;
                }
            }
        }
        level.getChunkAt(new BlockPos(bestX, 0, bestZ));
        return new BlockPos(bestX, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bestX, bestZ), bestZ);
    }

    // ---------------------------------------------------------------- remote link from a planet

    /** From the landed ship: link to the suit docked at the main station back home (another dimension). */
    private static boolean remote(ServerPlayer player) {
        if (ownShipNear(player) == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.need_ship").withStyle(ChatFormatting.GRAY));
            return false;
        }
        MainStation.Link link = MainStation.get(player);
        ServerLevel level = link == null ? null : player.server.getLevel(link.dimension());
        if (level == null) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.no_main_station").withStyle(ChatFormatting.GRAY));
            return false;
        }
        level.getChunkAt(link.pos());
        if (!(level.getBlockEntity(link.pos()) instanceof SuitStationBlockEntity station) || !station.hasSuit()) {
            player.sendSystemMessage(Component.translatable("space.flightsuit.no_main_station").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!RemoteLink.canConnect(player) || !RemoteLink.canPilot(player, station.getParts())) {
            return false;
        }
        float yaw = station.getBlockState().getValue(SuitStationBlock.FACING).toYRot();
        Vec3 spot = Vec3.atBottomCenterOf(station.getBlockPos()).add(0.0D, 0.25D, 0.0D);
        RemoteLink.start(player, level, station.takeAll(), spot, yaw, false);
        return true;
    }
}
