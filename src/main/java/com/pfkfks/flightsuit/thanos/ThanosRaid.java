package com.pfkfks.flightsuit.thanos;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.hero.CityHeroEntity;
import com.pfkfks.flightsuit.hero.HeroData;
import com.pfkfks.flightsuit.hero.HeroType;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The final battle (DESIGN.md 4-16 최종전, M16): once a player holds three Infinity Stones, Thanos comes for
 * them two days later at dusk - to their village (or to Hero City if they have none). Waves: Chitauri, then two
 * of the Black Order, then Thanos himself, wielding every stone the player does NOT have. Hero City's heroes
 * come to help an ally (trust 70+). The village is kept loaded throughout (DESIGN 4-15).
 *
 * Win (Thanos beaten, or turned to dust by the Infinity Gauntlet): the Nanotech Mark 50. Lose (still standing
 * after ten minutes): the snap - half the village's people are gone for three days - and he comes back later.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ThanosRaid {
    public static final int STONES_TO_RAID = 3;
    private static final int DELAY_DAYS = 2;
    private static final int RETRY_DAYS = 5;
    private static final int SNAP_DAYS = 3;
    private static final long RAID_TICKS = 20L * 60 * 10;
    private static final String ALLY_TAG = "flightsuit_thanos_ally";
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("flightsuit_thanos_raid", Comparator.comparingLong(ChunkPos::toLong), 100);

    private ThanosRaid() {
    }

    // ---------------------------------------------------------------- when

    static void onStoneWon(ServerPlayer player, int count) {
        if (count < STONES_TO_RAID) {
            return;
        }
        ThanosData data = ThanosData.get(player.server);
        if (data.defeated.contains(player.getUUID()) || data.raidDay.containsKey(player.getUUID())
                || data.raid != null && data.raid.player.equals(player.getUUID())) {
            return;
        }
        long day = player.server.overworld().getDayTime() / 24000L + DELAY_DAYS;
        data.raidDay.put(player.getUUID(), day);
        data.setDirty();
        player.sendSystemMessage(Component.translatable("thanos.flightsuit.he_senses").withStyle(ChatFormatting.DARK_PURPLE));
        EdithAlert.send(player, Component.translatable("thanos.flightsuit.edith_coming_title").withStyle(ChatFormatting.DARK_PURPLE),
                Component.translatable("thanos.flightsuit.edith_coming", day), null, null, EdithAlert.RED, true);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel overworld) || overworld.dimension() != Level.OVERWORLD
                || overworld.getGameTime() % 20 != 11) {
            return;
        }
        MinecraftServer server = overworld.getServer();
        ThanosData data = ThanosData.get(server);
        long day = overworld.getDayTime() / 24000L;
        long tod = overworld.getDayTime() % 24000L;
        if (data.raid != null) {
            tickRaid(server, data, data.raid);
            return;
        }
        if (tod < 13000L || tod > 20000L) {
            return;
        }
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(data.raidDay.entrySet())) {
            if (day >= entry.getValue() && begin(server, data, entry.getKey(), day)) {
                return;
            }
        }
    }

    /** "/flightsuit thanos now": he comes tonight, now. */
    public static boolean forceNow(ServerPlayer player) {
        ThanosData data = ThanosData.get(player.server);
        if (data.raid != null) {
            return false;
        }
        return begin(player.server, data, player.getUUID(), player.server.overworld().getDayTime() / 24000L);
    }

    private static boolean begin(MinecraftServer server, ThanosData data, UUID who, long day) {
        ServerPlayer player = server.getPlayerList().getPlayer(who);
        if (player == null) {
            // Nobody there to face him: he waits for a night they're around.
            data.raidDay.put(who, day + 1);
            data.setDirty();
            return false;
        }
        WarData.VillageRecord village = WarData.get(server).homeVillage(who);
        ThanosData.Raid raid;
        if (village != null && server.getLevel(village.dimension) != null) {
            raid = new ThanosData.Raid(who, village.dimension, village.hall, false, village.key());
        } else if (HeroData.get(server).built) {
            raid = new ThanosData.Raid(who, Level.OVERWORLD, HeroData.get(server).center(), true, "");
        } else {
            data.raidDay.put(who, day + 1);
            data.setDirty();
            return false;
        }
        raid.startedAt = server.overworld().getGameTime();
        raid.waveAt = raid.startedAt;
        data.raid = raid;
        data.raidDay.remove(who);
        data.setDirty();
        EdithAlert.send(player, Component.translatable("thanos.flightsuit.edith_raid_title").withStyle(ChatFormatting.DARK_RED),
                Component.translatable(raid.city ? "thanos.flightsuit.edith_raid_city" : "thanos.flightsuit.edith_raid"), raid.dimension, raid.center,
                EdithAlert.RED, true);
        return true;
    }

    // ---------------------------------------------------------------- the battle

    private static AABB arena(ThanosData.Raid raid) {
        return new AABB(raid.center).inflate(96.0D, 48.0D, 96.0D);
    }

    private static void tickRaid(MinecraftServer server, ThanosData data, ThanosData.Raid raid) {
        ServerLevel level = server.getLevel(raid.dimension);
        if (level == null) {
            data.raid = null;
            data.setDirty();
            return;
        }
        ChunkPos chunk = new ChunkPos(raid.center);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 4, chunk);
        long now = server.overworld().getGameTime();
        if (raid.won) {
            return;
        }
        if (now - raid.startedAt > RAID_TICKS) {
            lose(server, data, raid, level);
            return;
        }
        if (!level.isPositionEntityTicking(raid.center)) {
            return;
        }
        List<ThanosForceEntity> forces = level.getEntitiesOfClass(ThanosForceEntity.class, arena(raid), ThanosForceEntity::isRaider);
        int chitauri = 0;
        int blackOrder = 0;
        int thanos = 0;
        for (ThanosForceEntity force : forces) {
            switch (force.getForce().role()) {
                case MINION -> chitauri++;
                case BOSS -> blackOrder++;
                case FINAL -> thanos++;
                default -> {
                }
            }
        }
        RandomSource random = level.random;
        switch (raid.wave) {
            case 0 -> {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                for (int i = 0; i < 8; i++) {
                    spawn(level, raid, ThanosForce.CHITAURI, angle, 0);
                }
                if (HeroData.get(server).trust(raid.player) >= 70 && !raid.city) {
                    for (HeroType hero : new HeroType[]{HeroType.CAPTAIN, HeroType.IRON_MAN, HeroType.THOR, HeroType.HULK}) {
                        CityHeroEntity ally = CityHeroEntity.create(level, hero, raid.center);
                        BlockPos at = ground(level, raid.center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
                        ally.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                        ally.addTag(ALLY_TAG);
                        level.addFreshEntity(ally);
                    }
                    announce(level, raid, HeroType.CAPTAIN.line("assemble"));
                }
                announce(level, raid, ThanosForce.THANOS.line("arrive"));
                level.playSound(null, raid.center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.6F);
                next(data, raid, now);
            }
            case 1 -> {
                if (chitauri <= 2 || now - raid.waveAt > 20L * 60) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    List<ThanosForce> order = new ArrayList<>(List.of(ThanosForce.EBONY_MAW, ThanosForce.PROXIMA_MIDNIGHT,
                            ThanosForce.CORVUS_GLAIVE, ThanosForce.CULL_OBSIDIAN));
                    Collections.shuffle(order, new java.util.Random(random.nextLong()));
                    spawn(level, raid, order.get(0), angle, 0);
                    spawn(level, raid, order.get(1), angle, 0);
                    for (int i = 0; i < 4; i++) {
                        spawn(level, raid, ThanosForce.CHITAURI, angle, 0);
                    }
                    announce(level, raid, Component.translatable("thanos.flightsuit.wave_order").withStyle(ChatFormatting.DARK_PURPLE));
                    next(data, raid, now);
                }
            }
            case 2 -> {
                if (blackOrder == 0 || now - raid.waveAt > 20L * 90) {
                    int his = InfinityStone.ALL & ~ThanosSaga.stones(server, raid.player);
                    spawn(level, raid, ThanosForce.THANOS, random.nextDouble() * Math.PI * 2.0D, his);
                    announce(level, raid, ThanosForce.THANOS.line("go"));
                    announce(level, raid, Component.translatable("thanos.flightsuit.his_stones", InfinityStone.list(his)).withStyle(ChatFormatting.DARK_PURPLE));
                    next(data, raid, now);
                }
            }
            default -> {
                if (thanos == 0 && !raid.won) {
                    // He wandered off or his chunk unloaded: he comes back.
                    spawn(level, raid, ThanosForce.THANOS, random.nextDouble() * Math.PI * 2.0D,
                            InfinityStone.ALL & ~ThanosSaga.stones(server, raid.player));
                }
            }
        }
    }

    private static void next(ThanosData data, ThanosData.Raid raid, long now) {
        raid.wave++;
        raid.waveAt = now;
        data.setDirty();
    }

    private static void spawn(ServerLevel level, ThanosData.Raid raid, ThanosForce force, double angle, int stones) {
        RandomSource random = level.random;
        double a = angle + (random.nextDouble() - 0.5D) * 0.6D;
        int x = raid.center.getX() + Mth.floor(Math.cos(a) * 36.0D) + random.nextInt(5) - 2;
        int z = raid.center.getZ() + Mth.floor(Math.sin(a) * 36.0D) + random.nextInt(5) - 2;
        BlockPos at = ground(level, new BlockPos(x, raid.center.getY(), z));
        ThanosForceEntity entity = ThanosForceEntity.create(level, force, raid.center, stones);
        entity.restrictTo(raid.center, 64);
        entity.addTag(ThanosForceEntity.RAID_TAG);
        entity.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        if (force == ThanosForce.THANOS) {
            for (int i = 0; i < 2; i++) {
                net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.getX() + random.nextInt(7) - 3, at.getY(), at.getZ() + random.nextInt(7) - 3);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
    }

    private static void announce(ServerLevel level, ThanosData.Raid raid, Component line) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(raid.center.getX(), player.getY(), raid.center.getZ()) < 160.0D * 160.0D) {
                player.sendSystemMessage(line);
            }
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(raid.player);
        if (target != null && target.level() != level) {
            target.sendSystemMessage(line);
        }
    }

    /** A raider (Black Order or Thanos) withdrew - or turned to dust. Thanos gone means the battle is won. */
    static void onBeaten(ServerLevel level, ThanosForceEntity entity) {
        ThanosData data = ThanosData.get(level.getServer());
        ThanosData.Raid raid = data.raid;
        if (raid == null || entity.getForce() != ThanosForce.THANOS || raid.won) {
            return;
        }
        raid.won = true;
        win(level.getServer(), data, raid, level);
    }

    private static void clear(ServerLevel level, ThanosData.Raid raid) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, arena(raid),
                e -> e instanceof ThanosForceEntity force && force.isRaider() || e.getTags().contains(ALLY_TAG))) {
            entity.discard();
        }
    }

    private static void win(MinecraftServer server, ThanosData data, ThanosData.Raid raid, ServerLevel level) {
        clear(level, raid);
        data.raid = null;
        data.defeated.add(raid.player);
        data.raidDay.remove(raid.player);
        data.setDirty();
        announce(level, raid, Component.translatable("thanos.flightsuit.won").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        ServerPlayer player = server.getPlayerList().getPlayer(raid.player);
        if (player != null) {
            reward(player);
        } else {
            data.rewardOwed.add(raid.player);
            data.setDirty();
        }
        if (!raid.city) {
            VillageHallBlockEntity hall = Villages.hallAt(level, raid.center);
            if (hall != null) {
                hall.addNews(Component.translatable("thanos.flightsuit.news_won"));
            }
        }
    }

    private static void reward(ServerPlayer player) {
        ItemStack capsule = ModItems.capsuleFor(SuitType.NANO_MK50).createFilledCapsule();
        if (!player.getInventory().add(capsule)) {
            player.drop(capsule, false);
        }
        player.sendSystemMessage(Component.translatable("thanos.flightsuit.reward").withStyle(ChatFormatting.GOLD));
        EdithAlert.send(player, Component.translatable("thanos.flightsuit.edith_won_title").withStyle(ChatFormatting.GOLD),
                Component.translatable("thanos.flightsuit.reward"), null, null, EdithAlert.CYAN, true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ThanosData data = ThanosData.get(player.server);
            if (data.rewardOwed.remove(player.getUUID())) {
                data.setDirty();
                reward(player);
            }
        }
    }

    /** Thanos still stands when time runs out: he snaps his fingers. Half the village's people are gone for a while. */
    private static void lose(MinecraftServer server, ThanosData data, ThanosData.Raid raid, ServerLevel level) {
        clear(level, raid);
        data.raid = null;
        long day = server.overworld().getDayTime() / 24000L;
        data.raidDay.put(raid.player, day + RETRY_DAYS);
        data.setDirty();
        level.playSound(null, raid.center, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 3.0F, 1.4F);
        int gone = 0;
        if (raid.city) {
            HeroData heroes = HeroData.get(server);
            for (HeroType hero : HeroType.values()) {
                if (hero.isHero() && level.random.nextBoolean()) {
                    heroes.sendAway(hero, day + SNAP_DAYS);
                    gone++;
                }
            }
        } else {
            VillageHallBlockEntity hall = Villages.hallAt(level, raid.center);
            if (hall != null) {
                List<ResidentEntity> people = new ArrayList<>(hall.residents());
                Collections.shuffle(people, new java.util.Random(level.random.nextLong()));
                ThanosData.Snapped snapped = data.snapped.computeIfAbsent(raid.villageKey, key -> new ThanosData.Snapped());
                snapped.returnDay = day + SNAP_DAYS;
                for (int i = 0; i < people.size() / 2; i++) {
                    ResidentEntity resident = people.get(i);
                    CompoundTag saved = new CompoundTag();
                    if (resident.save(saved)) {
                        snapped.residents.add(saved);
                    }
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, resident.getX(), resident.getY() + 1.0D, resident.getZ(),
                            40, 0.3D, 0.8D, 0.3D, 0.02D);
                    resident.discard();
                    gone++;
                }
                hall.addNews(Component.translatable("thanos.flightsuit.news_snap", gone));
                hall.refreshStats();
            }
        }
        announce(level, raid, Component.translatable("thanos.flightsuit.snap", gone, SNAP_DAYS).withStyle(ChatFormatting.DARK_RED));
    }

    /** From RaidManager.noteVillage: the snapped come back when the days are up. */
    public static void onVillageLoaded(ServerLevel level, String villageKey, VillageHallBlockEntity hall) {
        ThanosData data = ThanosData.get(level.getServer());
        ThanosData.Snapped snapped = data.snapped.get(villageKey);
        if (snapped == null || level.getDayTime() / 24000L < snapped.returnDay) {
            return;
        }
        data.snapped.remove(villageKey);
        data.setDirty();
        int back = 0;
        BlockPos at = hall.getBlockPos();
        for (Tag raw : snapped.residents) {
            Entity entity = EntityType.create(((CompoundTag) raw).copy(), level).orElse(null);
            if (entity instanceof ResidentEntity resident) {
                BlockPos spot = ground(level, at.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3));
                resident.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, resident.getYRot(), 0.0F);
                level.addFreshEntity(resident);
                back++;
            }
        }
        hall.addNews(Component.translatable("thanos.flightsuit.news_back", back));
        hall.refreshStats();
    }

    public static @Nullable String status(MinecraftServer server, UUID player) {
        ThanosData data = ThanosData.get(server);
        if (data.defeated.contains(player)) {
            return "defeated";
        }
        Long day = data.raidDay.get(player);
        return day == null ? null : String.valueOf(day);
    }
}
