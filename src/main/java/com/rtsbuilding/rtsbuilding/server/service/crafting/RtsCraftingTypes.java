package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Java 8 下供 crafting 包共享的数据对象。访问器沿用 record 时代的名称。 */
final class AvailableCraftItem {
    private final ItemStack prototype;
    private final long count;

    AvailableCraftItem(ItemStack prototype, long count) {
        this.prototype = prototype == null ? ItemStack.EMPTY : prototype;
        this.count = count;
    }

    ItemStack prototype() { return prototype; }
    long count() { return count; }
}

final class CraftIngredientPlan {
    private final ItemStack[] prototypes;

    CraftIngredientPlan(ItemStack[] prototypes) {
        this.prototypes = prototypes == null ? new ItemStack[0] : prototypes.clone();
    }

    ItemStack prototypeAt(int slot) {
        if (slot < 0 || slot >= prototypes.length) return ItemStack.EMPTY;
        ItemStack prototype = prototypes[slot];
        return prototype == null ? ItemStack.EMPTY : prototype;
    }
}

final class RecipeAvailability {
    private final boolean craftable;
    private final String missingSummary;
    private final int missingTotal;

    RecipeAvailability(boolean craftable, String missingSummary, int missingTotal) {
        this.craftable = craftable;
        this.missingSummary = missingSummary == null ? "" : missingSummary;
        this.missingTotal = missingTotal;
    }

    boolean craftable() { return craftable; }
    String missingSummary() { return missingSummary; }
    int missingTotal() { return missingTotal; }
}

final class CraftableGroupEntry {
    private final CraftableCandidate primary;
    private final List<CraftableCandidate> options;

    CraftableGroupEntry(CraftableCandidate primary, List<CraftableCandidate> options) {
        this.primary = primary;
        this.options = options == null ? Collections.<CraftableCandidate>emptyList() : options;
    }

    CraftableCandidate primary() { return primary; }
    List<CraftableCandidate> options() { return options; }

    static int compareForPanel(CraftableGroupEntry a, CraftableGroupEntry b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return CraftableCandidate.compareForPanel(a.primary, b.primary);
    }
}

final class CraftableCandidate {
    private final String recipeId;
    private final String resultItemId;
    private final int resultCount;
    private final String resultLabel;
    private final boolean craftable;
    private final String missingSummary;
    private final int missingTotal;
    private final String recipeSummary;

    CraftableCandidate(String recipeId, String resultItemId, int resultCount, String resultLabel,
            boolean craftable, String missingSummary, int missingTotal, String recipeSummary) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.resultItemId = resultItemId == null ? "" : resultItemId;
        this.resultCount = resultCount;
        this.resultLabel = resultLabel == null ? "" : resultLabel;
        this.craftable = craftable;
        this.missingSummary = missingSummary == null ? "" : missingSummary;
        this.missingTotal = missingTotal;
        this.recipeSummary = recipeSummary == null ? "" : recipeSummary;
    }

    String recipeId() { return recipeId; }
    String resultItemId() { return resultItemId; }
    int resultCount() { return resultCount; }
    String resultLabel() { return resultLabel; }
    boolean craftable() { return craftable; }
    String missingSummary() { return missingSummary; }
    int missingTotal() { return missingTotal; }
    String recipeSummary() { return recipeSummary; }

    private boolean isPreferredOver(CraftableCandidate other) {
        if (other == null) return true;
        if (craftable != other.craftable) return craftable;
        if (missingTotal != other.missingTotal) return missingTotal < other.missingTotal;
        if (resultCount != other.resultCount) return resultCount > other.resultCount;
        return recipeId.compareToIgnoreCase(other.recipeId) < 0;
    }

    static int compareForPanel(CraftableCandidate a, CraftableCandidate b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (a.craftable != b.craftable) return a.craftable ? -1 : 1;
        int label = a.resultLabel.compareToIgnoreCase(b.resultLabel);
        return label != 0 ? label : a.recipeId.compareToIgnoreCase(b.recipeId);
    }

    static int compareForRecipeSelection(CraftableCandidate a, CraftableCandidate b) {
        if (a == b) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (a.isPreferredOver(b)) return b.isPreferredOver(a) ? 0 : -1;
        if (b.isPreferredOver(a)) return 1;
        return a.recipeId.compareToIgnoreCase(b.recipeId);
    }
}

final class GridInsert {
    private final int slotIndex;
    private final ItemStack stack;
    GridInsert(int slotIndex, ItemStack stack) { this.slotIndex = slotIndex; this.stack = stack; }
    int slotIndex() { return slotIndex; }
    ItemStack stack() { return stack; }
}

final class ExtractedIngredient {
    private final ItemStack stack;
    private final boolean fromPlayer;
    ExtractedIngredient(ItemStack stack, boolean fromPlayer) { this.stack = stack; this.fromPlayer = fromPlayer; }
    ItemStack stack() { return stack; }
    boolean fromPlayer() { return fromPlayer; }
}

final class CraftExecutionResult {
    private final boolean success;
    private final boolean storageFull;
    private final String resultItemId;
    private final int resultCount;
    private final Map<String, Integer> consumedCounts;

    CraftExecutionResult(boolean success, boolean storageFull, String resultItemId, int resultCount,
            Map<String, Integer> consumedCounts) {
        this.success = success;
        this.storageFull = storageFull;
        this.resultItemId = resultItemId == null ? "" : resultItemId;
        this.resultCount = resultCount;
        this.consumedCounts = consumedCounts == null ? Collections.<String, Integer>emptyMap() : consumedCounts;
    }

    boolean success() { return success; }
    boolean storageFull() { return storageFull; }
    String resultItemId() { return resultItemId; }
    int resultCount() { return resultCount; }
    Map<String, Integer> consumedCounts() { return consumedCounts; }

    static CraftExecutionResult failure(boolean storageFull) {
        return new CraftExecutionResult(false, storageFull, "", 0, Collections.<String, Integer>emptyMap());
    }
}
