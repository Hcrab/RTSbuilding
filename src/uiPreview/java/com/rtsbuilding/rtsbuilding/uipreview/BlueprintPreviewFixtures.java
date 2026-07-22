package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintInt3;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintUiState;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import java.util.Arrays;

/**
 * 离屏蓝图场景的确定性生产状态夹具。
 * 这里仅提供数据，不另造预览专用交互逻辑；窗口仍直接消费 Core 的正式状态对象。
 */
final class BlueprintPreviewFixtures {
    private BlueprintPreviewFixtures() {
    }

    static BlueprintUiState forScenario(UiPreviewScenario scenario) {
        BlueprintUiState.Mode mode = BlueprintUiState.Mode.PLACEMENT_PINNED;
        BlueprintInt3 anchor = new BlueprintInt3(128, 72, -34);
        BlueprintInt3 pointA = null;
        BlueprintInt3 pointB = null;
        BlueprintInt3 size = new BlueprintInt3(32, 18, 24);
        long blocks = 4386L;
        String status = "ready";
        boolean materialOpen = false;
        boolean nameOpen = false;
        boolean captureName = false;
        boolean replaceOnType = false;
        String nameDraft = "";
        BlueprintMaterialUiState materials = BlueprintMaterialUiState.EMPTY;
        if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_WAITING) {
            mode = BlueprintUiState.Mode.CAPTURE_WAITING_SECOND;
            anchor = null;
            pointA = new BlueprintInt3(112, 64, -42);
            size = new BlueprintInt3(0, 0, 0);
            blocks = 0L;
            status = "waiting second point";
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_READY) {
            mode = BlueprintUiState.Mode.CAPTURE_READY;
            anchor = null;
            pointA = new BlueprintInt3(112, 64, -42);
            pointB = new BlueprintInt3(143, 81, -19);
            status = "capture ready";
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_CAPTURE_SAVING) {
            mode = BlueprintUiState.Mode.CAPTURE_SAVING;
            anchor = null;
            pointA = new BlueprintInt3(112, 64, -42);
            pointB = new BlueprintInt3(143, 81, -19);
            status = "73% · 3200 / 4386";
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS) {
            materialOpen = true;
            materials = new BlueprintMaterialUiState("Harbour Workshop", 73, 3200, 4386,
                    3, 1, 1, Arrays.asList(
                    row("minecraft:oak_planks", "橡木木板", "384 / 512", 0xFFFFC06C),
                    row("minecraft:stone_bricks", "石砖", "1024 / 1024", 0xFF8EEA9B),
                    row("minecraft:glass", "玻璃", "48 / 192", 0xFFFFC06C),
                    row("minecraft:water_bucket", "水桶", "2 / 2", 0xFF8EEA9B),
                    row("", "需要模组：create", "需要安装该模组", 0xFFFF9E88),
                    row("", "create:framed_glass", "缺失 x64", 0xFFFF9E88)));
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_READY) {
            materialOpen = true;
            materials = new BlueprintMaterialUiState("Harbour Workshop", 100, 4386, 4386,
                    0, 0, 0, Arrays.asList(
                    row("minecraft:oak_planks", "Oak Planks", "512 / 512", 0xFF8EEA9B),
                    row("minecraft:stone_bricks", "Stone Bricks", "1024 / 1024", 0xFF8EEA9B),
                    row("minecraft:glass", "Glass", "192 / 192", 0xFF8EEA9B),
                    row("minecraft:water_bucket", "Water Bucket", "2 / 2", 0xFF8EEA9B)));
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_MATERIALS_EMPTY) {
            materialOpen = true;
            materials = new BlueprintMaterialUiState("Marker Blueprint", 100, 0, 0,
                    0, 0, 0, java.util.Collections.<BlueprintMaterialUiState.Row>emptyList());
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_NAME_CAPTURE) {
            mode = BlueprintUiState.Mode.CAPTURE_READY;
            anchor = null;
            pointA = new BlueprintInt3(112, 64, -42);
            pointB = new BlueprintInt3(143, 81, -19);
            nameOpen = true;
            captureName = true;
            nameDraft = "captured_harbour_workshop";
        } else if (scenario.variant() == UiPreviewScenario.Variant.BLUEPRINT_NAME_RENAME) {
            nameOpen = true;
            replaceOnType = true;
            nameDraft = "Harbour Workshop";
        }
        return new BlueprintUiState(mode, "Harbour Workshop", "32x18x24", 6,
                Math.max(7, scenario.blueprintCount()), size, pointA, pointB, blocks, status, 0xFFB7CDE2, anchor,
                1, 0, 0, materialOpen, 0, nameOpen, captureName, nameDraft,
                replaceOnType, materials);
    }

    private static BlueprintMaterialUiState.Row row(String icon, String label, String detail, int color) {
        return new BlueprintMaterialUiState.Row(icon, label, detail, color);
    }
}
