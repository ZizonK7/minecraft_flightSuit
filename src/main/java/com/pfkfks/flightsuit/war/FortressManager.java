package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The kingdoms' fortresses (DESIGN.md 4-11, M12): where they stand (picked once per world, far out from spawn,
 * one per kingdom at thirds of the compass), building each the first time a player comes near, keeping the
 * garrison and generals in place while someone is around, being found, falling, and the battles fought there.
 *
 * "Where are the generals" is world state (WarData.generalState): a general away on a raid or serving the
 * player is not at home, so nobody exists twice. Garrisons aren't saved; they're put back each visit.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class FortressManager {
    private static final int BUILD_PER_TICK = 3000;
    private static final TicketType<ChunkPos> BATTLE_TICKET =
            TicketType.create("flightsuit_battle", Comparator.comparingLong(ChunkPos::toLong), 100);

    private static final class BuildJob {
        final FortressBuilder plan;
        int index;

        BuildJob(FortressBuilder plan) {
            this.plan = plan;
        }
    }

    private record Surrender(UUID player, long at) {
    }

    private static final Map<Kingdom, BuildJob> JOBS = new EnumMap<>(Kingdom.class);
    private static final Map<Kingdom, Long> TICKING_SINCE = new EnumMap<>(Kingdom.class);
    private static final Map<Kingdom, Integer> LOSSES = new EnumMap<>(Kingdom.class);
    private static final Map<Kingdom, Long> LAST_LOSS = new EnumMap<>(Kingdom.class);
    private static final Map<Kingdom, Surrender> SURRENDERS = new EnumMap<>(Kingdom.class);

    private FortressManager() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        JOBS.clear();
        TICKING_SINCE.clear();
        LOSSES.clear();
        LAST_LOSS.clear();
        SURRENDERS.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        tickBuilds(level);
        if (level.getGameTime() % 20 == 0) {
            WarData data = WarData.get(level.getServer());
            ensureSites(level, data);
            tickForts(level, data);
            tickBattles(level, data);
            tickSurrenders(level, data);
        }
    }

    // ---------------------------------------------------------------- sites

    private static void ensureSites(ServerLevel level, WarData data) {
        if (data.forts().size() == Kingdom.values().length) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x5DEECE66DL);
        double base = random.nextDouble() * Math.PI * 2.0D;
        for (Kingdom kingdom : Kingdom.values()) {
            if (data.fort(kingdom) != null) {
                continue;
            }
            FortRecord fort = data.newFort(kingdom);
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = base + kingdom.ordinal() * (Math.PI * 2.0D / 3.0D)
                        + Math.toRadians(15.0D * ((attempt + 1) / 2)) * (attempt % 2 == 0 ? 1 : -1);
                int distance = WarTuning.FORT_DISTANCE + (attempt * 37) % WarTuning.FORT_DISTANCE_SPREAD;
                fort.x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
                fort.z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
                Holder<Biome> biome = level.getBiome(new BlockPos(fort.x, level.getSeaLevel(), fort.z));
                if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)) {
                    break;
                }
            }
            fort.resolve = 0.35F + random.nextFloat() * 0.55F;
            data.setDirty();
        }
    }

    // ---------------------------------------------------------------- building

    private static void startBuild(ServerLevel level, WarData data, FortRecord fort) {
        int[] heights = new int[9];
        int i = 0;
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                heights[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fort.x + dx, fort.z + dz) - 1;
            }
        }
        Arrays.sort(heights);
        fort.y = Math.max(heights[4], level.getSeaLevel());
        data.setDirty();
        JOBS.put(fort.kingdom, new BuildJob(FortressBuilder.plan(fort.kingdom, new BlockPos(fort.x, fort.y, fort.z))));
    }

    private static void tickBuilds(ServerLevel level) {
        if (JOBS.isEmpty()) {
            return;
        }
        WarData data = null;
        for (Kingdom kingdom : new ArrayList<>(JOBS.keySet())) {
            BuildJob job = JOBS.get(kingdom);
            List<FortressBuilder.Placement> placements = job.plan.placements();
            int end = Math.min(placements.size(), job.index + BUILD_PER_TICK);
            for (; job.index < end; job.index++) {
                FortressBuilder.Placement placement = placements.get(job.index);
                if (placement.fillOnly()) {
                    BlockState current = level.getBlockState(placement.pos());
                    if (!current.isAir() && !current.canBeReplaced() && current.getFluidState().isEmpty()) {
                        continue;
                    }
                }
                level.setBlock(placement.pos(), placement.state(), Block.UPDATE_CLIENTS);
            }
            if (job.index >= placements.size()) {
                stockChests(level, kingdom, job.plan.chests());
                JOBS.remove(kingdom);
                if (data == null) {
                    data = WarData.get(level.getServer());
                }
                FortRecord fort = data.fort(kingdom);
                if (fort != null) {
                    fort.built = true;
                    data.setDirty();
                }
            }
        }
    }

    private static void stockChests(ServerLevel level, Kingdom kingdom, List<BlockPos> chests) {
        RandomSource random = level.random;
        for (BlockPos pos : chests) {
            if (!(level.getBlockEntity(pos) instanceof Container chest)) {
                continue;
            }
            List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 4 + random.nextInt(5)),
                    new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(4)), new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(7)),
                    new ItemStack(Items.BREAD, 8 + random.nextInt(9)), new ItemStack(Items.ARROW, 16 + random.nextInt(17))));
            loot.add(switch (kingdom) {
                case SHU -> new ItemStack(Items.GOLDEN_APPLE);
                case WEI -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(2));
                case WU -> new ItemStack(Items.TRIDENT);
            });
            int slot = 0;
            for (ItemStack stack : loot) {
                if (slot < chest.getContainerSize()) {
                    chest.setItem(slot, stack);
                    slot += 1 + random.nextInt(3);
                }
            }
            chest.setChanged();
        }
    }

    // ---------------------------------------------------------------- the fortress while someone's around

    private static void tickForts(ServerLevel level, WarData data) {
        long day = level.getDayTime() / 24000L;
        for (FortRecord fort : data.forts().values()) {
            boolean anyoneNear = false;
            for (ServerPlayer player : level.players()) {
                double dx = player.getX() - fort.x;
                double dz = player.getZ() - fort.z;
                double distSqr = dx * dx + dz * dz;
                if (distSqr < 112.0D * 112.0D && !player.isSpectator()) {
                    Diplomacy.discover(player, data, fort);
                }
                if (distSqr < 144.0D * 144.0D) {
                    anyoneNear = true;
                }
            }
            if (!fort.built) {
                if (anyoneNear && !JOBS.containsKey(fort.kingdom)) {
                    startBuild(level, data, fort);
                }
                continue;
            }
            if (anyoneNear || data.battleAt(fort.kingdom) != null) {
                maintain(level, data, fort, day);
            } else {
                TICKING_SINCE.remove(fort.kingdom);
            }
        }
    }

    private static AABB area(FortRecord fort) {
        return new AABB(fort.center()).inflate(FortressBuilder.CLEAR + 16, 24.0D, FortressBuilder.CLEAR + 16);
    }

    /** Puts the garrison and the generals at home back in place (they aren't saved), and clears out those who left. */
    private static void maintain(ServerLevel level, WarData data, FortRecord fort, long day) {
        BlockPos center = fort.center();
        if (!level.isPositionEntityTicking(center)) {
            TICKING_SINCE.remove(fort.kingdom);
            return;
        }
        long now = level.getGameTime();
        long since = TICKING_SINCE.computeIfAbsent(fort.kingdom, k -> now);
        // Give the chunk's saved entities (raiders, prisoners) a moment to load before counting.
        if (now - since < 100L || now % 100L != 0L) {
            return;
        }
        if (now - LAST_LOSS.getOrDefault(fort.kingdom, now) > 20L * 60 * 5) {
            LOSSES.remove(fort.kingdom);
            LAST_LOSS.remove(fort.kingdom);
        }
        AABB box = area(fort);
        List<KingdomSoldierEntity> garrison = level.getEntitiesOfClass(KingdomSoldierEntity.class, box,
                soldier -> soldier.role() == WarRole.GARRISON && soldier.getKingdom() == fort.kingdom);
        int target = fort.isFallen(day) ? WarTuning.FALLEN_GARRISON : WarTuning.GARRISON_SIZE;
        if (SURRENDERS.containsKey(fort.kingdom)) {
            target = 0;
        }
        RandomSource random = level.random;
        for (int i = garrison.size(); i < target; i++) {
            KingdomSoldierEntity.Type type = random.nextFloat() < 0.3F ? KingdomSoldierEntity.Type.ARCHER
                    : random.nextBoolean() ? KingdomSoldierEntity.Type.SWORD : KingdomSoldierEntity.Type.SPEAR;
            KingdomSoldierEntity soldier = KingdomSoldierEntity.create(level, fort.kingdom, type, -1, null, false).asGarrison(center);
            BlockPos spot = courtyardSpot(level, center, random);
            soldier.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(soldier);
        }
        List<GeneralEntity> present = level.getEntitiesOfClass(GeneralEntity.class, box,
                general -> general.role() == WarRole.GARRISON && general.kingdom() == fort.kingdom);
        for (General general : General.values()) {
            if (general.kingdom() != fort.kingdom) {
                continue;
            }
            boolean home = !fort.isFallen(day) && data.generalState(general, day) == WarData.GENERAL_HOME
                    && !SURRENDERS.containsKey(fort.kingdom);
            List<GeneralEntity> mine = new ArrayList<>();
            for (GeneralEntity entity : present) {
                if (entity.getGeneral() == general) {
                    mine.add(entity);
                }
            }
            if (!home) {
                for (GeneralEntity entity : mine) {
                    if (!entity.hasYielded()) {
                        vanish(level, entity);
                    }
                }
                continue;
            }
            for (int i = 1; i < mine.size(); i++) {
                mine.get(i).discard();
            }
            if (mine.isEmpty()) {
                GeneralEntity entity = GeneralEntity.create(level, general, -1, null).asGarrison(center);
                BlockPos spot = general.isLeader() ? FortressBuilder.throne(center)
                        : FortressBuilder.palaceSteps(center).offset(random.nextInt(9) - 4, 0, random.nextInt(3));
                entity.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 180.0F, 0.0F);
                level.addFreshEntity(entity);
            }
        }
    }

    /** Open ground in the courtyard (between the palace steps and the storehouse, clear of the barracks). */
    private static BlockPos courtyardSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int tries = 0; tries < 12; tries++) {
            BlockPos pos = center.offset(random.nextInt(17) - 8, 1, random.nextInt(15) - 4);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }
        return center.offset(0, 1, 2);
    }

    // ---------------------------------------------------------------- losses and the fall

    /** A garrison soldier fell to the player's side. */
    public static void onGarrisonFell(ServerLevel level, Kingdom kingdom, boolean byPlayerSide) {
        if (byPlayerSide) {
            addLoss(level, kingdom, 1, false);
        }
    }

    /** A garrison general was beaten (GeneralEntity.die): away for a few days; the ruler's fall ends the fight. */
    public static void onGeneralBeaten(ServerLevel level, GeneralEntity general, boolean byPlayerSide) {
        WarData data = WarData.get(level.getServer());
        long day = level.getDayTime() / 24000L;
        data.setGeneralState(general.getGeneral(), WarData.GENERAL_BEATEN, day + WarTuning.GENERAL_REST_DAYS);
        if (byPlayerSide) {
            addLoss(level, general.kingdom(), 3, general.getGeneral().isLeader());
        }
    }

    private static void addLoss(ServerLevel level, Kingdom kingdom, int amount, boolean leaderDown) {
        WarData data = WarData.get(level.getServer());
        FortRecord fort = data.fort(kingdom);
        long day = level.getDayTime() / 24000L;
        if (fort == null || !fort.built || fort.isFallen(day) || SURRENDERS.containsKey(kingdom)) {
            return;
        }
        int losses = LOSSES.merge(kingdom, amount, Integer::sum);
        LAST_LOSS.put(kingdom, level.getGameTime());
        int generals = 0;
        for (General general : General.values()) {
            if (general.kingdom() == kingdom && data.generalState(general, day) != WarData.GENERAL_RECRUITED) {
                generals++;
            }
        }
        int breaking = Math.max(3, Math.round(fort.resolve * (WarTuning.GARRISON_SIZE + 3 * generals)));
        if (leaderDown || losses >= breaking) {
            surrender(level, data, fort);
        }
    }

    /** The fortress gives up: everyone left kneels; it stays fallen for a week; the conqueror decides on the prisoners. */
    private static void surrender(ServerLevel level, WarData data, FortRecord fort) {
        long day = level.getDayTime() / 24000L;
        fort.fallenUntilDay = day + WarTuning.FALLEN_DAYS;
        data.setDirty();
        LOSSES.remove(fort.kingdom);
        int soldiers = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area(fort),
                mob -> mob instanceof RaidMember member && member.role() == WarRole.GARRISON && member.kingdom() == fort.kingdom)) {
            if (mob instanceof KingdomSoldierEntity soldier) {
                soldier.yieldNow();
                soldiers++;
            } else if (mob instanceof GeneralEntity general) {
                general.yieldNow();
            }
        }
        level.playSound(null, fort.center(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0F, 0.7F);
        ServerPlayer conqueror = conqueror(level, data, fort);
        Battle battle = data.battleAt(fort.kingdom);
        if (battle != null && battle.kind == Battle.Kind.INVADE) {
            Diplomacy.onInvasionWon(level, data, battle);
        }
        if (conqueror == null) {
            return;
        }
        SURRENDERS.put(fort.kingdom, new Surrender(conqueror.getUUID(), level.getGameTime()));
        data.lowerTrustTo(conqueror.getUUID(), fort.kingdom, -60);
        EdithAlert.send(conqueror, Component.translatable("fort.flightsuit.fallen_title").withStyle(ChatFormatting.GOLD),
                Component.translatable("fort.flightsuit.fallen", fort.kingdom.displayName(), soldiers), Level.OVERWORLD, fort.center(),
                EdithAlert.AMBER, true);
        conqueror.sendSystemMessage(Component.empty()
                .append(button("raid.flightsuit.button_recruit", "/village fort recruit " + fort.kingdom.id(), "fort.flightsuit.recruit_hint", ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(button("raid.flightsuit.button_release", "/village fort release " + fort.kingdom.id(), "fort.flightsuit.release_hint", ChatFormatting.YELLOW)));
    }

    private static @Nullable ServerPlayer conqueror(ServerLevel level, WarData data, FortRecord fort) {
        Battle battle = data.battleAt(fort.kingdom);
        if (battle != null && battle.player != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(battle.player);
            if (player != null) {
                return player;
            }
        }
        ServerPlayer best = null;
        for (ServerPlayer player : level.players()) {
            double dist = player.distanceToSqr(fort.x, fort.y, fort.z);
            if (dist < 96.0D * 96.0D && (best == null || dist < best.distanceToSqr(fort.x, fort.y, fort.z))) {
                best = player;
            }
        }
        return best;
    }

    private static void tickSurrenders(ServerLevel level, WarData data) {
        for (Kingdom kingdom : new ArrayList<>(SURRENDERS.keySet())) {
            Surrender surrender = SURRENDERS.get(kingdom);
            if (level.getGameTime() - surrender.at() > WarTuning.SURRENDER_DECISION_TICKS) {
                decide(level.getServer(), kingdom, null, false);
            }
        }
    }

    /**
     * "/village fort recruit|release": the soldiers go to the player's village (they turn up there as soldier
     * residents next time it's loaded), generals with them; the ruler always escapes. Releasing them is
     * remembered kindly.
     */
    public static boolean decide(MinecraftServer server, Kingdom kingdom, @Nullable ServerPlayer player, boolean recruit) {
        Surrender surrender = SURRENDERS.get(kingdom);
        if (surrender == null || player != null && !player.getUUID().equals(surrender.player())) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("raid.flightsuit.no_surrender").withStyle(ChatFormatting.GRAY));
            }
            return false;
        }
        SURRENDERS.remove(kingdom);
        WarData data = WarData.get(server);
        ServerLevel level = server.overworld();
        FortRecord fort = data.fort(kingdom);
        long day = level.getDayTime() / 24000L;
        int soldiers = 0;
        List<General> generals = new ArrayList<>();
        if (fort != null) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area(fort), mob -> mob instanceof RaidMember member
                    && member.role() == WarRole.GARRISON && member.kingdom() == kingdom && member.isNoThreat())) {
                if (recruit && mob instanceof KingdomSoldierEntity) {
                    soldiers++;
                } else if (recruit && mob instanceof GeneralEntity general && !general.getGeneral().isLeader()) {
                    generals.add(general.getGeneral());
                    data.setGeneralState(general.getGeneral(), WarData.GENERAL_RECRUITED, 0L);
                } else if (mob instanceof GeneralEntity general) {
                    data.setGeneralState(general.getGeneral(), WarData.GENERAL_BEATEN, day + WarTuning.FALLEN_DAYS);
                }
                vanish(level, mob);
            }
        }
        UUID lord = surrender.player();
        if (recruit) {
            WarData.VillageRecord village = data.homeVillage(lord);
            if (village != null) {
                village.pendingRecruits += soldiers;
                for (General general : generals) {
                    village.pendingGenerals |= 1 << general.ordinal();
                }
                data.setDirty();
            }
            data.addTrust(lord, kingdom, -10);
        } else {
            data.addTrust(lord, kingdom, 15);
        }
        ServerPlayer owner = player != null ? player : server.getPlayerList().getPlayer(lord);
        if (owner != null) {
            owner.sendSystemMessage(recruit
                    ? Component.translatable("fort.flightsuit.recruited", soldiers + generals.size()).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("fort.flightsuit.released", kingdom.displayName()).withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    private static MutableComponent button(String key, String command, String hint, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hint))));
    }

    // ---------------------------------------------------------------- battles at a fortress

    /** A storm on {@code fort}'s fortress by {@code attacker}, or an invasion of it with the player (Diplomacy starts these). */
    public static Battle startBattle(ServerLevel level, WarData data, Battle.Kind kind, Kingdom fort, Kingdom attacker,
                                     @Nullable UUID player, int request) {
        Battle battle = data.newBattle(kind, fort, attacker, player, request);
        battle.startedAt = level.getGameTime();
        battle.waveSize = WarTuning.STORM_WAVE;
        data.setDirty();
        FortRecord record = data.fort(fort);
        if (kind == Battle.Kind.INVADE && record != null && player != null) {
            data.lowerTrustTo(player, fort, -60);
            ServerPlayer leader = level.getServer().getPlayerList().getPlayer(player);
            BlockPos base = approach(level, record, leader != null ? leader.blockPosition() : null, 30);
            RandomSource random = level.random;
            for (int i = 0; i < WarTuning.ALLIED_ARMY; i++) {
                KingdomSoldierEntity soldier = KingdomSoldierEntity.create(level, attacker,
                        i % 3 == 2 ? KingdomSoldierEntity.Type.ARCHER : KingdomSoldierEntity.Type.SWORD, -1, null, false)
                        .asAlly(player, fort, record.center(), level.getGameTime() + WarTuning.BATTLE_MAX_TICKS, battle.id);
                place(level, soldier, base, random);
            }
        }
        return battle;
    }

    private static void tickBattles(ServerLevel level, WarData data) {
        for (Battle battle : new ArrayList<>(data.battles().values())) {
            FortRecord fort = data.fort(battle.fort);
            if (fort == null || !fort.built) {
                data.endBattle(battle);
                continue;
            }
            ChunkPos chunk = new ChunkPos(fort.center());
            level.getChunkSource().addRegionTicket(BATTLE_TICKET, chunk, 6, chunk);
            long now = level.getGameTime();
            if (now - battle.startedAt > WarTuning.BATTLE_MAX_TICKS) {
                endStorm(level, data, battle, false);
                continue;
            }
            if (battle.kind != Battle.Kind.STORM || !level.isPositionEntityTicking(fort.center())) {
                continue;
            }
            List<Mob> attackers = level.getEntitiesOfClass(Mob.class, area(fort), mob -> mob instanceof RaidMember member
                    && member.role() == WarRole.RAID && member.foe() == battle.fort && member.kingdom() == battle.attacker
                    && !member.isNoThreat());
            int fighting = attackers.size();
            if (battle.wave < 3 && (battle.wave == 0 || fighting <= Math.max(1, battle.lastWave / 3) || now - battle.waveAt > WarTuning.WAVE_TIMEOUT)) {
                stormWave(level, data, battle, fort);
            } else if (battle.wave >= 3 && fighting == 0) {
                endStorm(level, data, battle, true);
            }
        }
    }

    private static void stormWave(ServerLevel level, WarData data, Battle battle, FortRecord fort) {
        battle.wave++;
        battle.waveAt = level.getGameTime();
        RandomSource random = level.random;
        FortRecord attackerFort = data.fort(battle.attacker);
        BlockPos from = attackerFort != null ? new BlockPos(attackerFort.x, fort.y, attackerFort.z) : null;
        BlockPos base = approach(level, fort, from, FortressBuilder.WALL + 14);
        int count = battle.wave == 3 ? battle.waveSize / 2 : battle.waveSize;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            KingdomSoldierEntity.Type type = battle.wave == 2 && i % 2 == 0 ? KingdomSoldierEntity.Type.ARCHER
                    : random.nextBoolean() ? KingdomSoldierEntity.Type.SWORD : KingdomSoldierEntity.Type.SPEAR;
            KingdomSoldierEntity soldier = KingdomSoldierEntity.create(level, battle.attacker, type, -1, null, false)
                    .asStorm(battle.fort, fort.center(), battle.id);
            if (place(level, soldier, base, random)) {
                spawned++;
            }
        }
        if (battle.wave == 3) {
            long day = level.getDayTime() / 24000L;
            for (General general : battle.attacker.generals()) {
                if (data.generalState(general, day) == WarData.GENERAL_HOME) {
                    data.setGeneralState(general, WarData.GENERAL_AWAY, 0L);
                    GeneralEntity entity = GeneralEntity.create(level, general, -1, null).asStorm(battle.fort, fort.center());
                    if (place(level, entity, base, random)) {
                        spawned++;
                        for (Player player : level.getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(96.0D))) {
                            player.sendSystemMessage(general.line("arrive"));
                        }
                    }
                    break;
                }
            }
        }
        battle.spawned += spawned;
        battle.lastWave = spawned;
        data.setDirty();
        level.playSound(null, base, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.6F);
        ServerPlayer player = battle.player == null ? null : level.getServer().getPlayerList().getPlayer(battle.player);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.storm_wave", battle.attacker.displayName(), battle.wave)
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** The storm is over (all waves beaten, or it ran out of time); an invasion that timed out ends here too. */
    private static void endStorm(ServerLevel level, WarData data, Battle battle, boolean held) {
        FortRecord fort = data.fort(battle.fort);
        long day = level.getDayTime() / 24000L;
        if (fort != null) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area(fort), mob -> mob instanceof RaidMember member
                    && (member.role() == WarRole.RAID && member.foe() == battle.fort
                    || member.role() == WarRole.ALLY && mob instanceof KingdomSoldierEntity soldier && soldier.battleId() == battle.id))) {
                if (mob instanceof GeneralEntity general) {
                    data.setGeneralState(general.getGeneral(), general.hasYielded() ? WarData.GENERAL_BEATEN : WarData.GENERAL_HOME,
                            day + WarTuning.GENERAL_REST_DAYS);
                }
                vanish(level, mob);
            }
        }
        // A general who marched out and never came back into view goes home.
        for (General general : battle.attacker.generals()) {
            if (data.generalState(general, day) == WarData.GENERAL_AWAY && !RaidManager.isOnRaid(level.getServer(), general)) {
                data.setGeneralState(general, WarData.GENERAL_HOME, 0L);
            }
        }
        data.endBattle(battle);
        Diplomacy.onBattleOver(level, data, battle, held);
    }

    // ---------------------------------------------------------------- helpers

    /** Outside the walls, on the side facing {@code from} (random side if null), on the ground. */
    private static BlockPos approach(ServerLevel level, FortRecord fort, @Nullable BlockPos from, int distance) {
        double angle = from == null ? level.random.nextDouble() * Math.PI * 2.0D : Math.atan2(from.getZ() - fort.z, from.getX() - fort.x);
        int x = fort.x + Mth.floor(Math.cos(angle) * distance);
        int z = fort.z + Mth.floor(Math.sin(angle) * distance);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean place(ServerLevel level, Mob mob, BlockPos base, RandomSource random) {
        int x = base.getX() + random.nextInt(7) - 3;
        int z = base.getZ() + random.nextInt(7) - 3;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        mob.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        return level.addFreshEntity(mob);
    }

    private static void vanish(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0D, mob.getZ(), 10, 0.3D, 0.5D, 0.3D, 0.02D);
        mob.discard();
    }

    /** Was this killed by the player's side (the player, their suits, their soldiers, their allies)? */
    public static boolean byPlayerSide(@Nullable Entity killer) {
        return killer instanceof Player || killer instanceof SuitCompanionEntity || killer instanceof ResidentEntity
                || killer instanceof RaidMember member && member.isPlayerSide();
    }
}
