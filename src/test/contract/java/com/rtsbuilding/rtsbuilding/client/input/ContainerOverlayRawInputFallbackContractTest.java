package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 MM 一类整合包绕过 Forge 聚合事件时，overlay 仍拥有原始 GuiScreen 输入入口。 */
class ContainerOverlayRawInputFallbackContractTest {
    @Test
    void rawGuiHooksReuseTheSameOwnerAndCancelOnlyConsumedInput() throws IOException {
        String mixin = read("src/main/java/com/rtsbuilding/rtsbuilding/mixin/GuiScreenOverlayInputMixin.java");
        String events = read("src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputEvents1122.java");
        String config = read("src/main/resources/mixins.rtsbuilding.json");

        assertTrue(mixin.contains("method = \"handleMouseInput\"")
                && mixin.contains("method = \"handleKeyboardInput\""));
        assertTrue(mixin.contains("routeCurrentMouseInput")
                && mixin.contains("routeCurrentKeyboardInput")
                && mixin.contains("ci.cancel()"));
        assertTrue(events.contains("routeCurrentMouseInput(event.getGui(), \"FORGE_PRE\")")
                && events.contains("routeCurrentKeyboardInput(event.getGui(), \"FORGE_PRE\")"));
        assertTrue(config.contains("GuiScreenOverlayInputMixin"));
    }

    @Test
    void consumedInputLeavesAnActionableLatestLogBreadcrumb() throws IOException {
        String adapter = read("src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsRawGuiInputAdapter.java");
        assertTrue(adapter.contains("[RTS-OVERLAY] side=C event=POINTER_CONSUMED"));
        assertTrue(adapter.contains("[RTS-OVERLAY] side=C event=KEY_CONSUMED"));
        assertTrue(adapter.contains("source={} screen={}"));
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }
}
