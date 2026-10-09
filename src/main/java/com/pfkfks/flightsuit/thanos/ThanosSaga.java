package com.pfkfks.flightsuit.thanos;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.PlanetData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Thanos saga (DESIGN.md 4-16, M16), the stone-hunting half. Chitauri start turning up in Hero City's
 * villain attacks (the herald). On Titan the Black Order each guard a stone in an arena, and Red Skull keeps the
 * Soul Stone atop the Vormir spire (a soul for a soul: 30 levels). The Time Stone is Hero City's - Captain
 * entrusts it to an ally, or it's in the vault for whoever takes the city. Every stone a player wins goes on
 * their record (PlanetData.Traveller.stones); with three, Thanos comes for them (ThanosRaid).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ThanosSaga {
    /** From this day on Chitauri join the villain attacks on Hero City even if no one has cleared Dragon Ball Earth. */
    public static final long HERALD_DAY = 20L;
    public static final int SOUL_PRICE = 30;

    private ThanosSaga() {
    }

    // ---------------------------------------------------------------- the record

    public static int stones(MinecraftServer server, java.util.UUID player) {
        return PlanetData.get(server).traveller(player).stones;
    }

    /** A stone is in this player's hands: on their record from now on. */
    public static void record(ServerPlayer player, InfinityStone stone) {
        PlanetData data = PlanetData.get(player.server);
        PlanetData.Traveller traveller = data.traveller(player.getUUID());
        if ((traveller.stones & stone.bit()) == 0) {
            traveller.stones |= stone.bit();
            data.setDirty();
            int count = Integer.bitCount(traveller.stones);
            player.sendSystemMessage(Component.translatable("thanos.flightsuit.stone_won", stone.displayName(), count).withStyle(ChatFormatting.LIGHT_PURPLE));
            ThanosRaid.onStoneWon(player, count);
        }
    }

    private static boolean has(ServerPlayer player, InfinityStone stone) {
        return (stones(player.server, player.getUUID()) & stone.bit()) != 0;
    }

    private static void giveStone(ServerPlayer player, InfinityStone stone) {
        ItemStack stack = new ItemStack(stone.item());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        record(player, stone);
    }

    /** Chitauri in Hero City's villain waves (DESIGN 4-16 전조). */
    public static boolean heraldActive(MinecraftServer server) {
        if (server.overworld().getDayTime() / 24000L >= HERALD_DAY) {
            return true;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PlanetData.get(server).traveller(player.getUUID()).cleared.getOrDefault(Planet.DBZ_EARTH, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Titan

    /** Titan's dusk never ends: no sleeping there (the bed doesn't blow up, it just won't let you). */
    @SubscribeEvent
    public static void onSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (event.getEntity().level().dimension() == Planet.TITAN.dimension()) {
            event.setResult(net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Planet.TITAN.dimension() || level.getGameTime() % 20 != 9) {
            return;
        }
        PlanetData data = PlanetData.get(level.getServer());
        BlockPos site = data.site(Planet.TITAN);
        if (site == null) {
            return;
        }
        CompoundTag world = data.world(Planet.TITAN);
        for (TitanSites place : TitanSites.values()) {
            BlockPos column = place.around(site);
            if (!world.contains(place.id()) && anyoneWithin(level, column, 96.0D) && level.isLoaded(column)) {
                world.putLong(place.id(), place.build(level, site).asLong());
                data.setDirty();
            }
            if (world.contains(place.id()) && place.keeper() != null) {
                keep(level, place, BlockPos.of(world.getLong(place.id())));
            }
        }
    }

    public static @Nullable BlockPos center(CompoundTag world, TitanSites place) {
        return world.contains(place.id()) ? BlockPos.of(world.getLong(place.id())) : null;
    }

    private static boolean anyoneWithin(ServerLevel level, BlockPos pos, double range) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - pos.getX();
            double dz = player.getZ() - pos.getZ();
            if (dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }

    /** A Black Order keeper waits in its arena while someone who hasn't won its stone is close; Red Skull is always home. */
    private static void keep(ServerLevel level, TitanSites place, BlockPos center) {
        ThanosForce keeper = place.keeper();
        BlockPos post = place.post(center);
        if (keeper == null || !level.isPositionEntityTicking(post)) {
            return;
        }
        boolean wanted = false;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - post.getX();
            double dz = player.getZ() - post.getZ();
            double range = keeper.role() == ThanosForce.Role.NPC ? 48.0D : 40.0D;
            if (dx * dx + dz * dz < range * range && (keeper.role() == ThanosForce.Role.NPC || keeper.stone() == null || !has(player, keeper.stone()))) {
                wanted = true;
            }
        }
        List<ThanosForceEntity> there = level.getEntitiesOfClass(ThanosForceEntity.class, new AABB(post).inflate(96.0D),
                entity -> entity.getForce() == keeper && !entity.isRaider());
        if (!wanted) {
            if (!anyoneWithin(level, post, 96.0D)) {
                there.forEach(Entity::discard);
            }
            return;
        }
        for (int i = 1; i < there.size(); i++) {
            there.get(i).discard();
        }
        if (there.isEmpty()) {
            ThanosForceEntity entity = ThanosForceEntity.create(level, keeper, post, 0);
            entity.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 180.0F, 0.0F);
            level.addFreshEntity(entity);
            if (keeper.isBlackOrder()) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(entity) < 64.0D * 64.0D) {
                        player.sendSystemMessage(keeper.line("arrive"));
                    }
                }
            }
        }
    }

    /** A Black Order member (or Thanos) is beaten. In an arena: its stone goes to everyone fighting there who lacks it. */
    static void onBeaten(ServerLevel level, ThanosForceEntity entity, @Nullable Entity killer) {
        if (entity.isRaider()) {
            ThanosRaid.onBeaten(level, entity);
            return;
        }
        InfinityStone stone = entity.getForce().stone();
        if (stone == null || !entity.getForce().isBlackOrder()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) < 64.0D * 64.0D && !has(player, stone)) {
                giveStone(player, stone);
            }
        }
    }

    /** Red Skull on Vormir: "a soul for a soul" - here, thirty levels of experience. */
    static void redSkull(ServerPlayer player) {
        ThanosForce skull = ThanosForce.RED_SKULL;
        if (has(player, InfinityStone.SOUL)) {
            player.sendSystemMessage(skull.line("done"));
        } else if (player.experienceLevel >= SOUL_PRICE || player.getAbilities().instabuild) {
            if (!player.getAbilities().instabuild) {
                player.giveExperienceLevels(-SOUL_PRICE);
            }
            player.sendSystemMessage(skull.line("taken"));
            giveStone(player, InfinityStone.SOUL);
        } else {
            player.sendSystemMessage(skull.line("price", SOUL_PRICE));
        }
    }

    /** "/village hero timestone": Captain's gift to an ally (Hero City trust 70+). */
    public static boolean timeStone(ServerPlayer player) {
        int trust = com.pfkfks.flightsuit.hero.HeroData.get(player.server).trust(player.getUUID());
        boolean nearCaptain = !player.level().getEntitiesOfClass(com.pfkfks.flightsuit.hero.CityHeroEntity.class, player.getBoundingBox().inflate(8.0D),
                hero -> hero.getHeroType() == com.pfkfks.flightsuit.hero.HeroType.CAPTAIN).isEmpty();
        if (!nearCaptain || trust < 70 || has(player, InfinityStone.TIME)) {
            player.sendSystemMessage(Component.translatable("thanos.flightsuit.no_time_stone").withStyle(ChatFormatting.GRAY));
            return false;
        }
        player.sendSystemMessage(com.pfkfks.flightsuit.hero.HeroType.CAPTAIN.line("time_stone", player.getName()));
        giveStone(player, InfinityStone.TIME);
        return true;
    }

    public static boolean offersTimeStone(ServerPlayer player, int trust) {
        return trust >= 70 && !has(player, InfinityStone.TIME);
    }

    /** "/planet" on Titan (and the stones line elsewhere). */
    public static void objective(ServerPlayer player) {
        int mask = stones(player.server, player.getUUID());
        player.sendSystemMessage(Component.translatable("thanos.flightsuit.goal_stones", Integer.bitCount(mask), InfinityStone.list(mask))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (player.level().dimension() != Planet.TITAN.dimension()) {
            return;
        }
        PlanetData data = PlanetData.get(player.server);
        BlockPos site = data.site(Planet.TITAN);
        if (site == null) {
            return;
        }
        CompoundTag world = data.world(Planet.TITAN);
        TitanSites best = null;
        double bestDist = Double.MAX_VALUE;
        for (TitanSites place : TitanSites.values()) {
            ThanosForce keeper = place.keeper();
            if (keeper == null || keeper.stone() == null || (mask & keeper.stone().bit()) != 0) {
                continue;
            }
            BlockPos at = center(world, place) != null ? center(world, place) : place.around(site);
            double d = player.distanceToSqr(at.getX(), player.getY(), at.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = place;
            }
        }
        if (best == null) {
            player.sendSystemMessage(Component.translatable((mask & InfinityStone.TIME.bit()) != 0 ? "thanos.flightsuit.goal_titan_all"
                    : "thanos.flightsuit.goal_titan_done").withStyle(ChatFormatting.AQUA));
            return;
        }
        BlockPos at = center(world, best) != null ? center(world, best) : best.around(site);
        double dx = at.getX() - player.getX();
        double dz = at.getZ() - player.getZ();
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
        player.sendSystemMessage(Component.translatable("thanos.flightsuit.goal_titan", best.keeper().displayName(),
                Component.translatable("edith.flightsuit.where", (int) Math.sqrt(bestDist), Component.translatable("edith.flightsuit.dir." + sector)))
                .withStyle(ChatFormatting.AQUA));
    }
}
