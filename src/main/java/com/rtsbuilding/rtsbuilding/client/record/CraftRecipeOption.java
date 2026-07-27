package com.rtsbuilding.rtsbuilding.client.record;

import java.util.Objects;

public final class CraftRecipeOption {
    private final String recipeId;
    private final int resultCount;
    private final boolean craftable;
    private final String summary;
    private final String missingSummary;

    public CraftRecipeOption(String recipeId, int resultCount, boolean craftable,
                             String summary, String missingSummary) {
        this.recipeId = recipeId;
        this.resultCount = resultCount;
        this.craftable = craftable;
        this.summary = summary;
        this.missingSummary = missingSummary;
    }

    public String recipeId() { return recipeId; }
    public int resultCount() { return resultCount; }
    public boolean craftable() { return craftable; }
    public String summary() { return summary; }
    public String missingSummary() { return missingSummary; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CraftRecipeOption)) return false;
        CraftRecipeOption value = (CraftRecipeOption) other;
        return resultCount == value.resultCount && craftable == value.craftable
                && Objects.equals(recipeId, value.recipeId)
                && Objects.equals(summary, value.summary)
                && Objects.equals(missingSummary, value.missingSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, resultCount, craftable, summary, missingSummary);
    }

    @Override
    public String toString() {
        return "CraftRecipeOption[recipeId=" + recipeId + ", resultCount=" + resultCount
                + ", craftable=" + craftable + ", summary=" + summary
                + ", missingSummary=" + missingSummary + ']';
    }
}
