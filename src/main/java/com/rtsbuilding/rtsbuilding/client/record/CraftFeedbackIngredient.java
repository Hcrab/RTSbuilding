package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public final class CraftFeedbackIngredient {
    private final String itemId;
    private final String label;
    private final ItemStack preview;
    private final int count;

    public CraftFeedbackIngredient(String itemId, String label, ItemStack preview, int count) {
        this.itemId = itemId;
        this.label = label;
        this.preview = ClientRecordSupport.copyStack(preview);
        this.count = count;
    }

    public String itemId() { return itemId; }
    public String label() { return label; }
    public ItemStack preview() { return ClientRecordSupport.copyStack(preview); }
    public int count() { return count; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CraftFeedbackIngredient)) return false;
        CraftFeedbackIngredient value = (CraftFeedbackIngredient) other;
        return count == value.count && Objects.equals(itemId, value.itemId)
                && Objects.equals(label, value.label)
                && ClientRecordSupport.stackEquals(preview, value.preview);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(itemId, label, count) + ClientRecordSupport.stackHash(preview);
    }

    @Override
    public String toString() {
        return "CraftFeedbackIngredient[itemId=" + itemId + ", label=" + label
                + ", preview=" + preview + ", count=" + count + ']';
    }
}
