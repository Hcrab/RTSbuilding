package com.rtsbuilding.rtsbuilding.uicore.routing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** 静态锁定首批生产适配边界，避免后续维护悄悄恢复广播式输入。 */
class UiProductionAdapterContractTest {
    @Test
    void 浮动窗口层使用Core的按键独立捕获() throws IOException {
        String source = floatingLayerSource();
        assertTrue(source.contains("PointerCapture<RtsWindowPanel> pointerCapture"));
        assertTrue(source.contains("this.pointerCapture.capture(button, window)"));
    }

    @Test
    void 捕获期间拖动和释放始终阻断世界() throws IOException {
        String source = floatingLayerSource();
        assertTrue(source.contains("captured.mouseDragged(mouseX, mouseY, button, dragX, dragY);"));
        assertTrue(source.contains("captured.mouseReleased(mouseX, mouseY, button);"));
        assertTrue(source.contains("this.pointerCapture.release(button);"));
    }

    @Test
    void z顺序不再依赖时钟粒度或先渲染一次() throws IOException {
        String window = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String layer = floatingLayerSource();
        assertTrue(window.contains("AtomicLong NEXT_Z_ORDER"));
        assertFalse(window.contains("System.nanoTime()"));
        assertTrue(layer.contains("private void ensureZOrder()"));
        assertTrue(layer.contains("public boolean mouseClicked") && layer.contains("ensureZOrder();"));
    }

    private static String floatingLayerSource() throws IOException {
        return read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsFloatingWindowLayer.java");
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
