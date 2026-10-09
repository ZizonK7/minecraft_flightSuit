package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.PowerGrid;
import com.pfkfks.flightsuit.energy.PowerTuning;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitHome;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

/**
 * The suit's home (DESIGN.md 3-1, 4-8): holds one suit (a piece per armor slot), takes power from the
 * wireless grid, and charges + repairs whatever is docked. The docked suit is rendered standing on top.
 */
public class SuitStationBlockEntity extends PowerBlockEntity {
    private final Map<EquipmentSlot, ItemStack> parts = new EnumMap<>(EquipmentSlot.class);

    public SuitStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUIT_STATION.get(), pos, state,
                PowerTuning.STATION_CAPACITY, PowerTuning.STATION_INPUT, 0);
    }

    @Override
    public PowerGrid.Role powerRole() {
        return PowerGrid.Role.CONSUMER;
    }

    public boolean hasSuit() {
        return !parts.isEmpty();
    }

    public Map<EquipmentSlot, ItemStack> getParts() {
        return parts;
    }

    public SuitType getSuitType() {
        for (ItemStack stack : parts.values()) {
            if (stack.getItem() instanceof SuitArmorItem armor) {
                return armor.getSuitType();
            }
        }
        return null;
    }

    /** The docked suit stands ~2 blocks tall on top of the platform; don't cull it as a 1-block entity. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0D, 2.0D, 0.0D).inflate(0.5D);
    }

    /** Where suit pieces launch from / return to. */
    public Vec3 dockPoint() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0D, 1.25D, 0.0D);
    }

    public boolean canDock(Map<EquipmentSlot, ItemStack> incoming) {
        SuitType current = getSuitType();
        for (Map.Entry<EquipmentSlot, ItemStack> entry : incoming.entrySet()) {
            if (parts.containsKey(entry.getKey())) {
                return false;
            }
            if (current != null && entry.getValue().getItem() instanceof SuitArmorItem armor && armor.getSuitType() != current) {
                return false;
            }
        }
        return !incoming.isEmpty();
    }

    public void dock(Map<EquipmentSlot, ItemStack> incoming) {
        for (Map.Entry<EquipmentSlot, ItemStack> entry : incoming.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                if (level != null) {
                    SuitHome.stamp(entry.getValue(), level, worldPosition);
                }
                parts.put(entry.getKey(), entry.getValue());
            }
        }
        syncToClients();
    }

    public Map<EquipmentSlot, ItemStack> takeAll() {
        Map<EquipmentSlot, ItemStack> taken = new EnumMap<>(parts);
        parts.clear();
        syncToClients();
        return taken;
    }

    /** Average charge of the docked suit, 0-100, or -1 when empty. */
    public int suitChargePercent() {
        if (parts.isEmpty()) {
            return -1;
        }
        long energy = 0;
        long capacity = 0;
        for (ItemStack stack : parts.values()) {
            energy += SuitEnergy.get(stack);
            capacity += SuitEnergy.capacity(stack);
        }
        return capacity <= 0 ? 0 : (int) (energy * 100 / capacity);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SuitStationBlockEntity station) {
        if (station.parts.isEmpty()) {
            return;
        }
        // Charge: arc reactor (chest) first, then the small buffers.
        int budget = Math.min(PowerTuning.STATION_CHARGE_RATE, station.energy.getEnergyStored());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = station.parts.get(slot);
            if (stack == null || budget <= 0) {
                continue;
            }
            int accepted = SuitEnergy.receive(stack, budget);
            station.energy.consume(accepted);
            budget -= accepted;
        }
        // Repair.
        if (level.getGameTime() % 10 == 0) {
            for (ItemStack stack : station.parts.values()) {
                if (stack.isDamaged() && station.energy.getEnergyStored() >= PowerTuning.STATION_REPAIR_COST) {
                    station.energy.consume(PowerTuning.STATION_REPAIR_COST);
                    stack.setDamageValue(Math.max(0, stack.getDamageValue() - PowerTuning.STATION_REPAIR_POINTS));
                }
            }
        }
        station.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag partsTag = new CompoundTag();
        for (Map.Entry<EquipmentSlot, ItemStack> entry : parts.entrySet()) {
            partsTag.put(entry.getKey().getName(), entry.getValue().save(new CompoundTag()));
        }
        tag.put("SuitParts", partsTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        parts.clear();
        CompoundTag partsTag = tag.getCompound("SuitParts");
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            if (partsTag.contains(slot.getName())) {
                ItemStack stack = ItemStack.of(partsTag.getCompound(slot.getName()));
                if (!stack.isEmpty()) {
                    parts.put(slot, stack);
                }
            }
        }
    }
}
