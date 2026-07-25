package com.rtsbuilding.rtsbuilding.client.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 UI 动画与物品栏入口配置在 Forge 生产路径和四语言资源中的完整契约。 */
class UiClientConfigContractTest {
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "zh_tw", "zh_hk");

    @Test
    void uiAnimationAndInventoryButtonSettingsDefaultOnAndReachProductionConsumers() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/Config.java"));
        String topBar = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java"));
        String bottomPanel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java"));
        String inventoryEvents = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/plugin/RtsPluginInventoryScreenEvents.java"));

        assertTrue(config.contains("ENABLE_UI_ANIMATIONS = BUILDER"));
        assertTrue(config.contains(".define(\"enableUiAnimations\", true)"));
        assertTrue(config.contains("return ENABLE_UI_ANIMATIONS.get()"));
        assertTrue(config.contains("ENABLE_UI_ANIMATIONS.set(enabled)"));
        assertTrue(topBar.contains("Config.isUiAnimationsEnabled()"));
        assertTrue(bottomPanel.contains("Config.isUiAnimationsEnabled()"));

        assertTrue(config.contains("SHOW_INVENTORY_RTS_BUTTON = BUILDER"));
        assertTrue(config.contains(".define(\"showInventoryRtsButton\", true)"));
        assertTrue(config.contains("return SHOW_INVENTORY_RTS_BUTTON.get()"));
        assertTrue(config.contains("SHOW_INVENTORY_RTS_BUTTON.set(enabled)"));
        assertTrue(inventoryEvents.contains("Config.isInventoryRtsButtonEnabled()"));
    }

    @Test
    void fourLanguagesHaveEqualKeySetsAndBothConfigLabels() throws Exception {
        Set<String> baseline = null;
        for (String language : LANGUAGES) {
            Path file = Path.of("src/main/resources/assets/rtsbuilding/lang", language + ".json");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            Set<String> keys = json.keySet();
            if (baseline == null) {
                baseline = Set.copyOf(keys);
            } else {
                assertEquals(baseline, keys, language + " 的 UI 翻译键必须与英文基线完全一致");
            }
            assertTrue(keys.contains("rtsbuilding.configuration.enableUiAnimations"));
            assertTrue(keys.contains("rtsbuilding.configuration.enableUiAnimations.tooltip"));
            assertTrue(keys.contains("rtsbuilding.configuration.showInventoryRtsButton"));
            assertTrue(keys.contains("rtsbuilding.configuration.showInventoryRtsButton.tooltip"));
        }
        assertTrue(baseline != null && baseline.size() >= 900, "四语言基线不能被截断");
    }
}
