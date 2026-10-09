package com.pfkfks.flightsuit.block;

import com.pfkfks.flightsuit.energy.NodeEnergyStorage;
import com.pfkfks.flightsuit.energy.PowerGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for every powered block: a Forge Energy buffer exposed as a capability (so other mods'
 * cables can plug in too), saved to NBT, and registered with the wireless {@link PowerGrid} while loaded.
 */
public abstract class PowerBlockEntity extends BlockEntity implements PowerGrid.Node {
    protected final NodeEnergyStorage energy;
    private final LazyOptional<IEnergyStorage> energyCap;

    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                               int capacity, int maxReceive, int maxExtract) {
        super(type, pos, state);
        this.energy = new NodeEnergyStorage(capacity, maxReceive, maxExtract, this::setChanged);
        this.energyCap = LazyOptional.of(() -> energy);
    }

    @Override
    public NodeEnergyStorage energy() {
        return energy;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            PowerGrid.add(level, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            PowerGrid.remove(level, this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) {
            PowerGrid.remove(level, this);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.setEnergy(tag.getInt("Energy"));
    }

    /** Pushes the block entity's client-relevant data (see getUpdateTag) to watching clients. */
    protected void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
