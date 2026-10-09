package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Station storage (DESIGN.md 4-8): the advanced vault next to the suit stations. Wirelessly paired with every
 * station within the power-grid radius - what a remote-piloted suit picks up or digs out lands here.
 *
 * It sits on the grid as a consumer: while it has power it stays locked (its upkeep trickles away every tick),
 * which is what keeps thieves out later (DESIGN.md 4-14 - Batman's EMP drains it to break in).
 */
public class StationStorageBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int SIZE = 54;

    private final SimpleContainer storage = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            StationStorageBlockEntity.this.setChanged();
        }
    };

    public StationStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION_STORAGE.get(), pos, state,
                PowerTuning.STORAGE_CAPACITY, PowerTuning.STORAGE_INPUT, 0);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.CONSUMER;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StationStorageBlockEntity storage) {
        storage.energy().consume(PowerTuning.STORAGE_UPKEEP);
    }

    public SimpleContainer getStorage() {
        return storage;
    }

    /** Locked while powered (thieves need to cut the power first). */
    public boolean isLocked() {
        return energy().getEnergyStored() > 0;
    }

    /** Puts what it can in - topping up matching stacks first, then empty slots. Returns the leftovers. */
    public ItemStack insert(ItemStack stack) {
        return storage.addItem(stack);
    }

    /** Merges partial stacks and packs everything to the front, grouped by item. */
    public void sort() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
            storage.setItem(i, ItemStack.EMPTY);
        }
        items.sort(Comparator.comparing((ItemStack stack) -> String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                .thenComparing(stack -> stack.getHoverName().getString()));
        for (ItemStack stack : items) {
            // addItem merges into matching stacks before taking a new slot, so this also packs partial stacks.
            ItemStack left = storage.addItem(stack);
            if (!left.isEmpty() && level != null) {
                // Can't happen (same items, same space), but never lose anything.
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                        worldPosition.getZ() + 0.5D, left);
            }
        }
        storage.setChanged();
    }

    public int usedSlots() {
        int used = 0;
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (!storage.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        Component state = isLocked()
                ? Component.translatable("container.flightsuit.station_storage.locked").withStyle(ChatFormatting.DARK_AQUA)
                : Component.translatable("container.flightsuit.station_storage.unpowered").withStyle(ChatFormatting.RED);
        return Component.translatable("container.flightsuit.station_storage", state);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.sixRows(id, inventory, storage);
    }

    // ---- save ----

    @Override
    public CompoundTag getUpdateTag() {
        // Clients only need the charge, not the contents (those travel with the open menu).
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", energy().getEnergyStored());
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(storage.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < storage.getContainerSize(); i++) {
            items.set(i, storage.getItem(i));
        }
        tag.put("Storage", ContainerHelper.saveAllItems(new CompoundTag(), items));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Storage")) {
            NonNullList<ItemStack> items = NonNullList.withSize(storage.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Storage"), items);
            for (int i = 0; i < items.size(); i++) {
                storage.setItem(i, items.get(i));
            }
        }
    }
}
