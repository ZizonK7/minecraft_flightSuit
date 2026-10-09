package com.pfkfks.flightsuit.energy;

import net.minecraftforge.energy.EnergyStorage;

/** Forge Energy buffer that reports changes (so the owning block entity can save / sync) and allows internal set/insert. */
public class NodeEnergyStorage extends EnergyStorage {
    private final Runnable onChanged;

    public NodeEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (received > 0 && !simulate) {
            onChanged.run();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (extracted > 0 && !simulate) {
            onChanged.run();
        }
        return extracted;
    }

    /** Internal generation, ignoring the external receive limit. */
    public int generate(int amount) {
        int accepted = Math.min(amount, capacity - energy);
        if (accepted > 0) {
            energy += accepted;
            onChanged.run();
        }
        return accepted;
    }

    /** Internal use (charging docked suits), ignoring the external extract limit. */
    public int consume(int amount) {
        int taken = Math.min(amount, energy);
        if (taken > 0) {
            energy -= taken;
            onChanged.run();
        }
        return taken;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(capacity, energy));
    }

    public float fillRatio() {
        return capacity <= 0 ? 0.0F : energy / (float) capacity;
    }
}
