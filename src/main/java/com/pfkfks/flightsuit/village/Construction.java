package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One building - going up, or already standing on the hall's list: a blueprint at a spot, turned some way.
 * Progress isn't stored; the world is the progress: a block counts as done once the same block is there.
 *
 * A rebuild (개축) also knows the building it replaces on the same spot: first the old blocks the new
 * blueprint doesn't keep come down (their materials go back to the storage), then the new one goes up.
 */
public class Construction {
    private final Blueprint blueprint;
    private final BlockPos center;
    private final Rotation rotation;
    private final List<Blueprint.Entry> entries;
    private final Map<BlockPos, Blueprint.Entry> byPos = new HashMap<>();
    private final @Nullable Construction replaces;
    /**
     * Whose it is (after the M13 test): a house's household head, a farm's farmer, a workplace's worker. Kept by
     * name too, for the screens (the owner may be far away or gone).
     */
    private @Nullable UUID owner;
    private String ownerName = "";

    public Construction(Blueprint blueprint, BlockPos center, Rotation rotation) {
        this(blueprint, center, rotation, null);
    }

    public Construction(Blueprint blueprint, BlockPos center, Rotation rotation, @Nullable Construction replaces) {
        this.blueprint = blueprint;
        this.center = center.immutable();
        this.rotation = rotation;
        this.replaces = replaces;
        this.entries = blueprint.entries(center, rotation);
        for (Blueprint.Entry entry : entries) {
            byPos.put(entry.pos(), entry);
        }
    }

    /** The bigger version of this building on the same spot (still theirs). */
    public Construction rebuiltAs(Blueprint next) {
        Construction rebuilt = new Construction(next, center, rotation, this);
        rebuilt.owner = owner;
        rebuilt.ownerName = ownerName;
        return rebuilt;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID owner, String name) {
        this.owner = owner;
        this.ownerName = owner == null ? "" : name;
    }

    /** Whether {@code pos} is inside the building (its blocks' box). */
    public boolean holds(BlockPos pos) {
        return bounds().contains(Vec3.atCenterOf(pos));
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public BlockPos center() {
        return center;
    }

    public Rotation rotation() {
        return rotation;
    }

    public @Nullable Construction replaces() {
        return replaces;
    }

    public List<Blueprint.Entry> entries() {
        return entries;
    }

    public @Nullable Blueprint.Entry entryAt(BlockPos pos) {
        return byPos.get(pos);
    }

    /** The ground it takes up, plus a block of elbow room. */
    public AABB footprint() {
        int r = blueprint.reach() + 1;
        return new AABB(center.offset(-r, 0, -r), center.offset(r + 1, blueprint.height(), r + 1));
    }

    /** Exactly the blocks it fills (for the placement preview). */
    public AABB bounds() {
        int r = blueprint.reach();
        return new AABB(center.offset(-r, 0, -r), center.offset(r + 1, blueprint.height(), r + 1));
    }

    /**
     * Which quarter of the building a block is in (0..3, around the center), so a crew of four can split
     * it. Blocks on the middle lines alternate between the two quarters beside them.
     */
    public int quarter(BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        boolean east = dx > 0 || dx == 0 && ((pos.getY() + dz) & 1) == 0;
        boolean south = dz > 0 || dz == 0 && ((pos.getY() + dx) & 1) == 0;
        return (east ? 1 : 0) + (south ? 2 : 0);
    }

    /** Which way its front (the door) faces. */
    public Direction front() {
        return rotation.rotate(Direction.NORTH);
    }

    /**
     * What the bottom two layers may be built over or dug out (so a building can sit on ground a block
     * uneven): natural ground - never someone's floor or farm.
     */
    static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SNOW_BLOCK);
    }

    /** Whether a block standing there belongs to the building this one replaces (so it may come down). */
    private boolean isOld(BlockPos pos, BlockState now) {
        Blueprint.Entry old = replaces != null ? replaces.entryAt(pos) : null;
        return old != null && !old.state().isAir() && now.is(old.state().getBlock());
    }

    /**
     * Whether the builder may put this entry's block in now (ignoring materials and who stands there). The
     * same block counts as done whatever its state (wet farmland, joined-up panes). Anything solid that isn't
     * ground is cleared off first (toDemolish); water is built over but never "cleared" (it would flow back).
     */
    public boolean canReplace(Level level, Blueprint.Entry entry) {
        BlockState now = level.getBlockState(entry.pos());
        if (now.is(entry.state().getBlock())) {
            return false;
        }
        boolean ground = entry.layer() <= 1 && isNaturalGround(now) && now.getFluidState().isEmpty();
        if (entry.state().isAir()) {
            return !now.isAir() && now.getFluidState().isEmpty() && (now.canBeReplaced() || ground);
        }
        return now.canBeReplaced() || ground;
    }

    public boolean isDone(Level level, Blueprint.Entry entry) {
        return !canReplace(level, entry);
    }

    /** Ready to place: support in place (doors bottom first, beds foot first) and nobody standing in it. */
    public boolean isReady(Level level, Blueprint.Entry entry) {
        BlockState state = entry.state();
        if (!state.isAir() && !state.canSurvive(level, entry.pos())) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            BlockPos foot = entry.pos().relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
            if (!level.getBlockState(foot).is(state.getBlock())) {
                return false;
            }
        }
        return state.isAir() || level.isUnobstructed(state, entry.pos(), CollisionContext.empty());
    }

    /**
     * What has to come down before (or while) building, top first: the old building's blocks the new one
     * doesn't keep (a rebuild), and anything else standing where the new blocks go (user's call: bulldoze the
     * site rather than refuse it). Natural ground in the bottom two layers isn't on the list - it's built over
     * or dug out directly - and neither is the hall block or anything that can't be broken.
     */
    public List<BlockPos> toDemolish(Level level) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> listed = new HashSet<>();
        if (replaces != null) {
            List<Blueprint.Entry> old = replaces.entries();
            for (int i = old.size() - 1; i >= 0; i--) {
                Blueprint.Entry entry = old.get(i);
                if (entry.state().isAir()) {
                    continue;
                }
                BlockState now = level.getBlockState(entry.pos());
                Blueprint.Entry keep = byPos.get(entry.pos());
                if (isOld(entry.pos(), now) && (keep == null || !keep.state().is(now.getBlock())) && listed.add(entry.pos())) {
                    out.add(entry.pos());
                }
            }
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Blueprint.Entry entry = entries.get(i);
            if (inTheWay(level, entry) && listed.add(entry.pos())) {
                out.add(entry.pos());
            }
        }
        return out;
    }

    private static boolean inTheWay(Level level, Blueprint.Entry entry) {
        BlockState now = level.getBlockState(entry.pos());
        if (now.isAir() || now.canBeReplaced() || now.is(entry.state().getBlock()) || !canClear(level, entry.pos(), now)) {
            return false;
        }
        return !(entry.layer() <= 1 && isNaturalGround(now));
    }

    /** Never the hall block itself, nor what nobody can break (bedrock and the like). */
    static boolean canClear(Level level, BlockPos pos, BlockState state) {
        return !(state.getBlock() instanceof VillageHallBlock) && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public boolean isLoaded(Level level) {
        return level.isLoaded(center) && level.isLoaded(center.offset(blueprint.reach(), 0, blueprint.reach()))
                && level.isLoaded(center.offset(-blueprint.reach(), 0, -blueprint.reach()));
    }

    /** Old building down, every new block in place (or taken by something that isn't ours to remove). */
    public boolean isFinished(Level level) {
        if (!isLoaded(level) || !toDemolish(level).isEmpty()) {
            return false;
        }
        for (Blueprint.Entry entry : entries) {
            if (!isDone(level, entry)) {
                return false;
            }
        }
        return true;
    }

    public int placedCount(Level level) {
        int count = 0;
        for (Blueprint.Entry entry : entries) {
            if (!entry.state().isAir() && level.getBlockState(entry.pos()).is(entry.state().getBlock())) {
                count++;
            }
        }
        return count;
    }

    public int solidCount() {
        int count = 0;
        for (Blueprint.Entry entry : entries) {
            if (!entry.state().isAir()) {
                count++;
            }
        }
        return count;
    }


    /**
     * Whether the building can go here at all: loaded, and nothing where it goes that can't be cleared
     * (bedrock, a hall). Trees, terrain and old structures are fine - they get bulldozed (clearCount says how
     * much). Shared by the hall's check and the placement preview.
     */
    public boolean terrainFits(Level level) {
        if (!isLoaded(level)) {
            return false;
        }
        for (Blueprint.Entry entry : entries) {
            BlockPos pos = entry.pos();
            BlockState now = level.getBlockState(pos);
            if (!now.isAir() && !now.canBeReplaced() && !now.is(entry.state().getBlock()) && !canClear(level, pos, now)) {
                return false;
            }
        }
        return true;
    }

    /** How many blocks would have to be cleared off the site first (for the preview's colour). */
    public int clearCount(Level level) {
        return toDemolish(level).size();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Blueprint", blueprint.name());
        tag.put("Center", NbtUtils.writeBlockPos(center));
        tag.putString("Rotation", rotation.name());
        if (replaces != null) {
            tag.put("Replaces", replaces.save());
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        return tag;
    }

    public static @Nullable Construction load(CompoundTag tag) {
        Blueprint blueprint;
        Rotation rotation;
        try {
            blueprint = Blueprint.valueOf(tag.getString("Blueprint"));
            rotation = Rotation.valueOf(tag.getString("Rotation"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Construction replaces = tag.contains("Replaces") ? load(tag.getCompound("Replaces")) : null;
        Construction construction = new Construction(blueprint, NbtUtils.readBlockPos(tag.getCompound("Center")), rotation, replaces);
        if (tag.hasUUID("Owner")) {
            construction.setOwner(tag.getUUID("Owner"), tag.getString("OwnerName"));
        }
        return construction;
    }
}
