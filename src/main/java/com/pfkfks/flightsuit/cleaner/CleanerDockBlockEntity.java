package com.pfkfks.flightsuit.cleaner;

import com.pfkfks.flightsuit.block.PowerBlockEntity;
import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The cleaner robot's home: stores its cleaning areas, a 27-slot bin for everything it vacuums up, and a
 * power buffer topped up by the wireless grid (each cleaning run costs power).
 */
public class CleanerDockBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int CAPACITY = 20_000;
    public static final int ROUTINE_COST = 2_000;
    public static final int MAX_AREAS = 8;

    /** Client-side registry so the area overlay can find docks without scanning chunks. */
    private static final Set<CleanerDockBlockEntity> CLIENT_DOCKS = Collections.newSetFromMap(new WeakHashMap<>());

    private final List<CleanArea> areas = new ArrayList<>();
    private final SimpleContainer bin = new SimpleContainer(27) {
        @Override
        public void setChanged() {
            super.setChanged();
            CleanerDockBlockEntity.this.setChanged();
        }
    };

    public CleanerDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEANER_DOCK.get(), pos, state, CAPACITY, 1_000, 0);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.CONSUMER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_DOCKS.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_DOCKS.remove(this);
    }

    public static List<CleanerDockBlockEntity> clientDocks() {
        return new ArrayList<>(CLIENT_DOCKS);
    }

    public List<CleanArea> getAreas() {
        return areas;
    }

    public boolean addArea(CleanArea area) {
        if (areas.size() >= MAX_AREAS) {
            return false;
        }
        areas.add(area);
        syncToClients();
        return true;
    }

    public void clearAreas() {
        areas.clear();
        syncToClients();
    }

    public boolean inAnyArea(BlockPos pos) {
        for (CleanArea area : areas) {
            if (area.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public SimpleContainer getBin() {
        return bin;
    }

    /** Where the robot sits when docked. */
    public Vec3 dockPoint() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0D, 2.0D / 16.0D, 0.0D);
    }

    public boolean payForRoutine(boolean free) {
        if (free) {
            return true;
        }
        if (energy.getEnergyStored() < ROUTINE_COST) {
            return false;
        }
        energy.consume(ROUTINE_COST);
        return true;
    }

    /** Moves everything it can from the robot's hopper into the bin; leftovers stay with the robot. */
    public void deposit(SimpleContainer from) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                from.setItem(i, bin.addItem(stack));
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.flightsuit.cleaner_dock");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.threeRows(id, inventory, bin);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CleanerDockBlockEntity dock) {
        // Pure consumer: the grid pushes power in; nothing to do per tick.
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (CleanArea area : areas) {
            list.add(area.save());
        }
        tag.put("Areas", list);
        tag.put("Bin", ContainerHelper.saveAllItems(new CompoundTag(), binItems()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        areas.clear();
        for (Tag entry : tag.getList("Areas", Tag.TAG_COMPOUND)) {
            areas.add(CleanArea.load((CompoundTag) entry));
        }
        if (tag.contains("Bin")) {
            net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(bin.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Bin"), items);
            for (int i = 0; i < items.size(); i++) {
                bin.setItem(i, items.get(i));
            }
        }
    }

    private net.minecraft.core.NonNullList<ItemStack> binItems() {
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(bin.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < bin.getContainerSize(); i++) {
            items.set(i, bin.getItem(i));
        }
        return items;
    }

    /** Clients only need the areas (for the overlay), not the bin contents. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (CleanArea area : areas) {
            list.add(area.save());
        }
        tag.put("Areas", list);
        return tag;
    }
}
