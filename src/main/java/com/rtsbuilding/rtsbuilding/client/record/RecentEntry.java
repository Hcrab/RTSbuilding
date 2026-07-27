package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class RecentEntry {
    private final boolean fluid;
    private final String id;
    private final String label;
    private final long amount;
    private final long capacity;
    private final byte kind;
    private final ItemStack preview;

    public RecentEntry(boolean fluid, String id, String label, long amount,
                       long capacity, byte kind, ItemStack preview) {
        this.fluid = fluid;
        this.id = id;
        this.label = label;
        this.amount = amount;
        this.capacity = capacity;
        this.kind = kind;
        this.preview = ClientRecordSupport.copyStack(preview);
    }

    public boolean fluid() { return fluid; }
    public String id() { return id; }
    public String label() { return label; }
    public long amount() { return amount; }
    public long capacity() { return capacity; }
    public byte kind() { return kind; }
    public ItemStack preview() { return ClientRecordSupport.copyStack(preview); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecentEntry)) return false;
        RecentEntry value = (RecentEntry) other;
        return fluid == value.fluid && amount == value.amount && capacity == value.capacity
                && kind == value.kind && Objects.equals(id, value.id)
                && Objects.equals(label, value.label)
                && ClientRecordSupport.stackEquals(preview, value.preview);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(fluid, id, label, amount, capacity, kind)
                + ClientRecordSupport.stackHash(preview);
    }

    @Override
    public String toString() {
        return "RecentEntry[fluid=" + fluid + ", id=" + id + ", label=" + label
                + ", amount=" + amount + ", capacity=" + capacity + ", kind=" + kind
                + ", preview=" + preview + ']';
    }
}
