package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CraftableEntry {
    private final ItemStack stack;
    private final String recipeId;
    private final String itemId;
    private final int resultCount;
    private final boolean craftable;
    private final String missingSummary;
    private final String mod;
    private final String name;
    private final List<CraftRecipeOption> recipeOptions;

    public CraftableEntry(ItemStack stack, String recipeId, String itemId, int resultCount,
                          boolean craftable, String missingSummary, String mod, String name,
                          List<CraftRecipeOption> recipeOptions) {
        this.stack = ClientRecordSupport.copyStack(stack);
        this.recipeId = recipeId;
        this.itemId = itemId;
        this.resultCount = resultCount;
        this.craftable = craftable;
        this.missingSummary = missingSummary;
        this.mod = mod;
        this.name = name;
        List<CraftRecipeOption> safe = recipeOptions == null
                ? Collections.<CraftRecipeOption>emptyList() : recipeOptions;
        this.recipeOptions = Collections.unmodifiableList(
                new ArrayList<CraftRecipeOption>(safe));
    }

    public ItemStack stack() { return ClientRecordSupport.copyStack(stack); }
    public String recipeId() { return recipeId; }
    public String itemId() { return itemId; }
    public int resultCount() { return resultCount; }
    public boolean craftable() { return craftable; }
    public String missingSummary() { return missingSummary; }
    public String mod() { return mod; }
    public String name() { return name; }
    public List<CraftRecipeOption> recipeOptions() { return recipeOptions; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CraftableEntry)) return false;
        CraftableEntry value = (CraftableEntry) other;
        return resultCount == value.resultCount && craftable == value.craftable
                && ClientRecordSupport.stackEquals(stack, value.stack)
                && Objects.equals(recipeId, value.recipeId)
                && Objects.equals(itemId, value.itemId)
                && Objects.equals(missingSummary, value.missingSummary)
                && Objects.equals(mod, value.mod) && Objects.equals(name, value.name)
                && Objects.equals(recipeOptions, value.recipeOptions);
    }

    @Override
    public int hashCode() {
        int result = ClientRecordSupport.stackHash(stack);
        result = 31 * result + Objects.hash(recipeId, itemId, resultCount, craftable,
                missingSummary, mod, name, recipeOptions);
        return result;
    }

    @Override
    public String toString() {
        return "CraftableEntry[stack=" + stack + ", recipeId=" + recipeId
                + ", itemId=" + itemId + ", resultCount=" + resultCount
                + ", craftable=" + craftable + ", missingSummary=" + missingSummary
                + ", mod=" + mod + ", name=" + name
                + ", recipeOptions=" + recipeOptions + ']';
    }
}
