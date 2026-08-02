package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityOption;
import com.rtsbuilding.rtsbuilding.uicore.craft.CraftQuantityState;

import java.util.Arrays;

/** 合成数量窗 preview-only 配方结果快照。 */
final class CraftQuantityPreviewFixtures {
    private CraftQuantityPreviewFixtures() {
    }

    static boolean supports(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.CRAFT_QUANTITY_READY
                || variant == UiPreviewScenario.Variant.CRAFT_QUANTITY_MISSING
                || variant == UiPreviewScenario.Variant.CRAFT_QUANTITY_MAX;
    }

    static CraftQuantityState forScenario(UiPreviewScenario scenario, int visibleRows) {
        boolean missing = scenario.variant() == UiPreviewScenario.Variant.CRAFT_QUANTITY_MISSING;
        int selected = missing ? 1 : 0;
        int quantity = scenario.variant() == UiPreviewScenario.Variant.CRAFT_QUANTITY_MAX ? 999 : 16;
        return new CraftQuantityState(true, "Area Destroy Plugin", "rtsbuilding:area_destroy_plugin", Arrays.asList(
                new CraftQuantityOption("minecraft:chest", "8 Oak Planks", "", 1, true),
                new CraftQuantityOption("minecraft:trapped_chest", "Iron latch",
                        "Missing 1 Iron Ingot", 1, false),
                new CraftQuantityOption("rtsbuilding:bulk_chest", "Bulk storage recipe", "", 4, true)),
                selected, 0, visibleRows, quantity, false);
    }
}
