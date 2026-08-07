package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 Quick Build 首次打开的两阶段生命周期，避免构造阶段再次读取未就绪布局。
 * 这是源码契约测试；真实 GUI 尺寸、渐显帧和拖拽位置仍需进游戏人工确认。
 */
class QuickBuildInitialOpenContractTest {
    @Test
    void initialOpenOnlyRecordsLogicalStateDuringPanelInitialization() throws IOException {
        String quickBuild = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        String init = methodBody(quickBuild, "public void init(BuilderScreen screen");

        assertTrue(init.contains("requestInitialOpen()"),
                "Quick Build 初始化应只登记逻辑打开并排队首次揭示");
        assertFalse(init.contains("setOpen(true)"),
                "构造阶段不能通过 setOpen(true) 触发依赖完整布局的定位");
        assertFalse(init.contains("computeDefaultPosition"),
                "构造阶段不能计算默认位置");
    }

    @Test
    void pendingRevealWaitsForLayoutAndStartsExactlyOnce() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String render = methodBody(panel, "public void render(LegacyGuiGraphics g");
        String pending = methodBody(panel, "private boolean tryStartPendingReveal");

        assertTrue(render.indexOf("tryStartPendingReveal()") < render.indexOf("shouldRenderWindow()"),
                "首次揭示必须在可见性判断前消费，避免错误位置闪现");
        assertTrue(panel.contains("!this.pendingInitialReveal && !this.pendingReveal"),
                "布局未就绪时窗口不能参与悬停、输入或覆盖层渲染");
        assertTrue(pending.contains("if (!isLayoutReady())")
                        && pending.contains("initializePosition()")
                        && pending.contains("visibilityAnimation.reveal"),
                "布局未就绪时应等待，就绪后才定位并启动渐显");
        assertTrue(pending.indexOf("initializePosition()") < pending.indexOf("visibilityAnimation.reveal"),
                "首次渐显必须发生在真实位置初始化之后");
        assertTrue(pending.contains("this.pendingInitialReveal = false")
                        && pending.contains("this.pendingReveal = false"),
                "待揭示标记必须在成功揭示后消费，不能每帧重复启动");
    }

    @Test
    void closingBeforeFirstRevealCancelsPendingStateAndManualOpenKeepsNormalAnimation() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String setOpen = methodBody(panel, "public void setOpen(boolean open)");
        String pending = methodBody(panel, "private boolean tryStartPendingReveal");

        assertTrue(setOpen.contains("if (!open)")
                        && setOpen.contains("this.pendingInitialReveal = false")
                        && setOpen.contains("this.pendingReveal = false"),
                "首次安全渲染前关闭必须取消两种待揭示状态");
        assertTrue(setOpen.contains("boolean hadPendingReveal")
                        && setOpen.contains("if (!hadPendingReveal)"),
                "尚未揭示的窗口关闭时不能伪造一次淡出帧");
        assertTrue(pending.contains("if (!this.open)")
                        && pending.contains("this.pendingInitialReveal = false")
                        && pending.contains("this.pendingReveal = false"),
                "待揭示消费时必须识别已经关闭的窗口，避免幽灵式重新出现");
        assertTrue(setOpen.contains("if (isLayoutReady())")
                        && setOpen.contains("visibilityAnimation.reveal")
                        && setOpen.contains("visibilityAnimation.dismiss"),
                "后续手动关闭/打开仍应走原有渐显和淡出状态机");
    }

    @Test
    void layoutReadinessIncludesRealScreenSizeAndBottomPanelBinding() throws IOException {
        String builder = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String bottom = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(builder.contains("this.width > 0 && this.height > 0")
                        && builder.contains("this.bottomPanel.isLayoutReady()"),
                "窗口定位必须等待真实 GUI 尺寸和 BottomPanel 绑定完成");
        assertTrue(bottom.contains("public boolean isLayoutReady()")
                        && bottom.contains("this.screen != null && this.controller != null"),
                "BottomPanel 应提供明确的布局生命周期状态");
    }

    @Test
    void storedBoundsRemainAuthoritative() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String setBounds = methodBody(panel, "public void setBounds(int x");
        String initialize = methodBody(panel, "private void initializePosition");

        assertTrue(setBounds.contains("this.positionInitialized = true"),
                "恢复或拖动后的 bounds 必须标记为已初始化");
        assertTrue(initialize.contains("if (!this.positionInitialized)")
                        && initialize.contains("initializeDefaultBounds()"),
                "已有 bounds 时首次 reveal 不得覆盖默认位置");
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
