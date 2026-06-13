package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Package-private data types shared across the crafting sub-package.
 *
 * <p>Each record carries a single piece of the craft-availability search,
 * the ingredient extraction plan, or the execution result.  None of these
 * types are exposed outside the {@code crafting} package.
 */
// ---- Ingredient counting & availability -----------------------------------

record AvailableCraftItem(ItemStack prototype, long count) {
}

record CraftIngredientPlan(ItemStack[] prototypes) {
    ItemStack prototypeAt(int slot) {
        if (slot < 0 || slot >= prototypes.length) {
            return ItemStack.EMPTY;
        }
        ItemStack prototype = prototypes[slot];
        return prototype == null ? ItemStack.EMPTY : prototype;
    }
}

record RecipeAvailability(boolean craftable, String missingSummary, int missingTotal) {
}

// ---- Craftable panel grouping ---------------------------------------------

record CraftableGroupEntry(CraftableCandidate primary, java.util.List<CraftableCandidate> options) {
    static int compareForPanel(CraftableGroupEntry a, CraftableGroupEntry b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return CraftableCandidate.compareForPanel(a.primary(), b.primary());
    }
}

record CraftableCandidate(
        String recipeId,
        String resultItemId,
        int resultCount,
        String resultLabel,
        boolean craftable,
        String missingSummary,
        int missingTotal,
        String recipeSummary) {

    private boolean isPreferredOver(CraftableCandidate other) {
        if (other == null) {
            return true;
        }
        if (this.craftable != other.craftable) {
            return this.craftable;
        }
        if (this.missingTotal != other.missingTotal) {
            return this.missingTotal < other.missingTotal;
        }
        if (this.resultCount != other.resultCount) {
            return this.resultCount > other.resultCount;
        }
        return this.recipeId.compareToIgnoreCase(other.recipeId) < 0;
    }

    static int compareForPanel(CraftableCandidate a, CraftableCandidate b) {
        if (a.craftable() != b.craftable()) {
            return a.craftable() ? -1 : 1;
        }
        int byLabel = a.resultLabel().compareToIgnoreCase(b.resultLabel());
        if (byLabel != 0) {
            return byLabel;
        }
        return a.recipeId().compareToIgnoreCase(b.recipeId());
    }

    static int compareForRecipeSelection(CraftableCandidate a, CraftableCandidate b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        if (a.isPreferredOver(b)) {
            return b.isPreferredOver(a) ? 0 : -1;
        }
        if (b.isPreferredOver(a)) {
            return 1;
        }
        return a.recipeId().compareToIgnoreCase(b.recipeId());
    }
}

// ---- Grid operations ------------------------------------------------------

record GridInsert(int slotIndex, ItemStack stack) {
}

// ---- Ingredient extraction during craft execution -------------------------

record ExtractedIngredient(ItemStack stack, boolean fromPlayer) {
}

// ---- Single-craft result ---------------------------------------------------

record CraftExecutionResult(
        boolean success,
        boolean storageFull,
        String resultItemId,
        int resultCount,
        Map<String, Integer> consumedCounts) {

    static CraftExecutionResult failure(boolean storageFull) {
        return new CraftExecutionResult(false, storageFull, "", 0, Map.of());
    }
}
