package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Three Kingdoms raids on the player's villages (DESIGN.md 4-11, M10).
 *
 * Every few days a kingdom the player isn't friends with sends an army: the scouts report it in the afternoon,
 * it arrives at dusk in three waves (foot soldiers, then archers - burning arrows if it's a fire attack -
 * then a general with an escort). While a raid is on, the village is kept loaded so it really happens even
 * with the owner far away or in another dimension, and EDITH tells them (4-15). Each army has a random will
 * to fight: once enough of it has fallen (or its general is beaten, or Guan Yu loses his duel), the rest lay
 * down their arms and the owner chooses to recruit them or let them go.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class RaidManager {
    private static final TicketType<ChunkPos> RAID_TICKET =
            TicketType.create("flightsuit_raid", Comparator.comparingLong(ChunkPos::toLong), 100);
    /** Keeps the hall chunk and five chunks around it entity-ticking (the army forms up 56 blocks out). */
    private static final int TICKET_DISTANCE = 7;

    private RaidManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % 20 != 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        WarData data = WarData.get(server);
        for (RaidState raid : new ArrayList<>(data.raids().values())) {
            tickRaid(server, data, raid);
        }
        for (WarData.VillageRecord village : new ArrayList<>(data.villages().values())) {
            schedule(server, data, village);
        }
    }

    // ---------------------------------------------------------------- villages

    /** The hall checks in now and then (VillageHallBlockEntity.serverTick), so raids know about it. */
    public static void noteVillage(VillageHallBlockEntity hall) {
        if (!(hall.getLevel() instanceof ServerLevel level)) {
            return;
        }
        WarData data = WarData.get(level.getServer());
        WarData.VillageRecord record = data.village(level.dimension(), hall.getBlockPos());
        if (!Objects.equals(record.owner, hall.getOwner()) || record.population != hall.getPopulation()) {
            record.owner = hall.getOwner();
            record.population = hall.getPopulation();
            data.setDirty();
        }
        if (record.pendingRecruits > 0 || record.pendingGenerals != 0) {
            bringPrisoners(level, data, record, hall);
        }
        Diplomacy.onVillageLoaded(level, data, record, hall);
        com.pfkfks.flightsuit.hero.HeroCity.onVillageLoaded(level, record.key(), hall);
    }

    /** Prisoners taken at a fortress arrive at the village: soldiers join as residents, generals as defenders. */
    private static void bringPrisoners(ServerLevel level, WarData data, WarData.VillageRecord record, VillageHallBlockEntity hall) {
        net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(hall.getBlockPos()).add(2.0D, 1.0D, 2.0D);
        for (int i = 0; i < record.pendingRecruits; i++) {
            ResidentEntity.spawnRecruit(level, hall, at, ResidentJob.SOLDIER);
        }
        for (General general : General.values()) {
            if ((record.pendingGenerals & (1 << general.ordinal())) != 0) {
                GeneralEntity entity = GeneralEntity.create(level, general, -1, hall.getBlockPos());
                entity.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                level.addFreshEntity(entity);
                entity.recruit(hall);
            }
        }
        hall.addNews(Component.translatable("news.flightsuit.prisoners_arrived", record.pendingRecruits + Integer.bitCount(record.pendingGenerals)));
        record.pendingRecruits = 0;
        record.pendingGenerals = 0;
        data.setDirty();
        hall.refreshStats();
    }

    /** The hall was broken: no more raids on it. */
    public static void forgetVillage(ServerLevel level, BlockPos hall) {
        WarData data = WarData.get(level.getServer());
        String key = WarData.keyOf(level.dimension(), hall);
        RaidState raid = data.raidOf(key);
        if (raid != null) {
            data.endRaid(raid);
        }
        data.forgetVillage(key);
    }

    private static void schedule(MinecraftServer server, WarData data, WarData.VillageRecord village) {
        ServerLevel level = server.getLevel(village.dimension);
        if (level == null || data.raidOf(village.key()) != null) {
            return;
        }
        long day = level.getDayTime() / 24000L;
        long time = level.getDayTime() % 24000L;
        if (village.nextRaidDay <= 0) {
            village.nextRaidDay = day + WarTuning.FIRST_RAID_DAYS + level.random.nextInt(2);
            data.setDirty();
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || village.owner == null) {
            return;
        }
        boolean due = day > village.nextRaidDay || day == village.nextRaidDay && time >= WarTuning.RAID_TIME;
        if (!due) {
            if (day == village.nextRaidDay && time >= WarTuning.WARNING_TIME && !village.warned) {
                Kingdom kingdom = pickKingdom(data, village, level.random);
                village.warned = true;
                village.nextKingdom = kingdom == null ? -1 : kingdom.ordinal();
                data.setDirty();
                if (kingdom != null) {
                    EdithAlert.send(owner(server, village.owner), Component.translatable("raid.flightsuit.scouts_title"),
                            Component.translatable("raid.flightsuit.scouts", kingdom.displayName()), village.dimension, village.hall,
                            EdithAlert.AMBER, true);
                }
            }
            return;
        }
        Kingdom kingdom = village.nextKingdom >= 0 ? Kingdom.byId(village.nextKingdom) : pickKingdom(data, village, level.random);
        if (village.population < WarTuning.MIN_POPULATION || kingdom == null) {
            village.nextRaidDay = day + 2;
            village.warned = false;
            village.nextKingdom = -1;
            data.setDirty();
            return;
        }
        startRaid(server, data, village, kingdom);
    }

    /** A kingdom the owner isn't friends with (trust under 30, DESIGN 외교: 우호 이상은 습격하지 않음). */
    private static @Nullable Kingdom pickKingdom(WarData data, WarData.VillageRecord village, RandomSource random) {
        List<Kingdom> hostile = new ArrayList<>();
        for (Kingdom kingdom : Kingdom.values()) {
            if (village.owner == null || data.trust(village.owner, kingdom) < 30) {
                hostile.add(kingdom);
            }
        }
        return hostile.isEmpty() ? null : hostile.get(random.nextInt(hostile.size()));
    }

    // ---------------------------------------------------------------- starting

    public static RaidState startRaid(MinecraftServer server, WarData data, WarData.VillageRecord village, Kingdom kingdom) {
        ServerLevel level = Objects.requireNonNull(server.getLevel(village.dimension));
        RandomSource random = level.random;
        RaidState raid = data.newRaid(village, kingdom);
        float roll = random.nextFloat();
        // Some armies fight to the last man, most break somewhere in the middle, some run at the first blood.
        raid.resolve = roll < 0.25F ? 0.85F + random.nextFloat() * 0.15F
                : roll < 0.7F ? 0.45F + random.nextFloat() * 0.25F
                : 0.15F + random.nextFloat() * 0.2F;
        // Only a general who is at home can lead it (one beaten lately, serving the player or away elsewhere can't).
        long today = level.getDayTime() / 24000L;
        List<General> generals = new ArrayList<>();
        for (General general : kingdom.generals()) {
            if (data.generalState(general, today) == WarData.GENERAL_HOME) {
                generals.add(general);
            }
        }
        raid.general = generals.isEmpty() ? null : generals.get(random.nextInt(generals.size()));
        if (raid.general != null) {
            raid.resolve = Math.min(1.0F, raid.resolve + raid.general.resolveBonus());
            data.setGeneralState(raid.general, WarData.GENERAL_AWAY, 0L);
        }
        raid.fireAttack = random.nextFloat() < WarTuning.FIRE_RAID_CHANCE;
        // The army comes from the direction of its fortress.
        FortRecord fort = data.fort(kingdom);
        raid.approach = fort != null && village.dimension == Level.OVERWORLD
                ? (float) Math.toDegrees(Math.atan2(fort.z - village.hall.getZ(), fort.x - village.hall.getX()))
                : random.nextFloat() * 360.0F;
        raid.startedAt = level.getGameTime();
        raid.waveSize = Math.min(WarTuning.WAVE_MAX, WarTuning.WAVE_BASE + village.population / 2);
        raid.planned = raid.waveSize * 2 + raid.waveSize / 2 + (raid.general != null ? 1 : 0);
        village.warned = false;
        village.nextKingdom = -1;
        data.setDirty();
        keepLoaded(level, raid);
        EdithAlert.send(owner(server, raid.owner), Component.translatable("raid.flightsuit.start_title").withStyle(ChatFormatting.RED),
                Component.translatable(raid.fireAttack ? "raid.flightsuit.start_fire" : "raid.flightsuit.start", kingdom.displayName()),
                raid.dimension, raid.hall, EdithAlert.RED, true);
        return raid;
    }

    private static void keepLoaded(ServerLevel level, RaidState raid) {
        ChunkPos chunk = new ChunkPos(raid.hall);
        level.getChunkSource().addRegionTicket(RAID_TICKET, chunk, TICKET_DISTANCE, chunk);
    }

    // ---------------------------------------------------------------- the raid

    private static void tickRaid(MinecraftServer server, WarData data, RaidState raid) {
        ServerLevel level = server.getLevel(raid.dimension);
        if (level == null) {
            data.endRaid(raid);
            return;
        }
        keepLoaded(level, raid);
        long now = level.getGameTime();
        VillageHallBlockEntity hall = level.isLoaded(raid.hall) && level.getBlockEntity(raid.hall) instanceof VillageHallBlockEntity found
                ? found : null;
        switch (raid.phase) {
            case STARTING -> {
                if (hall == null) {
                    if (level.isLoaded(raid.hall) && now - raid.startedAt > 100L) {
                        // Loaded, but the hall is gone: the village no longer exists.
                        data.endRaid(raid);
                        data.forgetVillage(raid.villageKey);
                    } else if (now - raid.startedAt > 600L) {
                        finish(server, data, raid, null, null);
                    }
                    return;
                }
                raid.phase = RaidState.Phase.ACTIVE;
                spawnWave(level, raid, hall);
                data.setDirty();
            }
            case ACTIVE -> tickActive(server, data, level, raid, hall, now);
            case SURRENDERED -> {
                if (now - raid.surrenderedAt > WarTuning.SURRENDER_DECISION_TICKS) {
                    release(server, raid.id, null);
                }
            }
        }
    }

    private static void tickActive(MinecraftServer server, WarData data, ServerLevel level, RaidState raid,
                                   @Nullable VillageHallBlockEntity hall, long now) {
        List<Mob> members = members(level, raid);
        int fighting = 0;
        for (Mob member : members) {
            if (!RaidMember.isNoThreat(member)) {
                fighting++;
            }
        }
        if (hall != null && fighting > 0 && now % 100 == 0) {
            hall.raiseAlarm(nearestTo(members, raid.hall), raid.kingdom.displayName());
        }
        // Next wave: when the current one is mostly down, or after a while regardless.
        if (raid.wave < 3 && hall != null) {
            int threshold = Math.max(1, Math.round(raid.lastWave * WarTuning.WAVE_REMAINING));
            if (fighting <= threshold || now - raid.waveAt > WarTuning.WAVE_TIMEOUT) {
                spawnWave(level, raid, hall);
                data.setDirty();
                return;
            }
        }
        int defeated = raid.spawned - fighting;
        float breaking = raid.resolve - (raid.generalDefeated ? 0.3F : 0.0F);
        if (fighting > 0 && (raid.duelLost || raid.wave >= 2 && raid.planned > 0 && defeated >= breaking * raid.planned)) {
            surrender(server, data, level, raid, hall, members);
            return;
        }
        if (!raid.wavering && raid.wave >= 2 && raid.planned > 0 && defeated >= (breaking - 0.15F) * raid.planned && fighting > 0) {
            raid.wavering = true;
            data.setDirty();
            ServerPlayer owner = owner(server, raid.owner);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("raid.flightsuit.wavering", raid.kingdom.displayName())
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (raid.wave >= 3 && fighting == 0) {
            victory(server, data, level, raid, hall);
            return;
        }
        if (now - raid.startedAt > WarTuning.RAID_MAX_TICKS) {
            retreat(server, data, level, raid, hall, members);
        }
    }

    private static List<Mob> members(ServerLevel level, RaidState raid) {
        AABB box = new AABB(raid.hall).inflate(128.0D, 64.0D, 128.0D);
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob instanceof RaidMember member && member.raidId() == raid.id);
    }

    private static BlockPos nearestTo(List<Mob> mobs, BlockPos center) {
        Mob best = null;
        for (Mob mob : mobs) {
            if (!RaidMember.isNoThreat(mob) && (best == null || mob.blockPosition().distSqr(center) < best.blockPosition().distSqr(center))) {
                best = mob;
            }
        }
        return best == null ? center : best.blockPosition();
    }

    /** Wave 1: swords and spears. Wave 2: with archers (burning arrows in a fire attack). Wave 3: the general. */
    private static void spawnWave(ServerLevel level, RaidState raid, VillageHallBlockEntity hall) {
        raid.wave++;
        raid.waveAt = level.getGameTime();
        RandomSource random = level.random;
        int count = raid.wave == 3 ? Math.max(1, raid.waveSize / 2) : raid.waveSize;
        BlockPos base = formUpSpot(level, raid);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            KingdomSoldierEntity.Type type;
            if (raid.wave == 2 && i % 2 == 0) {
                type = KingdomSoldierEntity.Type.ARCHER;
            } else {
                type = random.nextFloat() < 0.6F ? KingdomSoldierEntity.Type.SWORD : KingdomSoldierEntity.Type.SPEAR;
            }
            KingdomSoldierEntity soldier = KingdomSoldierEntity.create(level, raid.kingdom, type, raid.id, raid.hall,
                    raid.fireAttack && type == KingdomSoldierEntity.Type.ARCHER);
            if (place(level, soldier, base, random)) {
                spawned++;
            }
        }
        Component announce;
        if (raid.wave == 3 && raid.general != null) {
            GeneralEntity general = GeneralEntity.create(level, raid.general, raid.id, raid.hall);
            if (place(level, general, base, random)) {
                spawned++;
                general.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.0F);
                for (Player player : level.getEntitiesOfClass(Player.class, general.getBoundingBox().inflate(96.0D))) {
                    player.sendSystemMessage(raid.general.line("arrive"));
                }
            }
            announce = Component.translatable("raid.flightsuit.wave3", raid.general.displayName());
        } else if (raid.wave == 2) {
            announce = Component.translatable(raid.fireAttack ? "raid.flightsuit.wave2_fire" : "raid.flightsuit.wave2");
        } else {
            announce = Component.translatable("raid.flightsuit.wave1", raid.kingdom.displayName());
        }
        raid.spawned += spawned;
        raid.lastWave = spawned;
        level.playSound(null, base, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.6F);
        hall.raiseAlarm(base, raid.kingdom.displayName());
        hall.addNews(announce);
        ServerPlayer owner = owner(level.getServer(), raid.owner);
        if (owner != null) {
            if (raid.wave == 1) {
                owner.sendSystemMessage(announce.copy().withStyle(ChatFormatting.RED));
            } else {
                EdithAlert.send(owner, Component.translatable("raid.flightsuit.wave_title", raid.wave), announce, raid.dimension, base,
                        EdithAlert.RED, true);
            }
        }
    }

    /** Where the army forms up: out along its approach, on the ground, in a loaded chunk. */
    private static BlockPos formUpSpot(ServerLevel level, RaidState raid) {
        for (double scale : new double[]{1.0D, 0.75D, 0.5D}) {
            double angle = Math.toRadians(raid.approach + (raid.wave - 1) * 20.0D);
            int x = raid.hall.getX() + Mth.floor(Math.cos(angle) * WarTuning.SPAWN_DISTANCE * scale);
            int z = raid.hall.getZ() + Mth.floor(Math.sin(angle) * WarTuning.SPAWN_DISTANCE * scale);
            if (level.hasChunk(x >> 4, z >> 4)) {
                return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            }
        }
        return raid.hall.above();
    }

    private static boolean place(ServerLevel level, Mob mob, BlockPos base, RandomSource random) {
        int x = base.getX() + random.nextInt(9) - 4;
        int z = base.getZ() + random.nextInt(9) - 4;
        if (!level.hasChunk(x >> 4, z >> 4)) {
            x = base.getX();
            z = base.getZ();
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        mob.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        return level.addFreshEntity(mob);
    }

    // ---------------------------------------------------------------- endings

    /** A general went down (GeneralEntity.die): the army's spirit takes the blow; a lost duel breaks it. */
    public static void onGeneralDefeated(ServerLevel level, GeneralEntity general, boolean duel) {
        WarData data = WarData.get(level.getServer());
        RaidState raid = data.raids().get(general.raidId());
        if (raid == null) {
            return;
        }
        raid.generalDefeated = true;
        raid.duelLost |= duel;
        data.setDirty();
    }

    /** Is this general leading a raid right now? */
    public static boolean isOnRaid(MinecraftServer server, General general) {
        for (RaidState raid : WarData.get(server).raids().values()) {
            if (raid.general == general) {
                return true;
            }
        }
        return false;
    }

    /** Is this player Guan Yu's to fight (his soldiers stand aside)? */
    public static boolean isDueling(int raidId, Player player, Mob raider) {
        if (raidId < 0) {
            return false;
        }
        return !raider.level().getEntitiesOfClass(GeneralEntity.class, raider.getBoundingBox().inflate(48.0D),
                general -> general.raidId() == raidId && player.getUUID().equals(general.getDuelWith())).isEmpty();
    }

    private static void surrender(MinecraftServer server, WarData data, ServerLevel level, RaidState raid,
                                  @Nullable VillageHallBlockEntity hall, List<Mob> members) {
        raid.phase = RaidState.Phase.SURRENDERED;
        raid.surrenderedAt = level.getGameTime();
        data.setDirty();
        int soldiers = 0;
        int generals = 0;
        for (Mob member : members) {
            if (member instanceof KingdomSoldierEntity soldier) {
                soldier.yieldNow();
                soldiers++;
            } else if (member instanceof GeneralEntity general) {
                general.yieldNow();
                generals++;
            }
        }
        level.playSound(null, raid.hall, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.0F, 1.4F);
        if (hall != null) {
            hall.clearAlarm();
            hall.addNews(Component.translatable("news.flightsuit.raid_surrendered", raid.kingdom.displayName()));
        }
        ServerPlayer owner = owner(server, raid.owner);
        if (owner == null) {
            return;
        }
        Component who = raid.general != null && generals > 0
                ? Component.translatable("raid.flightsuit.surrender_with_general", raid.general.displayName(), soldiers)
                : Component.translatable("raid.flightsuit.surrender_soldiers", soldiers);
        EdithAlert.send(owner, Component.translatable("raid.flightsuit.surrender_title").withStyle(ChatFormatting.GOLD),
                Component.translatable("raid.flightsuit.surrender", raid.kingdom.displayName(), who), raid.dimension, raid.hall,
                EdithAlert.AMBER, true);
        owner.sendSystemMessage(Component.translatable("raid.flightsuit.resolve_was", Component.translatable(raid.resolveKey())));
        owner.sendSystemMessage(Component.empty()
                .append(button("raid.flightsuit.button_recruit", "/village recruit " + raid.id, "raid.flightsuit.button_recruit_hint", ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(button("raid.flightsuit.button_release", "/village release " + raid.id, "raid.flightsuit.button_release_hint", ChatFormatting.YELLOW)));
    }

    private static MutableComponent button(String key, String command, String hint, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hint))));
    }

    /** "/village recruit": the soldiers join the village as residents (soldier job), generals as its defenders. */
    public static boolean recruit(MinecraftServer server, int raidId, ServerPlayer player) {
        WarData data = WarData.get(server);
        RaidState raid = data.raids().get(raidId);
        if (raid == null || raid.phase != RaidState.Phase.SURRENDERED || !player.getUUID().equals(raid.owner)) {
            player.sendSystemMessage(Component.translatable("raid.flightsuit.no_surrender").withStyle(ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = server.getLevel(raid.dimension);
        VillageHallBlockEntity hall = level != null && level.getBlockEntity(raid.hall) instanceof VillageHallBlockEntity found ? found : null;
        if (level == null || hall == null) {
            player.sendSystemMessage(Component.translatable("raid.flightsuit.no_hall").withStyle(ChatFormatting.GRAY));
            return false;
        }
        int soldiers = 0;
        List<Component> generals = new ArrayList<>();
        for (Mob member : members(level, raid)) {
            if (member instanceof KingdomSoldierEntity soldier && soldier.hasYielded()) {
                ResidentEntity.spawnRecruit(level, hall, soldier.position(), ResidentJob.SOLDIER);
                soldier.discard();
                soldiers++;
            } else if (member instanceof GeneralEntity general && general.hasYielded()) {
                general.recruit(hall);
                generals.add(general.getGeneral().displayName());
                data.setGeneralState(general.getGeneral(), WarData.GENERAL_RECRUITED, 0L);
            }
        }
        data.addTrust(player.getUUID(), raid.kingdom, -5);
        hall.addNews(Component.translatable("news.flightsuit.raid_recruited", soldiers + generals.size()));
        player.sendSystemMessage(Component.translatable("raid.flightsuit.recruited", soldiers).withStyle(ChatFormatting.GREEN));
        for (Component general : generals) {
            player.sendSystemMessage(Component.translatable("raid.flightsuit.general_joined", general).withStyle(ChatFormatting.GOLD));
        }
        hall.refreshStats();
        finish(server, data, raid, level, hall);
        return true;
    }

    /** "/village release" (or no answer in time): the prisoners go home - and their kingdom remembers it. */
    public static boolean release(MinecraftServer server, int raidId, @Nullable ServerPlayer player) {
        WarData data = WarData.get(server);
        RaidState raid = data.raids().get(raidId);
        if (raid == null || raid.phase != RaidState.Phase.SURRENDERED || player != null && !player.getUUID().equals(raid.owner)) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("raid.flightsuit.no_surrender").withStyle(ChatFormatting.GRAY));
            }
            return false;
        }
        ServerLevel level = server.getLevel(raid.dimension);
        if (level != null) {
            for (Mob member : members(level, raid)) {
                vanish(level, member);
            }
        }
        if (raid.owner != null) {
            data.addTrust(raid.owner, raid.kingdom, 10);
        }
        ServerPlayer owner = owner(server, raid.owner);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("raid.flightsuit.released", raid.kingdom.displayName()).withStyle(ChatFormatting.YELLOW));
        }
        VillageHallBlockEntity hall = level != null && level.getBlockEntity(raid.hall) instanceof VillageHallBlockEntity found ? found : null;
        if (hall != null) {
            hall.addNews(Component.translatable("news.flightsuit.raid_released", raid.kingdom.displayName()));
        }
        finish(server, data, raid, level, hall);
        return true;
    }

    private static void victory(MinecraftServer server, WarData data, ServerLevel level, RaidState raid, @Nullable VillageHallBlockEntity hall) {
        if (hall != null) {
            RandomSource random = level.random;
            List<ItemStack> spoils = List.of(new ItemStack(Items.EMERALD, 2 + random.nextInt(4)),
                    new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(5)), new ItemStack(Items.BREAD, 4 + random.nextInt(6)),
                    new ItemStack(Items.ARROW, 8 + random.nextInt(12)));
            for (ItemStack stack : spoils) {
                ItemStack rest = hall.store(stack);
                if (!rest.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, raid.hall.getX() + 0.5D, raid.hall.getY() + 1.0D, raid.hall.getZ() + 0.5D, rest);
                }
            }
            hall.clearAlarm();
            hall.addNews(Component.translatable("news.flightsuit.raid_won", raid.kingdom.displayName()));
        }
        EdithAlert.send(owner(server, raid.owner), Component.translatable("raid.flightsuit.won_title").withStyle(ChatFormatting.GREEN),
                Component.translatable("raid.flightsuit.won", raid.kingdom.displayName()), raid.dimension, raid.hall, EdithAlert.CYAN, true);
        finish(server, data, raid, level, hall);
    }

    /** Ran too long: the army pulls back, carrying off some of the village storage. */
    private static void retreat(MinecraftServer server, WarData data, ServerLevel level, RaidState raid,
                                @Nullable VillageHallBlockEntity hall, List<Mob> members) {
        for (Mob member : members) {
            vanish(level, member);
        }
        int looted = 0;
        if (hall != null) {
            SimpleContainer storage = hall.getStorage();
            RandomSource random = level.random;
            for (int tries = 0; tries < 12 && looted < 4; tries++) {
                ItemStack stack = storage.getItem(random.nextInt(storage.getContainerSize()));
                if (!stack.isEmpty()) {
                    stack.shrink(Math.max(1, stack.getCount() / 2));
                    looted++;
                }
            }
            storage.setChanged();
            hall.clearAlarm();
            hall.addNews(Component.translatable("news.flightsuit.raid_retreated", raid.kingdom.displayName()));
        }
        EdithAlert.send(owner(server, raid.owner), Component.translatable("raid.flightsuit.retreat_title"),
                Component.translatable("raid.flightsuit.retreat", raid.kingdom.displayName(), looted), raid.dimension, raid.hall,
                EdithAlert.AMBER, true);
        finish(server, data, raid, level, hall);
    }

    /** "/flightsuit raid stop": calls the raid off and clears the field (testing). */
    public static void stop(MinecraftServer server, RaidState raid) {
        WarData data = WarData.get(server);
        ServerLevel level = server.getLevel(raid.dimension);
        VillageHallBlockEntity hall = null;
        if (level != null) {
            for (Mob member : members(level, raid)) {
                vanish(level, member);
            }
            hall = level.getBlockEntity(raid.hall) instanceof VillageHallBlockEntity found ? found : null;
            if (hall != null) {
                hall.clearAlarm();
            }
        }
        finish(server, data, raid, level, hall);
    }

    private static void finish(MinecraftServer server, WarData data, RaidState raid, @Nullable ServerLevel level,
                               @Nullable VillageHallBlockEntity hall) {
        data.endRaid(raid);
        if (raid.general != null) {
            long today = (level != null ? level.getDayTime() : server.overworld().getDayTime()) / 24000L;
            if (data.generalState(raid.general, today) == WarData.GENERAL_AWAY) {
                // Beaten (and let go, or still kneeling when it ended): resting a few days. Otherwise just home.
                data.setGeneralState(raid.general, raid.generalDefeated ? WarData.GENERAL_BEATEN : WarData.GENERAL_HOME,
                        today + WarTuning.GENERAL_REST_DAYS);
            }
        }
        WarData.VillageRecord village = data.villages().get(raid.villageKey);
        if (village != null) {
            long day = (level != null ? level.getDayTime() : server.overworld().getDayTime()) / 24000L;
            RandomSource random = level != null ? level.random : server.overworld().random;
            village.nextRaidDay = day + WarTuning.RAID_INTERVAL + random.nextInt(WarTuning.RAID_INTERVAL_SPREAD + 1);
            village.warned = false;
            village.nextKingdom = -1;
            data.setDirty();
        }
        if (hall != null) {
            hall.refreshStats();
        }
    }

    private static void vanish(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0D, mob.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
        mob.discard();
    }

    private static @Nullable ServerPlayer owner(MinecraftServer server, @Nullable UUID owner) {
        return owner == null ? null : server.getPlayerList().getPlayer(owner);
    }
}
