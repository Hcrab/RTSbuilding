package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildHintContractTest {
    @Test
    void buildHintTracksKeyboardFinalConfirmSetting() throws IOException {
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildUiAdapter.java"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        JsonObject zhCn = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"))).getAsJsonObject();

        assertTrue(panel.contains("Component.translatable(core.hintKey, core.confirmKeyLabel)"));
        assertTrue(adapter.contains("Config.isKeyboardBatchConfirmEnabled()"));
        assertTrue(adapter.contains("\"screen.rtsbuilding.quick_build.build_hint_auto\""));
        assertEquals(2, occurrences(controller, "if (shouldSubmitShapeAfterSelection())"),
                "范围建造和范围破坏都必须使用同一自动提交策略");
        assertTrue(controller.contains("!Config.isKeyboardBatchConfirmEnabled()"));
        assertEquals("预览满意的话，按右键锁定，然后按 %s 建造。",
                zhCn.get("screen.rtsbuilding.quick_build.build_hint").getAsString());
        assertEquals("右键设置点位；最后一个点会直接建造。",
                zhCn.get("screen.rtsbuilding.quick_build.build_hint_auto").getAsString());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
