package com.pfkfks.flightsuit.thief;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.StationStorageBlockEntity;
import com.pfkfks.flightsuit.block.SecuritySensorBlockEntity;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitCapsuleItem;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Batman's crew comes calling (DESIGN.md 4-14, M14). Rarely: never in a world's first days, then every week or
 * two, at night, to one village. If someone is there to see it the crew really comes in (ThiefEntity); if not -
 * the owner away, or asleep so the night skips - the night is worked out instead (DESIGN 4-15: 보이는 곳은
 * 진짜로, 안 보이는 곳은 계산으로): the village's guards and armed sensors may stop them, otherwise a few
 * chests are lighter in the morning, each with a bat mark in it. The village hears about it on its board.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ThiefManager {
    private static final Set<Item> VALUABLES = Set.of(Items.DIAMOND, Items.DIAMOND_BLOCK, Items.EMERALD, Items.EMERALD_BLOCK,
            Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.RAW_GOLD, Items.IRON_INGOT, Items.IRON_BLOCK, Items.NETHERITE_INGOT,
            Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING,
            Items.NETHER_STAR, Items.LAPIS_BLOCK, Items.AMETHYST_SHARD);

    private ThiefManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % 20 != 5) {
            return;
        }
        MinecraftServer server = level.getServer();
        ThiefData data = ThiefData.get(server);
        long time = level.getDayTime();
        long day = time / 24000L;
        long tod = time % 24000L;
        if (data.nextVisitDay < 0) {
            data.nextVisitDay = ThiefTuning.FIRST_DAY + level.random.nextInt(4);
            data.setDirty();
        }
        if (data.visit == null) {
            if (day >= data.nextVisitDay && tod >= ThiefTuning.ARRIVE_FROM && tod < ThiefTuning.ARRIVE_UNTIL) {
                start(server, data, day, null);
            }
        } else {
            tickVisit(server, data, day, tod);
        }
    }

    // ---------------------------------------------------------------- the night

    /** Picks tonight's village (one owned by a player, with people in it) and the crew. */
    public static boolean start(MinecraftServer server, ThiefData data, long day, @Nullable String onlyVillage) {
        List<WarData.VillageRecord> candidates = new ArrayList<>();
        for (WarData.VillageRecord record : WarData.get(server).villages().values()) {
            if (record.owner != null && record.population >= 2 && server.getLevel(record.dimension) != null
                    && (onlyVillage == null || record.key().equals(onlyVillage))) {
                candidates.add(record);
            }
        }
        RandomSource random = server.overworld().random;
        if (candidates.isEmpty()) {
            if (onlyVillage == null) {
                data.nextVisitDay = day + 1;
                data.setDirty();
            }
            return false;
        }
        WarData.VillageRecord record = candidates.get(random.nextInt(candidates.size()));
        int crew = pickCrew(random);
        int planned = 1 + Integer.bitCount(crew) + random.nextInt(2);
        data.visit = new ThiefData.Visit(record.dimension, record.hall, record.key(), day, crew, planned);
        data.nextVisitDay = day + ThiefTuning.MIN_GAP + random.nextInt(ThiefTuning.EXTRA_GAP + 1);
        data.setDirty();
        return true;
    }

    /** Usually one or two of them, sometimes all three. */
    private static int pickCrew(RandomSource random) {
        List<ThiefType> types = new ArrayList<>(List.of(ThiefType.values()));
        Collections.shuffle(types, new java.util.Random(random.nextLong()));
        float roll = random.nextFloat();
        int count = roll < 0.4F ? 1 : roll < 0.8F ? 2 : 3;
        int mask = 0;
        for (int i = 0; i < count; i++) {
            mask |= types.get(i).bit();
        }
        return mask;
    }

    private static void tickVisit(MinecraftServer server, ThiefData data, long day, long tod) {
        ThiefData.Visit visit = data.visit;
        if (visit == null) {
            return;
        }
        ServerLevel level = server.getLevel(visit.dimension);
        boolean night = day == visit.day && tod >= 13000L && tod < ThiefTuning.DAWN;
        if (!night || level == null) {
            end(server, data, level);
            return;
        }
        if (visit.spawned) {
            if (visit.caught + visit.escaped >= visit.out) {
                end(server, data, level);
            }
            return;
        }
        if (!level.isPositionEntityTicking(visit.hall)) {
            return;
        }
        VillageHallBlockEntity hall = Villages.hallAt(level, visit.hall);
        if (hall == null) {
            data.visit = null;
            data.setDirty();
            return;
        }
        spawnCrew(level, data, visit, hall);
    }

    /** Someone is there to see it: the crew really comes in, from the edge of the village, a few chests each. */
    private static void spawnCrew(ServerLevel level, ThiefData data, ThiefData.Visit visit, VillageHallBlockEntity hall) {
        visit.spawned = true;
        data.setDirty();
        boolean batman = (visit.crew & ThiefType.BATMAN.bit()) != 0;
        List<BlockPos> chests = findTargets(level, hall, batman, visit.planned);
        if (chests.isEmpty()) {
            return;
        }
        List<ThiefType> crew = new ArrayList<>();
        for (ThiefType type : ThiefType.values()) {
            if ((visit.crew & type.bit()) != 0) {
                crew.add(type);
            }
        }
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int ex = visit.hall.getX() + Mth.floor(Math.cos(angle) * (VillageTuning.RADIUS - 6));
        int ez = visit.hall.getZ() + Mth.floor(Math.sin(angle) * (VillageTuning.RADIUS - 6));
        for (int i = 0; i < crew.size(); i++) {
            List<BlockPos> mine = new ArrayList<>();
            for (int k = i; k < chests.size(); k += crew.size()) {
                mine.add(chests.get(k));
            }
            ThiefEntity thief = ThiefEntity.create(level, crew.get(i), visit.hall, mine);
            int x = ex + random.nextInt(5) - 2;
            int z = ez + random.nextInt(5) - 2;
            thief.moveTo(x + 0.5D, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (level.addFreshEntity(thief)) {
                visit.out++;
            }
        }
        data.setDirty();
    }

    /**
     * Dawn (or the night skipped, or the whole crew is caught or gone): whoever is still in the village slips away,
     * and whatever wasn't done for real is left to work out - now if the village is loaded, else when it next is.
     */
    private static void end(MinecraftServer server, ThiefData data, @Nullable ServerLevel level) {
        ThiefData.Visit visit = data.visit;
        if (visit == null) {
            return;
        }
        if (level != null) {
            AABB around = new AABB(visit.hall).inflate(VillageTuning.RADIUS + 48, 64.0D, VillageTuning.RADIUS + 48);
            for (ThiefEntity thief : level.getEntitiesOfClass(ThiefEntity.class, around)) {
                if (visit.hall.equals(thief.getHall())) {
                    thief.escape(level);
                }
            }
        }
        data.visit = null;
        int remaining = visit.spotted ? 0 : Math.max(0, visit.planned - visit.done);
        if (remaining > 0 || visit.stolen > 0) {
            ThiefData.Pending pending = data.pending().computeIfAbsent(visit.villageKey, key -> new ThiefData.Pending());
            pending.crew |= visit.crew;
            pending.remaining += remaining;
            pending.stolen += visit.stolen;
            pending.day = visit.day;
        }
        data.setDirty();
        VillageHallBlockEntity hall = level == null ? null : Villages.hallAt(level, visit.hall);
        if (hall != null && level.isPositionEntityTicking(visit.hall)) {
            resolve(level, data, visit.villageKey, hall);
        } else if (remaining > 0) {
            WarData.VillageRecord record = WarData.get(server).villages().get(visit.villageKey);
            ServerPlayer owner = record == null || record.owner == null ? null : server.getPlayerList().getPlayer(record.owner);
            if (owner != null) {
                EdithAlert.send(owner, Component.translatable("thief.flightsuit.edith_title"), Component.translatable("thief.flightsuit.edith_away"),
                        visit.dimension, visit.hall, EdithAlert.AMBER, true);
            }
        }
    }

    /** From RaidManager.noteVillage: a village with a night still to work out finds out what happened. */
    public static void onVillageLoaded(ServerLevel level, String villageKey, VillageHallBlockEntity hall) {
        ThiefData data = ThiefData.get(level.getServer());
        if (data.pending().isEmpty() || !data.pending().containsKey(villageKey)
                || data.visit != null && data.visit.villageKey.equals(villageKey)) {
            return;
        }
        resolve(level, data, villageKey, hall);
    }

    /** The worked-out night: security may stop them; if not, the chests they'd have reached are robbed. */
    private static void resolve(ServerLevel level, ThiefData data, String villageKey, VillageHallBlockEntity hall) {
        ThiefData.Pending pending = data.pending().remove(villageKey);
        data.setDirty();
        if (pending == null) {
            return;
        }
        RandomSource random = level.random;
        int stolen = pending.stolen;
        Component crew = ThiefType.crewName(pending.crew);
        if (pending.remaining > 0) {
            int guards = hall.getJobCount(ResidentJob.GUARD);
            int sensors = armedSensors(level, hall);
            double chance = Math.min(ThiefTuning.CATCH_MAX, guards * ThiefTuning.CATCH_PER_GUARD + sensors * ThiefTuning.CATCH_PER_SENSOR);
            if (random.nextDouble() < chance) {
                ThiefType dropped = someone(pending.crew, random);
                ItemStack reward = reward(dropped);
                Component rewardName = reward.getHoverName();
                ItemStack left = hall.store(reward);
                if (!left.isEmpty()) {
                    BlockPos at = hall.getBlockPos().above();
                    level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, left));
                }
                Component line = Component.translatable("thief.flightsuit.news_stopped", crew, rewardName);
                hall.addNews(line);
                hall.tellOwner(line.copy().withStyle(ChatFormatting.GREEN));
            } else {
                boolean batman = (pending.crew & ThiefType.BATMAN.bit()) != 0;
                for (BlockPos pos : findTargets(level, hall, batman, pending.remaining)) {
                    Container container = containerOf(level.getBlockEntity(pos), true);
                    if (container != null) {
                        stolen += steal(container, someone(pending.crew, random), random, new ArrayList<>());
                    }
                }
            }
        }
        if (stolen > 0) {
            Component line = Component.translatable("thief.flightsuit.news_robbed", crew, stolen);
            hall.addNews(line);
            hall.tellOwner(line.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static ThiefType someone(int crew, RandomSource random) {
        List<ThiefType> types = new ArrayList<>();
        for (ThiefType type : ThiefType.values()) {
            if ((crew & type.bit()) != 0) {
                types.add(type);
            }
        }
        return types.isEmpty() ? ThiefType.BATMAN : types.get(random.nextInt(types.size()));
    }

    // ---------------------------------------------------------------- what happens to the crew

    static void onSpotted(ServerLevel level, ThiefEntity thief) {
        ThiefData.Visit visit = visitOf(level, thief);
        if (visit != null) {
            visit.spotted = true;
            ThiefData.get(level.getServer()).setDirty();
        }
    }

    static void onEscaped(ServerLevel level, ThiefEntity thief) {
        ThiefData.Visit visit = visitOf(level, thief);
        if (visit != null) {
            visit.escaped++;
            ThiefData.get(level.getServer()).setDirty();
        }
    }

    static void onCaught(ServerLevel level, ThiefEntity thief, int recovered) {
        ThiefData.Visit visit = visitOf(level, thief);
        if (visit != null) {
            visit.caught++;
            visit.stolen = Math.max(0, visit.stolen - recovered);
            ThiefData.get(level.getServer()).setDirty();
        }
        VillageHallBlockEntity hall = Villages.hallAt(level, thief.getHall());
        if (hall != null) {
            Component line = Component.translatable("thief.flightsuit.news_caught", thief.getThiefType().displayName());
            hall.addNews(line);
            hall.tellOwner(line.copy().withStyle(ChatFormatting.GREEN));
        }
    }

    private static @Nullable ThiefData.Visit visitOf(ServerLevel level, ThiefEntity thief) {
        ThiefData.Visit visit = ThiefData.get(level.getServer()).visit;
        return visit != null && visit.dimension == level.dimension() && visit.hall.equals(thief.getHall()) ? visit : null;
    }

    /** What each of them drops when beaten (DESIGN 4-14: 갈고리, 배트랭, 연막탄). */
    public static ItemStack reward(ThiefType type) {
        return switch (type) {
            case BATMAN -> new ItemStack(ModItems.BATARANG.get());
            case CATWOMAN -> new ItemStack(ModItems.GRAPPLE.get());
            case ROBIN -> new ItemStack(ModItems.SMOKE_BOMB.get(), 3);
        };
    }

    // ---------------------------------------------------------------- chests

    /** A live thief at a chest: Batman cuts a locked station storage's power first (guards close by may hear it). */
    static void robLive(ServerLevel level, ThiefEntity thief, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StationStorageBlockEntity storage && storage.isLocked()) {
            if (thief.getThiefType() != ThiefType.BATMAN) {
                return;
            }
            thief.emp(level, 3.0D);
            for (ResidentEntity guard : level.getEntitiesOfClass(ResidentEntity.class, thief.getBoundingBox().inflate(ThiefTuning.EMP_HEARD),
                    r -> r.getJob().isFighter() && !r.isDowned() && !r.isSleeping() && !r.isBaby() && !r.isWanderer())) {
                thief.spot(level, guard, Component.translatable("thief.flightsuit.heard_emp", guard.getName()));
                break;
            }
        }
        Container container = containerOf(be, false);
        if (container == null) {
            return;
        }
        List<ItemStack> taken = new ArrayList<>();
        int stacks = steal(container, thief.getThiefType(), level.random, taken);
        for (ItemStack stack : taken) {
            thief.addLoot(stack);
        }
        ThiefData.Visit visit = visitOf(level, thief);
        if (visit != null) {
            visit.done++;
            visit.stolen += stacks;
            ThiefData.get(level.getServer()).setDirty();
        }
    }

    /**
     * Takes half of up to {@code type.stacksPerChest()} stacks - valuables first; Catwoman takes nothing else -
     * and leaves the bat mark in an empty slot. Returns how many stacks were touched.
     */
    static int steal(Container container, ThiefType type, RandomSource random, List<ItemStack> into) {
        List<Integer> valuable = new ArrayList<>();
        List<Integer> other = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || stack.is(ModItems.BAT_MARK.get())) {
                continue;
            }
            (isValuable(stack) ? valuable : other).add(slot);
        }
        java.util.Random shuffle = new java.util.Random(random.nextLong());
        Collections.shuffle(valuable, shuffle);
        Collections.shuffle(other, shuffle);
        List<Integer> order = new ArrayList<>(valuable);
        if (type != ThiefType.CATWOMAN) {
            order.addAll(other);
        }
        int taken = 0;
        for (int slot : order) {
            if (taken >= type.stacksPerChest()) {
                break;
            }
            ItemStack stack = container.getItem(slot);
            ItemStack part = container.removeItem(slot, Math.max(1, (stack.getCount() + 1) / 2));
            if (!part.isEmpty()) {
                into.add(part);
                taken++;
            }
        }
        if (taken > 0) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).isEmpty()) {
                    container.setItem(slot, new ItemStack(ModItems.BAT_MARK.get()));
                    break;
                }
            }
            container.setChanged();
        }
        return taken;
    }

    public static boolean isValuable(ItemStack stack) {
        return VALUABLES.contains(stack.getItem()) || stack.is(ModItems.ARC_REACTOR.get()) || stack.is(ModItems.ENERGY_CELL.get())
                || stack.isEnchanted() || stack.getItem() instanceof SuitCapsuleItem || stack.getItem() instanceof SuitArmorItem;
    }

    /** Chests, barrels and station storage (a locked one only if {@code allowLocked}). */
    static @Nullable Container containerOf(@Nullable BlockEntity be, boolean allowLocked) {
        if (be instanceof StationStorageBlockEntity storage) {
            return storage.isLocked() && !allowLocked ? null : storage.getStorage();
        }
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
            return (Container) be;
        }
        return null;
    }

    /** The most tempting containers in the village (loaded chunks only), best first. */
    static List<BlockPos> findTargets(ServerLevel level, VillageHallBlockEntity hall, boolean allowLocked, int count) {
        record Candidate(BlockPos pos, double score) {
        }
        List<Candidate> candidates = new ArrayList<>();
        BlockPos center = hall.getBlockPos();
        int r = VillageTuning.RADIUS;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!hall.contains(be.getBlockPos())) {
                        continue;
                    }
                    Container container = containerOf(be, allowLocked);
                    if (container == null) {
                        continue;
                    }
                    double score = 0.0D;
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty() && !stack.is(ModItems.BAT_MARK.get())) {
                            score += isValuable(stack) ? 10.0D : 1.0D;
                        }
                    }
                    if (score > 0.0D) {
                        candidates.add(new Candidate(be.getBlockPos().immutable(), score + level.random.nextDouble() * 5.0D));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < candidates.size() && out.size() < count; i++) {
            out.add(candidates.get(i).pos());
        }
        return out;
    }

    private static int armedSensors(ServerLevel level, VillageHallBlockEntity hall) {
        int count = 0;
        BlockPos center = hall.getBlockPos();
        int r = VillageTuning.RADIUS;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SecuritySensorBlockEntity sensor && sensor.isArmed() && hall.contains(be.getBlockPos())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** The only clue to a hidden thief: a chest lid lifting by itself (barrels open too). */
    static void setLid(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
            level.playSound(null, pos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.25F, 1.2F);
        } else if (state.getBlock() instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), 3);
            level.playSound(null, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.25F, 1.2F);
        }
    }

    // ---------------------------------------------------------------- test helpers

    /** "/flightsuit thief now": the crew comes to this village tonight - the clock jumps to nightfall. */
    public static boolean forceTonight(ServerLevel level, VillageHallBlockEntity hall) {
        MinecraftServer server = level.getServer();
        ThiefData data = ThiefData.get(server);
        if (data.visit != null) {
            end(server, data, server.getLevel(data.visit.dimension));
        }
        ServerLevel overworld = server.overworld();
        long day = overworld.getDayTime() / 24000L;
        long tod = overworld.getDayTime() % 24000L;
        if (tod < ThiefTuning.ARRIVE_FROM || tod >= ThiefTuning.DAWN) {
            if (tod >= ThiefTuning.DAWN) {
                day++;
            }
            overworld.setDayTime(day * 24000L + ThiefTuning.ARRIVE_FROM);
        }
        WarData war = WarData.get(server);
        WarData.VillageRecord record = war.village(level.dimension(), hall.getBlockPos());
        record.owner = hall.getOwner();
        record.population = hall.getPopulation();
        war.setDirty();
        long nextBefore = data.nextVisitDay;
        boolean started = start(server, data, day, record.key());
        if (started) {
            data.nextVisitDay = Math.max(nextBefore, day + ThiefTuning.MIN_GAP);
        }
        return started;
    }

    /** "/flightsuit thief spawn <type>": one hidden thief right here, working this village's chests. */
    public static @Nullable ThiefEntity spawnHere(ServerLevel level, VillageHallBlockEntity hall, ThiefType type, BlockPos at) {
        List<BlockPos> targets = findTargets(level, hall, type == ThiefType.BATMAN, 3);
        ThiefEntity thief = ThiefEntity.create(level, type, hall.getBlockPos(), targets);
        thief.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        return level.addFreshEntity(thief) ? thief : null;
    }

    /** For "/flightsuit thief when". */
    public static long nextVisitDay(MinecraftServer server) {
        return ThiefData.get(server).nextVisitDay;
    }
}
