package com.rtsbuilding.rtsbuilding.client.rendering;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.12.2 固定管线与私有 BufferBuilder 的关键生命周期约束。 */
class LegacyGlStateContractTest {
    private static final Path CLIENT_SOURCES = Path.of(
            "src", "main", "java", "com", "rtsbuilding", "rtsbuilding", "client");
    private static final Path RESTORER = CLIENT_SOURCES.resolve(
            Path.of("rendering", "util", "RtsGlStateRestorer.java"));
    private static final Path UPLOADER = CLIENT_SOURCES.resolve(
            Path.of("rendering", "util", "RtsOwnedBufferUploader.java"));
    private static final Path FLOATING_LAYER = CLIENT_SOURCES.resolve(
            Path.of("screen", "panel", "RtsFloatingWindowLayer.java"));
    private static final Path WINDOW_PANEL = CLIENT_SOURCES.resolve(
            Path.of("screen", "panel", "RtsWindowPanel.java"));
    private static final Path CANVAS = CLIENT_SOURCES.resolve(
            Path.of("screen", "canvas", "MinecraftUiCanvas.java"));
    private static final Path LEGACY_GRAPHICS = CLIENT_SOURCES.resolve(
            Path.of("input", "overlay", "LegacyGuiGraphics.java"));
    private static final Path BUILDER_SCREEN = CLIENT_SOURCES.resolve(
            Path.of("screen", "standalone", "BuilderScreen.java"));
    private static final Path BUILDER_RENDER = CLIENT_SOURCES.resolve(
            Path.of("screen", "standalone", "BuilderScreenRenderOwner.java"));

    @Test
    void rendererStateRestoresNeverBypassGlStateManagerCache() throws IOException {
        List<Path> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CLIENT_SOURCES.resolve("rendering"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.normalize().equals(RESTORER.normalize()))
                    .filter(path -> containsAny(path,
                            "GL11.glEnable(", "GL11.glDisable(", "GL11.glBindTexture(",
                            "GL11.glDepthMask(", "GL11.glBlendFunc(", "GL14.glBlendFuncSeparate("))
                    .forEach(offenders::add);
        }
        Path textureRenderer = CLIENT_SOURCES.resolve(Path.of("util", "RtsTextureRenderer.java"));
        if (containsAny(textureRenderer,
                "GL11.glBindTexture(", "GL14.glBlendFuncSeparate(")) {
            offenders.add(textureRenderer);
        }

        assertEquals(List.of(), offenders,
                "归还 GL 状态必须经过 GlStateManager，否则它的缓存会与真实 OpenGL 状态分裂");
    }

    @Test
    void ownedBufferUploaderFinishesBeforeUploading() throws IOException {
        String source = Files.readString(UPLOADER);
        int finish = source.indexOf("buffer.finishDrawing()");
        int draw = source.indexOf("UPLOADER.draw(buffer)");

        assertTrue(finish >= 0 && draw > finish,
                "1.12.2 WorldVertexBufferUploader 不会替私有缓冲调用 finishDrawing");
    }

    @Test
    void floatingWindowsStayInsideLegacyGuiDepthRange() throws IOException {
        String source = Files.readString(FLOATING_LAYER);

        assertTrue(source.contains("WINDOW_MAX_Z = 384.0F"));
        assertTrue(source.contains("Math.min(WINDOW_MAX_Z"));
        assertFalse(source.contains("WINDOW_Z_STRIDE = 400.0F"),
                "巨大的窗口深度间距会让置顶窗口越过 1.12.2 GUI 裁剪面");
    }

    @Test
    void floatingWindowClipsUseTheActiveRtsScale() throws IOException {
        String window = Files.readString(WINDOW_PANEL);
        String canvas = Files.readString(CANVAS);

        assertTrue(window.contains("this.screen.enableRtsScissor(g, x1, y1, x2, y2)"));
        assertTrue(canvas.contains("this.screen.enableRtsScissor("));
        assertFalse(window.contains("new ScaledResolution"),
                "浮窗内容裁剪不能绕过 RTS 固定缩放坐标系");
    }

    @Test
    void itemRenderingCannotDefineTheFollowingGuiState() throws IOException {
        String graphics = Files.readString(LEGACY_GRAPHICS);
        String owner = Files.readString(BUILDER_RENDER);

        assertTrue(graphics.contains("RtsGuiRenderState.beginItem()"));
        assertTrue(owner.contains("RtsGuiRenderState.beginFrame()"));
        assertFalse(graphics.contains("GlStateManager.enableDepth();"),
                "物品数量/空槽分支不能把深度状态泄漏给后续格子");
    }

    @Test
    void forgeTooltipsOnlyUseTheIsolatedLegacyAdapter() throws IOException {
        List<Path> directCalls = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CLIENT_SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.normalize().equals(LEGACY_GRAPHICS.normalize()))
                    .filter(path -> containsAny(path, "GuiUtils.drawHoveringText("))
                    .forEach(directCalls::add);
        }
        assertTrue(directCalls.isEmpty(),
                "Forge tooltip 不能绕过 GL 状态隔离适配器: " + directCalls);

        String graphics = Files.readString(LEGACY_GRAPHICS);
        assertTrue(graphics.contains("RtsGuiRenderState.preserveForExternalGuiCall()"));
        assertTrue(graphics.contains("GuiUtils.drawHoveringText"));
    }

    @Test
    void legacyMouseDragForwardsRealDeltas() throws IOException {
        String screen = Files.readString(BUILDER_SCREEN);

        assertTrue(screen.contains("mouseX - this.legacyDragLastX"));
        assertTrue(screen.contains("mouseY - this.legacyDragLastY"));
        assertFalse(screen.contains("mouseDragged(mouseX, mouseY, button, 0.0D, 0.0D)"),
                "1.12 mouseClickMove 没有现代 dragX/dragY 参数，必须自行计算差值");
    }

    private static boolean containsAny(Path path, String... tokens) {
        try {
            String source = Files.readString(path);
            for (String token : tokens) {
                if (source.contains(token)) return true;
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取客户端源码: " + path, exception);
        }
    }
}
