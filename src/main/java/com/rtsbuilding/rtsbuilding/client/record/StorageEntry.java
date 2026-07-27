package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class StorageEntry {
    private final ItemStack stack;
    private final String itemId;
    private final long count;
    private final String mod;
    private final String name;

    public StorageEntry(ItemStack stack, String itemId, long count, String mod, String name) {
        this.stack = ClientRecordSupport.copyStack(stack);
        this.itemId = itemId;
        this.count = count;
        this.mod = mod;
        this.name = name;
    }

    public ItemStack stack() { return ClientRecordSupport.copyStack(stack); }
    public String itemId() { return itemId; }
    public long count() { return count; }
    public String mod() { return mod; }
    public String name() { return name; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StorageEntry)) return false;
        StorageEntry value = (StorageEntry) other;
        return count == value.count && Objects.equals(itemId, value.itemId)
                && Objects.equals(mod, value.mod) && Objects.equals(name, value.name)
                && ClientRecordSupport.stackEquals(stack, value.stack);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(itemId, count, mod, name)
                + ClientRecordSupport.stackHash(stack);
    }

    @Override
    public String toString() {
        return "StorageEntry[stack=" + stack + ", itemId=" + itemId + ", count=" + count
                + ", mod=" + mod + ", name=" + name + ']';
    }
}
