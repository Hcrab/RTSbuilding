package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildHintContractTest {
    @Test
    void buildHintExplainsRightClickLockBeforeEnterConfirmation() throws IOException {
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        JsonObject zhCn = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/rtsbuilding/lang/zh_cn.json"))).getAsJsonObject();

        assertTrue(panel.contains("Component.translatable(\"screen.rtsbuilding.quick_build.build_hint\")"));
        assertFalse(panel.contains("quick_build.build_hint\", confirmKeyLabel"));
        assertEquals("预览满意的话，按右键锁定，然后 Enter 建造。",
                zhCn.get("screen.rtsbuilding.quick_build.build_hint").getAsString());
    }
}
