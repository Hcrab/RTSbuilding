package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class FluidEntry {
    private final String fluidId;
    private final String label;
    private final long amount;
    private final long capacity;
    private final String mod;
    private final String name;
    private final ItemStack preview;

    public FluidEntry(String fluidId, String label, long amount, long capacity,
                      String mod, String name, ItemStack preview) {
        this.fluidId = fluidId;
        this.label = label;
        this.amount = amount;
        this.capacity = capacity;
        this.mod = mod;
        this.name = name;
        this.preview = ClientRecordSupport.copyStack(preview);
    }

    public String fluidId() { return fluidId; }
    public String label() { return label; }
    public long amount() { return amount; }
    public long capacity() { return capacity; }
    public String mod() { return mod; }
    public String name() { return name; }
    public ItemStack preview() { return ClientRecordSupport.copyStack(preview); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FluidEntry)) return false;
        FluidEntry value = (FluidEntry) other;
        return amount == value.amount && capacity == value.capacity
                && Objects.equals(fluidId, value.fluidId) && Objects.equals(label, value.label)
                && Objects.equals(mod, value.mod) && Objects.equals(name, value.name)
                && ClientRecordSupport.stackEquals(preview, value.preview);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(fluidId, label, amount, capacity, mod, name)
                + ClientRecordSupport.stackHash(preview);
    }

    @Override
    public String toString() {
        return "FluidEntry[fluidId=" + fluidId + ", label=" + label + ", amount=" + amount
                + ", capacity=" + capacity + ", mod=" + mod + ", name=" + name
                + ", preview=" + preview + ']';
    }
}
