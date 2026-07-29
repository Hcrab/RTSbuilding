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
        String renderer = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildStatusRenderer.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildUiAdapter.java"));
        JsonObject zhCn = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"))).getAsJsonObject();

        assertTrue(panel.contains("QuickBuildStatusRenderer.render("));
        assertTrue(renderer.contains("Component.translatable(state.hintKey, state.confirmKeyLabel)"));
        assertTrue(adapter.contains("Config.isKeyboardBatchConfirmEnabled()"));
        assertTrue(adapter.contains("\"screen.rtsbuilding.quick_build.build_hint_auto\""));
        assertEquals("预览满意的话，按右键锁定，然后按 %s 建造。",
                zhCn.get("screen.rtsbuilding.quick_build.build_hint").getAsString());
        assertEquals("右键设置点位；最后一个点会直接建造。",
                zhCn.get("screen.rtsbuilding.quick_build.build_hint_auto").getAsString());
    }

    @Test
    void buildAndDestroyUseTheSameAutoSubmitPolicyAfterSelection() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/shape/ShapeSelectionSession.java"));

        assertEquals(2, occurrences(controller, "if (shouldSubmitShapeAfterSelection())"),
                "范围建造和范围破坏都必须在最后一个选点后经过同一自动提交策略");
        assertTrue(controller.contains("this.selectionSession.shouldSubmitAfterSelection("));
        assertTrue(session.contains("ShapeConfirmationPolicy.shouldSubmitAfterSelection("));
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
