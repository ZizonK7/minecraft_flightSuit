package com.pfkfks.flightsuit.village;

import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.war.RaidManager;
import com.pfkfks.flightsuit.war.RaidMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The village hall (DESIGN.md 4-12 "본거지"): the heart of one village. It holds the village storage (food,
 * building materials), the architects' work (VillageWorks: repairs, ordered buildings, its own expansion),
 * the alarm, the village stage (on the block state: 1 개척 캠프, 2 마을, 3 기술 도시) and the numbers written on
 * its board - the board is the village's only "UI" (user decision: no HUD for this).
 *
 * Once a morning it hands each resident a meal from the storage and may draw a wanderer in, if there is a
 * free bed for them.
 */
public class VillageHallBlockEntity extends BlockEntity implements MenuProvider {
    private @Nullable UUID owner;
    private String ownerName = "";
    private final SimpleContainer storage = new SimpleContainer(54) {
        @Override
        public void setChanged() {
            super.setChanged();
            VillageHallBlockEntity.this.setChanged();
        }
    };
    private final VillageWorks works = new VillageWorks(this);
    private final FireWatch fireWatch = new FireWatch();
    private final List<Component> news = new ArrayList<>();
    private final List<Long> deathDays = new ArrayList<>();
    private long alarmUntil;
    private BlockPos alarmAt = BlockPos.ZERO;
    /** What set the alarm off (a monster's name), for the board. */
    private Component alarmCause = Component.empty();
    private long lastMorning = -1L;
    private long placedAt = -1L;
    private boolean firstWandererDone;

    // What the board shows: computed on the server, sent to clients.
    private int population;
    /** Of the population, how many are children (M11), and the education figure (-1 = nothing to say). */
    private int children;
    private int education = -1;
    private final int[] jobCounts = new int[ResidentJob.values().length];
    private int food;
    private int beds;
    private int freeBeds;
    /** -1 = nobody to ask. */
    private int happiness = -1;
    private int safety = 100;
    private boolean alarm;
    private int repairs;
    private List<ItemStack> missing = List.of();
    /** The building at the front of the queue (-1 = none), how far along, and how many wait behind it. */
    private int orderType = -1;
    private boolean orderRebuild;
    private int orderPlaced;
    private int orderTotal;
    private int queueSize;
    /** A farmer had bare farmland and no seed anywhere (shown on the board for a minute). */
    private long noSeedsAt = -10000L;
    private boolean noSeeds;
    private final int[] buildingCounts = new int[Blueprint.values().length];
    /** The first suggestion: its building (blueprint ordinal, -1 = none) and its reason. */
    private int proposalType = -1;
    private String proposalReason = "";
    private int proposalCount;
    private int lastSentHash;

    public VillageHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_HALL.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            Villages.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Villages.remove(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            Villages.remove(this);
        }
    }

    // ---- ownership / area / stage ----

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        syncToClients();
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - worldPosition.getX()) <= VillageTuning.RADIUS
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= VillageTuning.RADIUS
                && Math.abs(pos.getY() - worldPosition.getY()) <= VillageTuning.HEIGHT;
    }

    public AABB area() {
        return new AABB(worldPosition).inflate(VillageTuning.RADIUS, VillageTuning.HEIGHT, VillageTuning.RADIUS);
    }

    public Vec3 center() {
        return Vec3.atBottomCenterOf(worldPosition);
    }

    public int getStage() {
        return getBlockState().getValue(VillageHallBlock.STAGE);
    }

    /** The hall expansion is done: the next stage's buildings open up. */
    void stageUp() {
        if (level == null || getStage() >= 3) {
            return;
        }
        int stage = getStage() + 1;
        level.setBlock(worldPosition, getBlockState().setValue(VillageHallBlock.STAGE, stage), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 0.8F);
        Component name = Component.translatable("stage.flightsuit." + stage);
        addNews(Component.translatable("news.flightsuit.stage_up", name));
        tellOwner(Component.translatable("message.flightsuit.stage_up", name).withStyle(ChatFormatting.GOLD));
        refreshStats();
    }

    public VillageWorks works() {
        return works;
    }

    private @Nullable ServerPlayer onlineOwner() {
        if (owner == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    public void tellOwner(Component message) {
        ServerPlayer player = onlineOwner();
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /** Builders build for free while the owner is in creative (testing). */
    boolean ownerBuildsFree() {
        ServerPlayer player = onlineOwner();
        return player != null && player.getAbilities().instabuild;
    }

    // ---- residents ----

    /** Everyone tied to this hall that is loaded: residents and the wanderer(s) on their way in. */
    public List<ResidentEntity> people() {
        if (level == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(ResidentEntity.class, area().inflate(16.0D), r -> worldPosition.equals(r.getHallPos()));
    }

    public List<ResidentEntity> residents() {
        return people().stream().filter(r -> !r.isWanderer()).toList();
    }

    public Set<String> takenNames() {
        Set<String> names = new HashSet<>();
        for (ResidentEntity person : people()) {
            names.add(person.getName().getString());
        }
        return names;
    }

    /** Beds (their head block) inside the village. */
    public List<BlockPos> beds() {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        return server.getPoiManager().findAll(type -> type.is(PoiTypes.HOME), this::contains, worldPosition,
                VillageTuning.RADIUS * 3 / 2, PoiManager.Occupancy.ANY).toList();
    }

    /**
     * A bed no other resident has claimed (after the M13 test, houses have owners): back in their own house if it
     * has a free bed; a child next to where they are (their parents' house - they're born by a parent's bed);
     * a grown-up in an empty house first, so each household gets one of its own; else the free bed closest to
     * the hall. The house's owner is settled right after (VillageWorks.assignOwners).
     */
    public @Nullable BlockPos claimBed(ResidentEntity resident) {
        Set<BlockPos> taken = new HashSet<>();
        Set<Construction> occupied = new HashSet<>();
        for (ResidentEntity other : residents()) {
            if (other != resident && other.getHomeBed() != null) {
                taken.add(other.getHomeBed());
                Construction house = works.buildingAt(other.getHomeBed());
                if (house != null) {
                    occupied.add(house);
                }
            }
        }
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos bed : beds()) {
            if (!taken.contains(bed)) {
                free.add(bed);
            }
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos bed : free) {
            Construction house = works.buildingAt(bed);
            double score;
            if (house != null && resident.getUUID().equals(house.owner())) {
                score = 0.0D;
            } else if (resident.isBaby()) {
                score = 1.0D + bed.distSqr(resident.blockPosition());
            } else if (house != null && house.blueprint().isLodging() && !occupied.contains(house)) {
                score = 1.0E6D + bed.distSqr(worldPosition);
            } else {
                score = 1.0E8D + bed.distSqr(worldPosition);
            }
            if (score < bestScore) {
                best = bed;
                bestScore = score;
            }
        }
        return best;
    }

    public void announceArrival(ResidentEntity wanderer) {
        addNews(Component.translatable("news.flightsuit.wanderer", wanderer.getName()));
        tellOwner(Component.translatable("message.flightsuit.wanderer_arrived", wanderer.getName()).withStyle(ChatFormatting.YELLOW));
    }

    public void announceJoined(ResidentEntity resident) {
        addNews(Component.translatable("news.flightsuit.joined", resident.getName()));
    }

    /** A resident went down; the alarm only goes up if an enemy did it (not the owner's own fist). */
    public void announceDowned(ResidentEntity resident, @Nullable Entity attacker) {
        addNews(Component.translatable("news.flightsuit.downed", resident.getName()));
        tellOwner(Component.translatable("message.flightsuit.resident_downed", resident.getName(),
                VillageTuning.DOWNED_TICKS / 20).withStyle(ChatFormatting.RED));
        if (attacker instanceof Enemy) {
            raiseAlarm(resident.blockPosition(), attacker.getName());
        }
    }

    public void announceTreated(ResidentEntity doctor, ResidentEntity patient) {
        addNews(Component.translatable("news.flightsuit.treated", doctor.getName(), patient.getName()));
    }

    public void announceRevived(ResidentEntity resident) {
        addNews(Component.translatable("news.flightsuit.revived", resident.getName()));
    }

    public void announceDeath(ResidentEntity resident) {
        works.forgetOwner(resident.getUUID());
        if (level != null) {
            deathDays.add(level.getDayTime() / 24000L);
        }
        addNews(Component.translatable("news.flightsuit.died", resident.getName()));
        tellOwner(Component.translatable("message.flightsuit.resident_died", resident.getName()).withStyle(ChatFormatting.DARK_RED));
        refreshStats();
    }

    void announceBuilt(Blueprint blueprint) {
        Component name = Component.translatable(blueprint.translationKey());
        addNews(Component.translatable("news.flightsuit.build_done", name));
        tellOwner(Component.translatable("message.flightsuit.build_done", name).withStyle(ChatFormatting.GREEN));
    }

    private int recentDeaths() {
        if (level == null) {
            return 0;
        }
        long today = level.getDayTime() / 24000L;
        deathDays.removeIf(day -> today - day >= VillageTuning.DEATH_MEMORY_DAYS);
        return deathDays.size();
    }

    public @Nullable ResidentEntity spawnWanderer() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        ResidentEntity wanderer = ResidentEntity.spawnWanderer(server, this, arrivalSpot(server.random));
        firstWandererDone = true;
        refreshStats();
        return wanderer;
    }

    private boolean wandererOnTheWay() {
        for (ResidentEntity person : people()) {
            if (person.isWanderer() && !person.isLeaving()) {
                return true;
            }
        }
        return false;
    }

    /** Where a wanderer walks in from: the edge of the village, on the ground. */
    private BlockPos arrivalSpot(RandomSource random) {
        for (int i = 0; i < 16; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float dist = 24.0F + random.nextFloat() * 16.0F;
            int x = worldPosition.getX() + Mth.floor(Mth.cos(angle) * dist);
            int z = worldPosition.getZ() + Mth.floor(Mth.sin(angle) * dist);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (contains(pos) && level.getFluidState(pos.below()).isEmpty()) {
                return pos;
            }
        }
        BlockPos front = worldPosition.relative(getBlockState().getValue(VillageHallBlock.FACING), 2);
        return new BlockPos(front.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front.getX(), front.getZ()), front.getZ());
    }

    // ---- families (M11) ----

    /**
     * The day's lessons: with a school standing and a teacher on their feet, each teacher takes up to
     * VillageTuning.PUPILS_PER_TEACHER children; each child gets the stars of the best teacher (DESIGN 4-12:
     * 선생님 → 아이 교육 → 커서 가질 재능).
     */
    private void schoolDay() {
        if (works.schools() == 0) {
            return;
        }
        int bestTeacher = 0;
        int teachers = 0;
        List<ResidentEntity> pupils = new ArrayList<>();
        for (ResidentEntity resident : residents()) {
            if (resident.isBaby()) {
                pupils.add(resident);
            } else if (resident.getJob() == ResidentJob.TEACHER && !resident.isDowned()) {
                teachers++;
                bestTeacher = Math.max(bestTeacher, resident.talent(ResidentJob.TEACHER));
            }
        }
        int seats = teachers * VillageTuning.PUPILS_PER_TEACHER;
        for (int i = 0; i < pupils.size() && i < seats; i++) {
            pupils.get(i).attendSchool(bestTeacher);
        }
    }

    /**
     * Maybe a baby this morning (DESIGN 4-12 번식): two grown-ups in a good mood, a free bed, food to spare and
     * not too many children already. Happier villages have more; a doctor helps (출산 돌봄).
     */
    private void tryBirth() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        List<ResidentEntity> adults = new ArrayList<>();
        int kids = 0;
        boolean doctor = false;
        for (ResidentEntity resident : residents()) {
            if (resident.isBaby()) {
                kids++;
            } else if (!resident.isDowned()) {
                adults.add(resident);
                doctor |= resident.getJob() == ResidentJob.DOCTOR;
            }
        }
        if (adults.size() < 2 || freeBeds <= 0 || kids >= Math.max(1, (int) (adults.size() * VillageTuning.CHILDREN_PER_ADULT))
                || countFood() < (adults.size() + kids) * 2 || happiness < VillageTuning.BIRTH_MOOD) {
            return;
        }
        float chance = VillageTuning.BIRTH_BASE_CHANCE + (happiness - VillageTuning.BIRTH_MOOD) / 300.0F
                + (doctor ? VillageTuning.BIRTH_DOCTOR_BONUS : 0.0F);
        if (server.random.nextFloat() >= chance) {
            return;
        }
        adults.sort((a, b) -> Integer.compare(b.getMood(), a.getMood()));
        ResidentEntity first = adults.get(0);
        ResidentEntity second = adults.get(1);
        String parents = first.getName().getString() + " · " + second.getName().getString();
        BlockPos at = first.getHomeBed() != null ? first.getHomeBed() : first.blockPosition();
        ResidentEntity child = ResidentEntity.spawnChild(server, this, Vec3.atBottomCenterOf(at).add(0.0D, 0.6D, 0.0D), parents);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, child.getX(), child.getY() + 0.8D, child.getZ(),
                6, 0.4D, 0.4D, 0.4D, 0.0D);
        addNews(Component.translatable("news.flightsuit.born", child.getName(), first.getName(), second.getName()));
        tellOwner(Component.translatable("message.flightsuit.born", first.getName(), second.getName(), child.getName(),
                VillageTuning.CHILDHOOD_DAYS).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    void announceGrownUp(ResidentEntity resident, int lessons) {
        ResidentJob best = ResidentJob.FARMER;
        for (ResidentJob job : ResidentJob.values()) {
            if (job != ResidentJob.NONE && resident.talent(job) > resident.talent(best)) {
                best = job;
            }
        }
        Component gift = Component.translatable(best.translationKey()).copy().append(" ★" + resident.talent(best));
        addNews(Component.translatable("news.flightsuit.grown_up", resident.getName()));
        tellOwner(Component.translatable("message.flightsuit.grown_up", resident.getName(), lessons, gift).withStyle(ChatFormatting.LIGHT_PURPLE));
        refreshStats();
    }

    // ---- food ----

    /** One meal out of the storage: real food first, rotten flesh and the like only when nothing else is left. */
    private boolean takeMeal() {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < storage.getContainerSize(); i++) {
                ItemStack stack = storage.getItem(i);
                FoodProperties props = stack.getFoodProperties(null);
                if (props != null && (pass == 1 || props.getEffects().isEmpty())) {
                    stack.shrink(1);
                    storage.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The merchants' day at the market (after the M13 test - "what does the merchant do?"): each sells some of
     * the food beyond what the village eats in four days, and brings back what the builders are short of (the
     * first missing material), or emeralds if they lack nothing. More stars, more sold and a better price.
     */
    /** Who played for the village last night (MusicianGoal); the concert counts at the next morning. Not saved. */
    private @Nullable UUID concertBy;

    public void noteConcert(ResidentEntity musician) {
        if (concertBy == null || !concertBy.equals(musician.getUUID())
                && musician.talent(ResidentJob.MUSICIAN) > talentOf(concertBy)) {
            concertBy = musician.getUUID();
        }
    }

    private int talentOf(UUID id) {
        ResidentEntity resident = residentById(id);
        return resident == null ? 0 : resident.talent(ResidentJob.MUSICIAN);
    }

    private @Nullable ResidentEntity residentById(UUID id) {
        for (ResidentEntity resident : residents()) {
            if (resident.getUUID().equals(id)) {
                return resident;
            }
        }
        return null;
    }

    private void marketDay() {
        for (ResidentEntity merchant : residents()) {
            if (merchant.getJob() != ResidentJob.MERCHANT || merchant.isBaby() || merchant.isDowned()) {
                continue;
            }
            int stars = merchant.talent(ResidentJob.MERCHANT);
            int surplus = countFood() - getPopulation() * VillageTuning.MARKET_KEEP_DAYS;
            int sell = Math.min(surplus, VillageTuning.MARKET_BASE + VillageTuning.MARKET_PER_STAR * stars);
            if (sell <= 0) {
                merchant.setLastWork(Component.translatable("activity.flightsuit.no_surplus"));
                continue;
            }
            int sold = 0;
            for (int i = 0; i < sell && takeMeal(); i++) {
                sold++;
            }
            List<ItemStack> lacking = works.missing();
            ItemStack bought;
            if (!lacking.isEmpty() && lacking.get(0).getItem() != net.minecraft.world.item.Items.AIR) {
                int amount = Math.max(1, Math.min(lacking.get(0).getCount(), sold * (2 + stars) / 6));
                bought = new ItemStack(lacking.get(0).getItem(), amount);
            } else {
                bought = new ItemStack(net.minecraft.world.item.Items.EMERALD, Math.max(1, sold * (2 + stars) / 16));
            }
            Component line = Component.translatable("news.flightsuit.market", merchant.getName(), sold, bought.getHoverName(), bought.getCount());
            ItemStack left = store(bought);
            if (!left.isEmpty()) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, left));
            }
            merchant.setLastWork(Component.translatable("activity.flightsuit.traded", sold, bought.getHoverName(), bought.getCount()));
            addNews(line);
        }
    }

    private int countFood() {
        int count = 0;
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (stack.getFoodProperties(null) != null) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public SimpleContainer getStorage() {
        return storage;
    }

    /** Puts what it can into the storage; returns the leftovers. */
    public ItemStack store(ItemStack stack) {
        return storage.addItem(stack);
    }

    public @Nullable ItemStack takeAny(Predicate<ItemStack> filter) {
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                ItemStack one = stack.split(1);
                storage.setChanged();
                return one;
            }
        }
        return null;
    }

    // ---- alarm ----

    public boolean isAlarm() {
        return level != null && level.getGameTime() < alarmUntil;
    }

    /**
     * An enemy was seen or a resident hit: everyone to shelter, the nearest bell rings. It clears by itself
     * VillageTuning.ALARM_TICKS after the last sighting, or when the owner calls it off at the hall.
     */
    public void raiseAlarm(@Nullable BlockPos near, Component cause) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasOn = isAlarm();
        alarmUntil = level.getGameTime() + VillageTuning.ALARM_TICKS;
        alarmAt = near != null ? near.immutable() : worldPosition;
        alarmCause = cause;
        if (!wasOn) {
            ringBell(near != null ? near : worldPosition);
            addNews(Component.translatable("news.flightsuit.alarm"));
            tellOwner(Component.translatable("message.flightsuit.village_alarm").withStyle(ChatFormatting.RED));
            ServerPlayer owner = onlineOwner();
            // Away from the village (or off in another dimension): EDITH flashes it up too (DESIGN.md 4-15).
            if (owner != null && EdithAlert.isAway(owner, level.dimension(), worldPosition, VillageTuning.RADIUS + 16)) {
                EdithAlert.send(owner, Component.translatable("edith.flightsuit.alarm_title"), Component.translatable("edith.flightsuit.alarm",
                        cause), level.dimension(), alarmAt, EdithAlert.RED, false);
            }
            refreshStats();
        }
    }

    /** Sneak + right-click on the hall: the owner calls the alarm off. */
    public boolean clearAlarm() {
        if (!isAlarm()) {
            return false;
        }
        alarmUntil = 0L;
        addNews(Component.translatable("news.flightsuit.alarm_off"));
        refreshStats();
        return true;
    }

    /** Where the enemy was last reported (soldiers run there). */
    public BlockPos alarmPos() {
        return alarmAt;
    }

    /** A farmer found bare farmland but no seed in their bag, the storage or the grass around. */
    public void noteNoSeeds() {
        if (level != null) {
            noSeedsAt = level.getGameTime();
        }
    }

    private void ringBell(BlockPos near) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos bell = server.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING), near,
                VillageTuning.BELL_REACH, PoiManager.Occupancy.ANY).orElse(null);
        if (bell != null && level.getBlockState(bell).getBlock() instanceof BellBlock bellBlock) {
            bellBlock.attemptToRing(level, bell, null);
        } else {
            level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
        }
    }

    /**
     * With a watchtower standing and a guard on duty, nothing gets into the village unseen: any monster
     * on the surface inside it raises the alarm, whether or not a guard has it in sight. Monsters down in
     * caves under the village don't count (they kept the alarm up for good).
     */
    private void keepWatch() {
        if (works.count(Blueprint.WATCHTOWER) == 0 || jobCounts[ResidentJob.GUARD.ordinal()] == 0) {
            return;
        }
        List<Monster> intruders = level.getEntitiesOfClass(Monster.class, area(), monster -> monster.isAlive()
                && !RaidMember.isNoThreat(monster) && contains(monster.blockPosition()) && isOnSurface(monster.blockPosition()));
        if (!intruders.isEmpty()) {
            raiseAlarm(intruders.get(0).blockPosition(), intruders.get(0).getName());
        }
    }

    private boolean isOnSurface(BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 3;
    }

    // ---- fight damage (VillageWorks keeps the ledger) ----

    /** Fire set inside the village (a burning arrow): watched so what burns goes on the ledger, and guards put it out. */
    public void watchFire(BlockPos pos) {
        fireWatch.watch(pos, level == null ? 0L : level.getGameTime());
    }

    public FireWatch fireWatch() {
        return fireWatch;
    }

    public void recordDamage(BlockPos pos, BlockState state) {
        works.recordDamage(pos, state);
    }

    // ---- news ----

    public void addNews(Component line) {
        news.add(0, line);
        while (news.size() > VillageTuning.NEWS_LINES) {
            news.remove(news.size() - 1);
        }
        syncToClients();
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, VillageHallBlockEntity hall) {
        long now = level.getGameTime();
        if (hall.placedAt < 0) {
            hall.placedAt = now;
        }
        if (now % 20 != 0) {
            return;
        }
        if (hall.alarm != hall.isAlarm()) {
            hall.refreshStats();
        }
        if (now % 40 == 0) {
            hall.keepWatch();
        }
        hall.morning();
        if (!hall.firstWandererDone && now - hall.placedAt >= VillageTuning.FIRST_WANDERER_DELAY) {
            if (hall.population > 0) {
                hall.firstWandererDone = true;
            } else if (hall.freeBeds > 0 && !hall.wandererOnTheWay()) {
                hall.spawnWanderer();
            }
        }
        hall.fireWatch.tick(level, hall);
        if (now % 100 == 0) {
            hall.refreshStats();
            RaidManager.noteVillage(hall);
        }
    }

    /** Once a day, after sunrise: a meal for everyone, the day's mood, and maybe a wanderer. */
    private void morning() {
        long day = level.getDayTime() / 24000L;
        long time = level.getDayTime() % 24000L;
        if (day == lastMorning || time < 1000L || time >= 12000L) {
            return;
        }
        lastMorning = day;
        refreshStats();
        int deaths = recentDeaths();
        // The jobs' day (VillageWorkday): the cook first, so the bread is on the table for breakfast.
        int bonus = VillageWorkday.cook(this) ? VillageTuning.WARM_MEAL_MOOD : 0;
        ResidentEntity musician = concertBy == null ? null : residentById(concertBy);
        int concert = VillageWorkday.concertBonus(this, musician);
        if (concert > 0) {
            addNews(Component.translatable("news.flightsuit.concert", musician.getName()));
            musician.setLastWork(Component.translatable("activity.flightsuit.played", concert));
        }
        concertBy = null;
        int smithStars = VillageWorkday.smith(this);
        VillageWorkday.ranch(this);
        for (ResidentEntity resident : residents()) {
            resident.morning(takeMeal(), deaths, safety, bonus + concert);
            resident.setSmithBonus(smithStars);
        }
        schoolDay();
        marketDay();
        tryBirth();
        refreshStats();
        float chance = VillageTuning.WANDERER_BASE_CHANCE + Math.max(0, happiness) / 200.0F;
        if (freeBeds > 0 && !wandererOnTheWay() && level.random.nextFloat() < chance) {
            spawnWanderer();
        }
        setChanged();
    }

    /** Recounts what the board shows and sends it out if anything changed. */
    public void refreshStats() {
        if (level == null || level.isClientSide) {
            return;
        }
        // Houses, farms and workplaces to their owners (after the M13 test).
        works.assignOwners(residents());
        List<ResidentEntity> people = people();
        Arrays.fill(jobCounts, 0);
        int moodSum = 0;
        int guards = 0;
        int wanderers = 0;
        population = 0;
        children = 0;
        for (ResidentEntity person : people) {
            if (person.isWanderer()) {
                wanderers++;
                continue;
            }
            population++;
            if (person.isBaby()) {
                children++;
            } else {
                jobCounts[person.getJob().ordinal()]++;
            }
            moodSum += person.getMood();
            if (person.getJob() == ResidentJob.GUARD) {
                guards++;
            }
        }
        food = countFood();
        beds = beds().size();
        freeBeds = Math.max(0, beds - population - wanderers);
        happiness = population == 0 ? -1 : moodSum / population;
        int guarded = population == 0 ? 100 : Math.min(100, guards * VillageTuning.RESIDENTS_PER_GUARD * 100 / population);
        safety = Mth.clamp(guarded - 20 * recentDeaths(), 0, 100);
        int teachers = jobCounts[ResidentJob.TEACHER.ordinal()];
        if (children == 0) {
            education = teachers > 0 && works.schools() > 0 ? 100 : -1;
        } else {
            education = works.schools() == 0 ? 0
                    : Math.min(100, teachers * VillageTuning.PUPILS_PER_TEACHER * 100 / children);
        }
        alarm = isAlarm();

        works.checkFinished();
        // Also prunes repairs already done, and notes what's missing even with no builder around.
        works.nextTask(center(), null);
        missing = works.missing();
        repairs = works.repairCount();
        List<Construction> queue = works.queue();
        Construction front = queue.isEmpty() ? null : queue.get(0);
        orderType = front != null ? front.blueprint().ordinal() : -1;
        orderRebuild = front != null && front.replaces() != null;
        orderPlaced = front != null ? front.placedCount(level) : 0;
        orderTotal = front != null ? front.solidCount() : 0;
        queueSize = queue.size();
        noSeeds = level.getGameTime() - noSeedsAt < 1200L;
        Arrays.fill(buildingCounts, 0);
        for (Construction building : works.buildings()) {
            buildingCounts[building.blueprint().ordinal()]++;
        }
        List<VillageWorks.Proposal> proposals = works.proposals();
        proposalCount = proposals.size();
        proposalType = proposals.isEmpty() ? -1 : proposals.get(0).blueprint().ordinal();
        proposalReason = proposals.isEmpty() ? "" : proposals.get(0).reason();

        int hash = clientTag().hashCode();
        if (hash != lastSentHash) {
            lastSentHash = hash;
            syncToClients();
        }
    }

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- board data (client side reads these) ----

    public String getOwnerName() {
        return ownerName;
    }

    public int getPopulation() {
        return population;
    }

    public int getChildren() {
        return children;
    }

    /** 0..100, or -1 when there's nobody to teach and no teacher. */
    public int getEducation() {
        return education;
    }

    public int getJobCount(ResidentJob job) {
        return jobCounts[job.ordinal()];
    }

    public int getFood() {
        return food;
    }

    public int getBeds() {
        return beds;
    }

    public int getFreeBeds() {
        return freeBeds;
    }

    public int getHappiness() {
        return happiness;
    }

    public int getSafety() {
        return safety;
    }

    public boolean isAlarmShown() {
        return alarm;
    }

    public Component getAlarmCause() {
        return alarmCause;
    }

    public int getRepairs() {
        return repairs;
    }

    public List<ItemStack> getMissing() {
        return missing;
    }

    /** The building at the front of the queue, or null. */
    public @Nullable Blueprint getOrder() {
        return orderType >= 0 && orderType < Blueprint.values().length ? Blueprint.values()[orderType] : null;
    }

    public boolean isOrderRebuild() {
        return orderRebuild;
    }

    public int getOrderPlaced() {
        return orderPlaced;
    }

    public int getOrderTotal() {
        return orderTotal;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public boolean isNoSeeds() {
        return noSeeds;
    }

    public int getBuildingCount(Blueprint blueprint) {
        return buildingCounts[blueprint.ordinal()];
    }

    /** -1 = no suggestion, else the suggested building's blueprint ordinal. */
    public int getProposalType() {
        return proposalType;
    }

    public String getProposalReason() {
        return proposalReason;
    }

    public int getProposalCount() {
        return proposalCount;
    }

    public List<Component> getNews() {
        return news;
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.flightsuit.village_storage");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.sixRows(id, inventory, storage);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The board stands above the block.
        return new AABB(worldPosition.offset(-2, 0, -2), worldPosition.offset(3, 4, 3));
    }

    // ---- saving ----

    private CompoundTag clientTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("OwnerName", ownerName);
        ListTag newsList = new ListTag();
        for (Component line : news) {
            newsList.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        tag.put("News", newsList);
        CompoundTag stats = new CompoundTag();
        stats.putInt("Population", population);
        stats.putInt("Children", children);
        stats.putInt("Education", education);
        stats.putIntArray("Jobs", jobCounts);
        stats.putInt("Food", food);
        stats.putInt("Beds", beds);
        stats.putInt("FreeBeds", freeBeds);
        stats.putInt("Happiness", happiness);
        stats.putInt("Safety", safety);
        stats.putBoolean("Alarm", alarm);
        stats.putString("AlarmCause", Component.Serializer.toJson(alarmCause));
        stats.putInt("Repairs", repairs);
        ListTag missingList = new ListTag();
        for (ItemStack stack : missing) {
            CompoundTag item = new CompoundTag();
            stack.save(item);
            item.putInt("Need", stack.getCount());
            missingList.add(item);
        }
        stats.put("Missing", missingList);
        stats.putInt("Order", orderType);
        stats.putBoolean("OrderRebuild", orderRebuild);
        stats.putInt("OrderPlaced", orderPlaced);
        stats.putInt("OrderTotal", orderTotal);
        stats.putInt("QueueSize", queueSize);
        stats.putBoolean("NoSeeds", noSeeds);
        stats.putIntArray("Buildings", buildingCounts);
        stats.putInt("Proposal", proposalType);
        stats.putString("ProposalReason", proposalReason);
        stats.putInt("ProposalCount", proposalCount);
        tag.put("Stats", stats);
        return tag;
    }

    private void readClientTag(CompoundTag tag) {
        if (tag.contains("OwnerName")) {
            ownerName = tag.getString("OwnerName");
        }
        if (tag.contains("News")) {
            news.clear();
            for (Tag raw : tag.getList("News", Tag.TAG_STRING)) {
                Component line = Component.Serializer.fromJson(raw.getAsString());
                if (line != null) {
                    news.add(line);
                }
            }
        }
        if (tag.contains("Stats")) {
            CompoundTag stats = tag.getCompound("Stats");
            population = stats.getInt("Population");
            children = stats.getInt("Children");
            education = stats.contains("Education") ? stats.getInt("Education") : -1;
            copyInto(stats.getIntArray("Jobs"), jobCounts);
            food = stats.getInt("Food");
            beds = stats.getInt("Beds");
            freeBeds = stats.getInt("FreeBeds");
            happiness = stats.getInt("Happiness");
            safety = stats.getInt("Safety");
            alarm = stats.getBoolean("Alarm");
            Component cause = stats.contains("AlarmCause") ? Component.Serializer.fromJson(stats.getString("AlarmCause")) : null;
            alarmCause = cause != null ? cause : Component.empty();
            repairs = stats.getInt("Repairs");
            List<ItemStack> list = new ArrayList<>();
            for (Tag raw : stats.getList("Missing", Tag.TAG_COMPOUND)) {
                CompoundTag item = (CompoundTag) raw;
                ItemStack stack = ItemStack.of(item);
                stack.setCount(Math.max(1, item.getInt("Need")));
                list.add(stack);
            }
            missing = list;
            orderType = stats.contains("Order") ? stats.getInt("Order") : -1;
            orderRebuild = stats.getBoolean("OrderRebuild");
            orderPlaced = stats.getInt("OrderPlaced");
            orderTotal = stats.getInt("OrderTotal");
            queueSize = stats.getInt("QueueSize");
            noSeeds = stats.getBoolean("NoSeeds");
            copyInto(stats.getIntArray("Buildings"), buildingCounts);
            proposalType = stats.contains("Proposal") ? stats.getInt("Proposal") : -1;
            proposalReason = stats.getString("ProposalReason");
            proposalCount = stats.getInt("ProposalCount");
        }
    }

    private static void copyInto(int[] from, int[] to) {
        Arrays.fill(to, 0);
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.merge(clientTag());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        NonNullList<ItemStack> items = NonNullList.withSize(storage.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < storage.getContainerSize(); i++) {
            items.set(i, storage.getItem(i));
        }
        tag.put("Storage", ContainerHelper.saveAllItems(new CompoundTag(), items));
        tag.put("Works", works.save());
        tag.putLongArray("DeathDays", deathDays);
        tag.putLong("AlarmUntil", alarmUntil);
        tag.putLong("LastMorning", lastMorning);
        tag.putBoolean("FirstWanderer", firstWandererDone);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readClientTag(tag);
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
        }
        if (tag.contains("Storage")) {
            NonNullList<ItemStack> items = NonNullList.withSize(storage.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Storage"), items);
            for (int i = 0; i < items.size(); i++) {
                storage.setItem(i, items.get(i));
            }
        }
        if (tag.contains("Works")) {
            works.load(tag.getCompound("Works"));
        }
        if (tag.contains("DeathDays")) {
            deathDays.clear();
            for (long day : tag.getLongArray("DeathDays")) {
                deathDays.add(day);
            }
        }
        if (tag.contains("LastMorning")) {
            alarmUntil = tag.getLong("AlarmUntil");
            lastMorning = tag.getLong("LastMorning");
            firstWandererDone = tag.getBoolean("FirstWanderer");
            // A reloaded village doesn't wait for "the first wanderer" again.
            placedAt = 0L;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return clientTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
