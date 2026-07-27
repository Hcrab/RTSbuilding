package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class FunnelBufferEntry {
    private final ItemStack stack;
    private final String itemId;
    private final long count;

    public FunnelBufferEntry(ItemStack stack, String itemId, long count) {
        this.stack = ClientRecordSupport.copyStack(stack);
        this.itemId = itemId;
        this.count = count;
    }

    public ItemStack stack() { return ClientRecordSupport.copyStack(stack); }
    public String itemId() { return itemId; }
    public long count() { return count; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunnelBufferEntry)) return false;
        FunnelBufferEntry value = (FunnelBufferEntry) other;
        return count == value.count && Objects.equals(itemId, value.itemId)
                && ClientRecordSupport.stackEquals(stack, value.stack);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(itemId, count) + ClientRecordSupport.stackHash(stack);
    }

    @Override
    public String toString() {
        return "FunnelBufferEntry[stack=" + stack + ", itemId=" + itemId
                + ", count=" + count + ']';
    }
}
