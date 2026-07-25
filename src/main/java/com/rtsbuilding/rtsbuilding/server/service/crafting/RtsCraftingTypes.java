package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * craft 子包内部共享的数据类型定义。
 *
 * <p>所有类型均为包私有 record，不暴露在 {@code crafting} 包之外。
 * 按用途分为以下几组：
 *
 * <ul>
 *   <li><b>材料统计与可用性</b>— {@link AvailableCraftItem}（可用物品及数量）、
 *   {@link CraftIngredientPlan}（槽位材料分配计划）、{@link RecipeAvailability}（可合成判定结果）</li>
 *   <li><b>可合成面板分组</b>— {@link CraftableGroupEntry}（同一产出物品的配方组）、
 *   {@link CraftableCandidate}（单个配方候选，包含可用性和材料摘要）</li>
 *   <li><b>网格操作</b>— {@link GridInsert}（指定槽位的物品插入指令）</li>
 *   <li><b>材料提取</b>— {@link ExtractedIngredient}（已提取的材料及来源标识）</li>
 *   <li><b>执行结果</b>— {@link CraftExecutionResult}（单次合成结果，含成功、存储满、消耗统计）</li>
 * </ul>
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
