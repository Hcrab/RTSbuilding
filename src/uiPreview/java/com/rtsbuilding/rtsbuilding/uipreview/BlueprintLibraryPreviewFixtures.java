package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 蓝图空间离屏场景的真实 Core 列表夹具。 */
final class BlueprintLibraryPreviewFixtures {
    private BlueprintLibraryPreviewFixtures() {
    }

    static BlueprintLibraryUiState forScenario(UiPreviewScenario scenario) {
        List<BlueprintLibraryUiEntry> entries = new ArrayList<BlueprintLibraryUiEntry>();
        int harbourPercent = scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_READY
                ? 100 : 73;
        entries.add(entry("harbour_workshop.nbt", "Harbour Workshop", "NBT", "32x18x24",
                4386, harbourPercent));
        entries.add(entry("windmill.schem", "Windmill", "SCHEM", "17x32x17", 2830, 100));
        entries.add(entry("stone_bridge.nbt", "Stone Bridge", "NBT", "48x12x9", 1912, 92));
        entries.add(entry("farm_house.litematic", "Farm House", "LITEMATIC", "24x16x28", 3096, 54));
        entries.add(entry("warehouse.schematic", "Warehouse", "SCHEMATIC", "40x20x34", 7214, 88));
        entries.add(new BlueprintLibraryUiEntry("broken_factory.schem", "Broken Factory", "SCHEM",
                "-", 0, 0, "", "Palette index 17 is missing",
                Collections.<String>emptyList()));
        String query = "";
        String selected = "harbour_workshop.nbt";
        boolean locked = isCaptureScenario(scenario.variant());
        boolean saving = scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING;
        String status = "Blueprint space ready.";
        int statusColor = 0xFFB8C7D6;
        if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY_EMPTY_SEARCH) {
            query = "no-such-blueprint";
            selected = "";
            status = "No matching blueprints.";
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY_PARSE_ERROR) {
            selected = "broken_factory.schem";
            status = "Could not read blueprint: Palette index 17 is missing";
            statusColor = 0xFFFF8A8A;
        } else if (locked) {
            status = saving ? "Blueprint save is still running." : "Capture is active.";
            statusColor = 0xFFFFC06C;
        }
        return new BlueprintLibraryUiState(entries, query,
                scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY_EMPTY_SEARCH,
                0, selected, locked, saving, status, statusColor);
    }

    static boolean isBlueprintScenario(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.BLUEPRINT_STATES
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING
                || variant == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS
                || variant == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_READY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_EMPTY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_NAME_CAPTURE
                || variant == UiPreviewScenario.Variant.BLUEPRINT_NAME_RENAME
                || variant == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY_EMPTY_SEARCH
                || variant == UiPreviewScenario.Variant.BLUEPRINT_LIBRARY_PARSE_ERROR;
    }

    private static boolean isCaptureScenario(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY
                || variant == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING
                || variant == UiPreviewScenario.Variant.BLUEPRINT_NAME_CAPTURE;
    }

    private static BlueprintLibraryUiEntry entry(String file, String name, String format,
                                                 String size, int blocks, int percent) {
        return new BlueprintLibraryUiEntry(file, name, format, size, blocks, percent,
                percent + "% buildable", "", Collections.<String>emptyList());
    }
}
