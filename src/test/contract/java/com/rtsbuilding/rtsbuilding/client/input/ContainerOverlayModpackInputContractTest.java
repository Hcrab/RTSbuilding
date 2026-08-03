package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.12 大型整合包中 overlay 对已取消键鼠事件的输入所有权。 */
class ContainerOverlayModpackInputContractTest {
    private static final Path EVENTS = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputEvents1122.java");

    @Test
    void overlayReceivesInputBeforeAndAfterInventoryTweaksCancellation() throws IOException {
        String source = Files.readString(EVENTS, StandardCharsets.UTF_8);

        assertTrue(source.contains(
                "@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)\n"
                        + "    public static void onKeyboardInput"));
        assertTrue(source.contains(
                "@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)\n"
                        + "    public static void onMouseInput"));
    }
}
