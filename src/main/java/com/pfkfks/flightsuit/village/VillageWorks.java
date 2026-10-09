package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the architects do for one village (DESIGN.md 4-11, 4-12): putting back fight damage (the
 * ledger), the buildings the player ordered (a queue, worked front to back), rebuilds into a later stage's
 * version (개축), and the hall's own building - the next stage's hall building is the hall expansion.
 *
 * Architects never build on their own (user decision: that takes the freedom away) - they suggest (건의),
 * and the player decides what goes up and where, with a blueprint.
 *
 * Up to VillageTuning.CREW_SIZE architects work one building, each on their own quarter of it (helping
 * the others once theirs is done), and no two ever take the same block - so more hands really is faster.
 */
public class VillageWorks {
    public enum Kind { REPAIR, BUILD, DEMOLISH }

    /** One step for a builder: a block to set or to take down. */
    public record Task(BlockPos pos, ItemStack held, Kind kind) {
    }

    public enum ProposalKind { BUILD, REBUILD, HALL }

    /** A suggestion: what, why (a translation key), and for a rebuild which standing building. */
    public record Proposal(ProposalKind kind, Blueprint blueprint, String reason, int target) {
    }

    /** Which building (and which quarter of it) an architect is on; dropped when they stop asking for work. */
    private record Crew(Construction order, int quarter, long seen) {
    }

    private record Claim(UUID builder, long until) {
    }

    private final VillageHallBlockEntity hall;
    private final DamageLedger ledger = new DamageLedger();
    private final List<Construction> queue = new ArrayList<>();
    private final List<Construction> buildings = new ArrayList<>();
    /** Building blocks a builder couldn't reach, left for a while (not saved). */
    private final Map<BlockPos, Long> skipUntil = new HashMap<>();
    private final Map<UUID, Crew> crews = new HashMap<>();
    private final Map<BlockPos, Claim> claims = new HashMap<>();
    private List<ItemStack> missing = List.of();

    VillageWorks(VillageHallBlockEntity hall) {
        this.hall = hall;
    }

    private Level level() {
        return hall.getLevel();
    }

    // ---- fight damage ----

    public void recordDamage(BlockPos pos, BlockState state) {
        if (ledger.record(pos, state)) {
            hall.setChanged();
        }
    }

    public int repairCount() {
        return ledger.size();
    }

    // ---- the queue ----

    public List<Construction> queue() {
        return queue;
    }

    public List<Construction> buildings() {
        return buildings;
    }

    public int count(Blueprint blueprint) {
        int count = 0;
        for (Construction building : buildings) {
            if (building.blueprint() == blueprint) {
                count++;
            }
        }
        return count;
    }

    private int queued(Blueprint blueprint) {
        int count = 0;
        for (Construction order : queue) {
            if (order.blueprint() == blueprint) {
                count++;
            }
        }
        return count;
    }

    private @Nullable Construction hallBuilding() {
        for (Construction building : buildings) {
            if (building.blueprint().isHall()) {
                return building;
            }
        }
        return null;
    }

    private boolean hallWorkQueued() {
        for (Construction order : queue) {
            if (order.blueprint().isHall()) {
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> missing() {
        return missing;
    }

    /**
     * Checks a blueprint placed by the player (BlueprintItem). Returns why it can't go there, or null and
     * queues it.
     */
    public @Nullable Component order(Blueprint blueprint, BlockPos center, Rotation rotation) {
        if (blueprint.isHall()) {
            return Component.translatable("message.flightsuit.proposal_gone");
        }
        if (blueprint.stage() > hall.getStage()) {
            return Component.translatable("message.flightsuit.order_locked", Component.translatable(blueprint.translationKey()));
        }
        Construction site = new Construction(blueprint, center, rotation);
        Component problem = checkSite(site, null);
        if (problem != null) {
            return problem;
        }
        queue.add(site);
        hall.addNews(Component.translatable("news.flightsuit.build_ordered", Component.translatable(blueprint.translationKey())));
        hall.refreshStats();
        return null;
    }

    /** Inside the village, clear of the hall and other buildings (except the one being rebuilt), on good ground. */
    private @Nullable Component checkSite(Construction site, @Nullable Construction rebuilding) {
        for (Blueprint.Entry entry : site.entries()) {
            if (!hall.contains(entry.pos())) {
                return Component.translatable("message.flightsuit.order_outside");
            }
        }
        AABB footprint = site.footprint();
        if (!site.blueprint().isHall() && footprint.intersects(new AABB(hall.getBlockPos()).inflate(2.0D))) {
            return Component.translatable("message.flightsuit.order_hall");
        }
        for (Construction other : buildings) {
            if (other != rebuilding && other.footprint().intersects(footprint)) {
                return Component.translatable("message.flightsuit.order_overlap");
            }
        }
        for (Construction other : queue) {
            if (other.footprint().intersects(footprint)) {
                return Component.translatable("message.flightsuit.order_overlap");
            }
        }
        if (!site.terrainFits(level())) {
            return Component.translatable("message.flightsuit.order_ground");
        }
        return null;
    }

    public void cancel(int index) {
        if (index >= 0 && index < queue.size()) {
            Construction order = queue.remove(index);
            crews.values().removeIf(crew -> crew.order() == order);
        }
        hall.refreshStats();
    }

    // ---- suggestions (건의) ----

    /**
     * What the architects think the village needs right now: a roof over the hall, somewhere to sleep, food,
     * a lookout for the guards, the next stage's hall, and rebuilding the old stage's buildings once a better
     * one is open.
     */
    public List<Proposal> proposals() {
        List<Proposal> out = new ArrayList<>();
        int stage = hall.getStage();
        if (!hallWorkQueued()) {
            Construction current = hallBuilding();
            int next = stage + 1;
            boolean canGrow = next <= 2 && hall.getPopulation() >= VillageTuning.stagePopulation(next);
            if (canGrow) {
                out.add(new Proposal(ProposalKind.HALL, Blueprint.hallFor(next), "proposal.flightsuit.hall_" + next, -1));
            } else if (current == null) {
                out.add(new Proposal(ProposalKind.HALL, Blueprint.hallFor(stage), "proposal.flightsuit.hall_roof", -1));
            }
        }
        Blueprint lodging = stage >= 2 ? Blueprint.HOUSE : Blueprint.TENT;
        boolean lodgingQueued = queued(Blueprint.TENT) + queued(Blueprint.HOUSE) > 0;
        if (hall.getFreeBeds() == 0 && !lodgingQueued) {
            out.add(new Proposal(ProposalKind.BUILD, lodging, "proposal.flightsuit.no_beds", -1));
        }
        int farmers = hall.getJobCount(ResidentJob.FARMER);
        if (farmers > 0 && hall.getFood() < hall.getPopulation() * 3 && count(Blueprint.FARM) + queued(Blueprint.FARM) < farmers) {
            out.add(new Proposal(ProposalKind.BUILD, Blueprint.FARM, "proposal.flightsuit.food", -1));
        }
        if (hall.getJobCount(ResidentJob.GUARD) > 0 && count(Blueprint.WATCHTOWER) + queued(Blueprint.WATCHTOWER) == 0) {
            out.add(new Proposal(ProposalKind.BUILD, Blueprint.WATCHTOWER, "proposal.flightsuit.lookout", -1));
        }
        for (int i = 0; i < buildings.size(); i++) {
            Construction building = buildings.get(i);
            Blueprint upgrade = building.blueprint().upgrade();
            if (!building.blueprint().isHall() && upgrade != null && upgrade.stage() <= stage && !isBeingRebuilt(building)) {
                out.add(new Proposal(ProposalKind.REBUILD, upgrade, "proposal.flightsuit.rebuild", i));
                break;
            }
        }
        return out;
    }

    private boolean isBeingRebuilt(Construction building) {
        for (Construction order : queue) {
            if (order.replaces() == building) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player said yes to suggestion {@code index}. A new building needs a spot, so that hands them its
     * blueprint (into {@code give}); a rebuild or the hall's building just starts. Returns what to tell them.
     */
    public Component approve(int index, List<ItemStack> give) {
        List<Proposal> list = proposals();
        if (index < 0 || index >= list.size()) {
            return Component.translatable("message.flightsuit.proposal_gone");
        }
        Proposal proposal = list.get(index);
        Component name = Component.translatable(proposal.blueprint().translationKey());
        if (proposal.kind() == ProposalKind.BUILD) {
            give.add(BlueprintItem.of(proposal.blueprint()));
            return Component.translatable("message.flightsuit.blueprint_given", name);
        }
        Construction old;
        Construction order;
        if (proposal.kind() == ProposalKind.REBUILD) {
            old = buildings.get(proposal.target());
            order = old.rebuiltAs(proposal.blueprint());
        } else {
            // Around the hall block, front where the hall faces (the board looks out of the door).
            old = hallBuilding();
            order = old != null ? old.rebuiltAs(proposal.blueprint())
                    : new Construction(proposal.blueprint(), hall.getBlockPos().below(),
                    Blueprint.facing(hall.getBlockState().getValue(VillageHallBlock.FACING)));
        }
        Component problem = checkSite(order, old);
        if (problem != null) {
            return problem;
        }
        queue.add(order);
        if (old != null) {
            hall.addNews(Component.translatable("news.flightsuit.rebuild_ordered",
                    Component.translatable(old.blueprint().translationKey()), name));
        } else {
            hall.addNews(Component.translatable("news.flightsuit.build_ordered", name));
        }
        hall.refreshStats();
        return Component.translatable("message.flightsuit.work_ordered", name);
    }

    // ---- builder tasks ----

    /**
     * What builder {@code builder} at {@code from} should do next (null builder = just looking, for the
     * board): repairs first, then their crew's building (a rebuild takes the old one down before building up;
     * buildings go up layer by layer and wait for materials rather than roofing a missing wall). Only steps
     * that can be done now: support in place, nobody standing there, paid for (free when the owner is in
     * creative), not taken by another builder. Notes what is missing.
     */
    public @Nullable Task nextTask(Vec3 from, @Nullable UUID builder) {
        Level level = level();
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        skipUntil.values().removeIf(until -> until <= now);
        claims.values().removeIf(claim -> claim.until() <= now);
        crews.values().removeIf(crew -> now - crew.seen() > 600L || !queue.contains(crew.order()));
        boolean free = hall.ownerBuildsFree();
        Map<Item, Integer> lacking = new LinkedHashMap<>();

        Task task = nextRepair(level, from, free, lacking, builder);
        if (builder == null) {
            for (Construction order : queue) {
                if (order.isLoaded(level)) {
                    noteLacking(level, order, free, lacking);
                }
            }
        } else if (task == null) {
            Crew crew = joinCrew(builder, now);
            if (crew != null && crew.order().isLoaded(level)) {
                noteLacking(level, crew.order(), free, lacking);
                task = nextConstruction(level, crew.order(), crew.quarter(), from, free, builder);
            }
        }
        List<ItemStack> nowMissing = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : lacking.entrySet()) {
            if (nowMissing.size() < 3) {
                nowMissing.add(new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
        if (builder == null || !nowMissing.isEmpty()) {
            missing = nowMissing;
        }
        if (task != null && builder != null) {
            claims.put(task.pos(), new Claim(builder, now + 60L));
        }
        return task;
    }

    /** Keeps a builder on their building, or puts them on the first one with room in its crew. */
    private @Nullable Crew joinCrew(UUID builder, long now) {
        Crew crew = crews.get(builder);
        if (crew != null) {
            crew = new Crew(crew.order(), crew.quarter(), now);
            crews.put(builder, crew);
            return crew;
        }
        for (Construction order : queue) {
            boolean[] taken = new boolean[VillageTuning.CREW_SIZE];
            int members = 0;
            for (Crew other : crews.values()) {
                if (other.order() == order) {
                    taken[other.quarter() % VillageTuning.CREW_SIZE] = true;
                    members++;
                }
            }
            if (members < VillageTuning.CREW_SIZE) {
                int quarter = 0;
                while (taken[quarter]) {
                    quarter++;
                }
                crew = new Crew(order, quarter, now);
                crews.put(builder, crew);
                return crew;
            }
        }
        return null;
    }

    private boolean claimedByOther(BlockPos pos, @Nullable UUID builder) {
        Claim claim = claims.get(pos);
        return claim != null && !claim.builder().equals(builder);
    }

    private @Nullable Task nextRepair(Level level, Vec3 from, boolean free, Map<Item, Integer> lacking, @Nullable UUID builder) {
        Task best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<Map.Entry<BlockPos, BlockState>> it = ledger.entries().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockState> entry = it.next();
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            if (now.equals(state) || !now.canBeReplaced()) {
                // Already back, or someone built something else there - that is theirs now.
                it.remove();
                continue;
            }
            int cost = DamageLedger.cost(state);
            if (!free && cost > 0 && available(state) < cost) {
                lacking.merge(DamageLedger.material(state), cost, Integer::sum);
                continue;
            }
            if (claimedByOther(pos, builder) || !state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, CollisionContext.empty())) {
                continue;
            }
            double dist = Vec3.atCenterOf(pos).distanceToSqr(from);
            if (dist < bestDist) {
                best = new Task(pos, new ItemStack(state.getBlock().asItem()), Kind.REPAIR);
                bestDist = dist;
            }
        }
        return best;
    }

    /** What a building still needs from the storage (for the board), and the lowest layer waiting on it. */
    private int noteLacking(Level level, Construction order, boolean free, Map<Item, Integer> lacking) {
        int waitingLayer = Integer.MAX_VALUE;
        if (free) {
            return waitingLayer;
        }
        Map<Item, Integer> need = new HashMap<>();
        Map<Item, BlockState> sample = new HashMap<>();
        for (Blueprint.Entry entry : order.entries()) {
            int cost = DamageLedger.cost(entry.state());
            if (cost > 0 && !order.isDone(level, entry)) {
                need.merge(DamageLedger.material(entry.state()), cost, Integer::sum);
                sample.putIfAbsent(DamageLedger.material(entry.state()), entry.state());
            }
        }
        for (Blueprint.Entry entry : order.entries()) {
            int cost = DamageLedger.cost(entry.state());
            if (cost > 0 && !order.isDone(level, entry) && available(entry.state()) < cost) {
                waitingLayer = Math.min(waitingLayer, entry.layer());
            }
        }
        for (Map.Entry<Item, Integer> entry : need.entrySet()) {
            // Counted with stand-ins (dirt for farmland).
            int shortBy = entry.getValue() - available(sample.get(entry.getKey()));
            if (shortBy > 0) {
                lacking.merge(entry.getKey(), shortBy, Integer::sum);
            }
        }
        return waitingLayer;
    }

    /**
     * The next step on one building for one builder: their own quarter first, then anywhere (helping). A
     * rebuild takes the old building down first.
     */
    private @Nullable Task nextConstruction(Level level, Construction order, int quarter, Vec3 from, boolean free, UUID builder) {
        List<BlockPos> down = order.toDemolish(level);
        if (!down.isEmpty()) {
            for (int pass = 0; pass < 2; pass++) {
                for (BlockPos pos : down) {
                    if ((pass == 1 || order.quarter(pos) == quarter) && !skipUntil.containsKey(pos) && !claimedByOther(pos, builder)) {
                        return new Task(pos, new ItemStack(Items.IRON_AXE), Kind.DEMOLISH);
                    }
                }
            }
            return null;
        }
        int waitingLayer = noteLacking(level, order, free, new HashMap<>());
        for (int pass = 0; pass < 2; pass++) {
            Task best = null;
            double bestDist = Double.MAX_VALUE;
            int bestLayer = -1;
            for (Blueprint.Entry entry : order.entries()) {
                BlockPos pos = entry.pos();
                if (pass == 0 && order.quarter(pos) != quarter || entry.layer() > waitingLayer
                        || bestLayer >= 0 && entry.layer() > bestLayer) {
                    continue;
                }
                if (order.isDone(level, entry) || skipUntil.containsKey(pos) || claimedByOther(pos, builder)) {
                    continue;
                }
                int cost = DamageLedger.cost(entry.state());
                if (!free && cost > 0 && available(entry.state()) < cost || !order.isReady(level, entry)) {
                    continue;
                }
                double dist = Vec3.atCenterOf(pos).distanceToSqr(from);
                if (bestLayer < 0 || entry.layer() < bestLayer || dist < bestDist) {
                    ItemStack held = entry.state().isAir() ? ItemStack.EMPTY : new ItemStack(entry.state().getBlock().asItem());
                    best = new Task(pos, held, Kind.BUILD);
                    bestDist = dist;
                    bestLayer = entry.layer();
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** Does one step. False if it can't be done right now. */
    public boolean doTask(Task task) {
        Level level = level();
        if (level == null) {
            return false;
        }
        claims.remove(task.pos());
        boolean done = switch (task.kind()) {
            case REPAIR -> repair(level, task.pos());
            case BUILD -> build(level, task.pos());
            case DEMOLISH -> demolish(level, task.pos());
        };
        if (done) {
            hall.setChanged();
        }
        return done;
    }

    private boolean repair(Level level, BlockPos pos) {
        BlockState state = ledger.entries().get(pos);
        if (state == null) {
            return false;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            ledger.remove(pos);
            return false;
        }
        if (!pay(state)) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        ledger.remove(pos);
        return true;
    }

    private @Nullable Construction orderAt(BlockPos pos) {
        for (Construction order : queue) {
            if (order.entryAt(pos) != null || order.replaces() != null && order.replaces().entryAt(pos) != null) {
                return order;
            }
        }
        return null;
    }

    private boolean build(Level level, BlockPos pos) {
        Construction order = orderAt(pos);
        Blueprint.Entry entry = order != null ? order.entryAt(pos) : null;
        if (entry == null || !order.canReplace(level, entry) || !order.isReady(level, entry)) {
            return false;
        }
        BlockState state = entry.state();
        if (state.isAir()) {
            // Clearing grass, or digging the ground where the floor sits higher.
            level.destroyBlock(pos, false);
            return true;
        }
        if (!pay(state)) {
            return false;
        }
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !state.hasProperty(BlockStateProperties.BED_PART)) {
            // Panes and fences join up with what is already around them.
            state = Block.updateFromNeighbourShapes(state, level, pos);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        return true;
    }

    /**
     * Clears one block off the site (the old building, a tree, someone's old wall); its material goes into the
     * storage. Something with contents (a chest) is broken in place instead, so nothing inside is lost.
     */
    private boolean demolish(Level level, BlockPos pos) {
        Construction order = orderAt(pos);
        if (order == null || !order.toDemolish(level).contains(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity()) {
            level.destroyBlock(pos, true);
            return true;
        }
        Item item = state.getBlock().asItem();
        // Removing one half of a door or bed takes the other with it, so it pays back once.
        int count = state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        level.destroyBlock(pos, false);
        if (item != Items.AIR) {
            giveBack(level, pos, new ItemStack(item, count));
        }
        return true;
    }

    /** Finished orders move to the list of standing buildings (a rebuild takes the old one's place). */
    public void checkFinished() {
        Level level = level();
        Iterator<Construction> it = queue.iterator();
        while (it.hasNext()) {
            Construction order = it.next();
            if (order.isFinished(level)) {
                it.remove();
                crews.values().removeIf(crew -> crew.order() == order);
                if (order.replaces() != null) {
                    buildings.remove(order.replaces());
                }
                buildings.add(order);
                hall.announceBuilt(order.blueprint());
                if (order.blueprint().isHall() && order.blueprint().stage() > hall.getStage()) {
                    hall.stageUp();
                }
            }
        }
    }

    public void giveUp(Task task) {
        claims.remove(task.pos());
        if (task.kind() == Kind.REPAIR) {
            ledger.defer(task.pos());
        } else if (level() != null) {
            skipUntil.put(task.pos(), level().getGameTime() + 600L);
        }
    }

    /** Whether a builder standing here is in the way of a block that still has to go in. */
    public boolean isPendingSpot(BlockPos pos) {
        if (ledger.entries().containsKey(pos)) {
            return true;
        }
        for (Construction order : queue) {
            Blueprint.Entry entry = order.entryAt(pos);
            if (entry != null && !entry.state().isAir() && !order.isDone(level(), entry)) {
                return true;
            }
        }
        return false;
    }

    // ---- paying ----

    private boolean pay(BlockState state) {
        int cost = DamageLedger.cost(state);
        if (hall.ownerBuildsFree() || cost == 0) {
            return true;
        }
        if (available(state) < cost) {
            return false;
        }
        takeMaterial(state, cost);
        return true;
    }

    private int available(BlockState state) {
        Item item = DamageLedger.material(state);
        List<Item> subs = DamageLedger.substitutes(state);
        int count = 0;
        SimpleContainer storage = hall.getStorage();
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (stack.is(item) || subs.contains(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void takeMaterial(BlockState state, int amount) {
        Item item = DamageLedger.material(state);
        List<Item> subs = DamageLedger.substitutes(state);
        SimpleContainer storage = hall.getStorage();
        for (int pass = 0; pass < 2 && amount > 0; pass++) {
            for (int i = 0; i < storage.getContainerSize() && amount > 0; i++) {
                ItemStack stack = storage.getItem(i);
                if (pass == 0 ? stack.is(item) : subs.contains(stack.getItem())) {
                    int used = Math.min(amount, stack.getCount());
                    stack.shrink(used);
                    amount -= used;
                }
            }
        }
        storage.setChanged();
    }

    private void giveBack(Level level, BlockPos pos, ItemStack stack) {
        ItemStack rest = hall.store(stack);
        if (!rest.isEmpty()) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, rest));
        }
    }

    // ---- saving ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Ledger", ledger.save());
        ListTag orders = new ListTag();
        for (Construction order : queue) {
            orders.add(order.save());
        }
        tag.put("Queue", orders);
        ListTag standing = new ListTag();
        for (Construction building : buildings) {
            standing.add(building.save());
        }
        tag.put("Buildings", standing);
        return tag;
    }

    public void load(CompoundTag tag) {
        ledger.load(tag.getCompound("Ledger"));
        buildings.clear();
        for (Tag raw : tag.getList("Buildings", Tag.TAG_COMPOUND)) {
            Construction building = Construction.load((CompoundTag) raw);
            if (building != null) {
                buildings.add(building);
            }
        }
        queue.clear();
        for (Tag raw : tag.getList("Queue", Tag.TAG_COMPOUND)) {
            Construction order = Construction.load((CompoundTag) raw);
            if (order != null) {
                // A rebuild points at the very building on the standing list, so finishing it replaces that one.
                Construction old = order.replaces();
                if (old != null) {
                    for (Construction building : buildings) {
                        if (building.center().equals(old.center()) && building.blueprint() == old.blueprint()) {
                            order = building.rebuiltAs(order.blueprint());
                            break;
                        }
                    }
                }
                queue.add(order);
            }
        }
    }

    /** For the architect's screen: suggestions, open blueprints and the queue. */
    public CompoundTag screenData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Stage", hall.getStage());
        ListTag proposals = new ListTag();
        for (Proposal proposal : proposals()) {
            CompoundTag item = new CompoundTag();
            item.putString("Kind", proposal.kind().name());
            item.putInt("Blueprint", proposal.blueprint().ordinal());
            item.putString("Reason", proposal.reason());
            if (proposal.kind() == ProposalKind.REBUILD) {
                item.putInt("From", buildings.get(proposal.target()).blueprint().ordinal());
            }
            proposals.add(item);
        }
        tag.put("Proposals", proposals);
        ListTag orders = new ListTag();
        Level level = level();
        for (Construction order : queue) {
            CompoundTag item = new CompoundTag();
            item.putInt("Blueprint", order.blueprint().ordinal());
            item.putBoolean("Rebuild", order.replaces() != null);
            item.putInt("Placed", level != null ? order.placedCount(level) : 0);
            item.putInt("Total", order.solidCount());
            int crew = 0;
            for (Crew member : crews.values()) {
                if (member.order() == order) {
                    crew++;
                }
            }
            item.putInt("Crew", crew);
            orders.add(item);
        }
        tag.put("Queue", orders);
        return tag;
    }
}
