package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocks of the village broken in a fight (explosions, and later the raiders' side effects), with the state
 * they had - what the builders put back (DESIGN.md 4-11: "싸우다 보니 부서지는" 흐름 → 건축가가 복구).
 * The first state recorded for a spot wins, so a crater blown twice still comes back as the original wall.
 */
public class DamageLedger {
    private final Map<BlockPos, BlockState> entries = new LinkedHashMap<>();

    public boolean record(BlockPos pos, BlockState state) {
        if (!worthRestoring(state) || entries.size() >= VillageTuning.LEDGER_MAX || entries.containsKey(pos)) {
            return false;
        }
        entries.put(pos.immutable(), state);
        return true;
    }

    /** Air, fire, water, grass and the like are not "damage". */
    static boolean worthRestoring(BlockState state) {
        return !state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof BaseFireBlock)
                && !(state.getBlock() instanceof LiquidBlock);
    }

    public Map<BlockPos, BlockState> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public void remove(BlockPos pos) {
        entries.remove(pos);
    }

    /** Moves an entry to the back of the line (a builder couldn't reach it). */
    public void defer(BlockPos pos) {
        BlockState state = entries.remove(pos);
        if (state != null) {
            entries.put(pos, state);
        }
    }

    /**
     * What putting this state back costs. The second half of a door or bed is paid with the first; a bare
     * block whose item is missing (grass, farmland, path) can be paid in dirt.
     */
    public static int cost(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return 0;
        }
        if (material(state) == Items.AIR) {
            return 0;
        }
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return 2;
        }
        return 1;
    }

    public static Item material(BlockState state) {
        return state.getBlock().asItem();
    }

    /** Stand-ins accepted when the exact item isn't in the village storage. */
    public static List<Item> substitutes(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.COARSE_DIRT)) {
            return List.of(Items.DIRT);
        }
        return List.of();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, BlockState> entry : entries.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.put("Pos", NbtUtils.writeBlockPos(entry.getKey()));
            item.put("State", NbtUtils.writeBlockState(entry.getValue()));
            list.add(item);
        }
        tag.put("Entries", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        entries.clear();
        for (Tag raw : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) raw;
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), item.getCompound("State"));
            if (!state.isAir()) {
                entries.put(NbtUtils.readBlockPos(item.getCompound("Pos")), state);
            }
        }
    }
}
