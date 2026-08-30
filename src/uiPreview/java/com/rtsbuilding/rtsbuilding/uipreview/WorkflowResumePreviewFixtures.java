package com.rtsbuilding.rtsbuilding.uipreview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 两类工作流恢复窗口的 preview-only 有界扫描结果。 */
final class WorkflowResumePreviewFixtures {
    private WorkflowResumePreviewFixtures() {
    }

    static boolean supportsPlacement(UiPreviewScenario.Variant variant) {
        return variant
                == UiPreviewScenario.Variant.RESUME_PLACEMENT_CONFLICTS
                || variant
                == UiPreviewScenario.Variant.RESUME_PLACEMENT_DISABLED;
    }

    static boolean supportsBlueprint(UiPreviewScenario.Variant variant) {
        return variant
                == UiPreviewScenario.Variant.RESUME_BLUEPRINT_MISSING
                || variant
                == UiPreviewScenario.Variant.RESUME_BLUEPRINT_READY;
    }

    static Placement placement(
            UiPreviewScenario scenario,
            UiMainlineAssets assets) {
        boolean disabled = scenario.variant()
                == UiPreviewScenario.Variant.RESUME_PLACEMENT_DISABLED;
        return new Placement(
                assets.itemNames().get(0),
                disabled ? "Remote Storage" : "Building Chest",
                384,
                96,
                disabled ? 0 : 27,
                disabled ? 112 : 480,
                disabled ? 140 : 480,
                disabled ? 28 : 0);
    }

    static Blueprint blueprint(
            UiPreviewScenario scenario,
            UiMainlineAssets assets) {
        boolean ready = scenario.variant()
                == UiPreviewScenario.Variant.RESUME_BLUEPRINT_READY;
        List<String> names = assets.itemNames();
        List<Material> rows = new ArrayList<Material>();
        for (int index = 0; index < 8; index++) {
            String assetName = names.get(index % names.size());
            long available = ready
                    ? 64L + index * 8L
                    : (index % 3 == 0 ? 4L : 64L + index * 8L);
            int required = 24 + index * 5;
            rows.add(new Material(
                    assetName,
                    "Material " + (1993 + index),
                    required,
                    available));
        }
        return new Blueprint(
                1992,
                2000,
                2000,
                1992,
                rows);
    }

    static final class Placement {
        final String assetName;
        final String itemLabel;
        final int remaining;
        final int alreadyPlaced;
        final int conflicts;
        final long available;
        final int needed;
        final long missing;

        Placement(
                String assetName,
                String itemLabel,
                int remaining,
                int alreadyPlaced,
                int conflicts,
                long available,
                int needed,
                long missing) {
            this.assetName = assetName;
            this.itemLabel = itemLabel;
            this.remaining = remaining;
            this.alreadyPlaced = alreadyPlaced;
            this.conflicts = conflicts;
            this.available = available;
            this.needed = needed;
            this.missing = missing;
        }

        boolean hasConflicts() {
            return conflicts > 0;
        }

        boolean enough() {
            return missing <= 0L;
        }
    }

    static final class Blueprint {
        final int completed;
        final int total;
        final int totalRows;
        final int scroll;
        final List<Material> visibleRows;

        Blueprint(
                int completed,
                int total,
                int totalRows,
                int scroll,
                List<Material> visibleRows) {
            this.completed = completed;
            this.total = total;
            this.totalRows = totalRows;
            this.scroll = scroll;
            this.visibleRows = Collections.unmodifiableList(
                    new ArrayList<Material>(visibleRows));
        }

        boolean enough() {
            for (Material material : visibleRows) {
                if (!material.enough()) {
                    return false;
                }
            }
            return true;
        }
    }

    static final class Material {
        final String assetName;
        final String label;
        final int required;
        final long available;

        Material(
                String assetName,
                String label,
                int required,
                long available) {
            this.assetName = assetName;
            this.label = label;
            this.required = required;
            this.available = available;
        }

        boolean enough() {
            return available >= required;
        }

        long missing() {
            return Math.max(0L, required - available);
        }
    }
}
