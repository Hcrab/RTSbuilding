package com.rtsbuilding.rtsbuilding.uicore.craft;

/** 合成数量窗中的一条纯配方选项。 */
public final class CraftQuantityOption {
    public final String recipeId;
    public final String summary;
    public final String missingSummary;
    public final int resultCount;
    public final boolean craftable;

    public CraftQuantityOption(String recipeId, String summary, String missingSummary,
                               int resultCount, boolean craftable) {
        this.recipeId = safe(recipeId);
        this.summary = safe(summary);
        this.missingSummary = safe(missingSummary);
        this.resultCount = Math.max(1, resultCount);
        this.craftable = craftable;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
