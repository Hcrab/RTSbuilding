package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiPhase;
import com.rtsbuilding.rtsbuilding.uicore.ultimine.UltimineUiState;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiPhase;

/**
 * 世界交互的离屏边界提示。
 *
 * <p>只用二维方框表达生产中的绿色连锁轮廓与已确认进度，不冒充 Minecraft 方块模型、
 * 深度测试或 GPU 世界渲染；普通 UI 的窗口和文字仍由正式布局负责。</p>
 */
final class UiWorldInteractionBoundaryRenderer {
    void render(BufferedImageUiCanvas canvas, UiPreviewLayout layout, UiPreviewScenario scenario) {
        if (CullingPreviewFixtures.supports(scenario.variant())) {
            drawCulling(canvas, layout, CullingPreviewFixtures.forScenario(scenario));
        }
        if (!UltiminePreviewFixtures.supports(scenario.variant())) return;
        UltimineUiState state = UltiminePreviewFixtures.forScenario(scenario);
        double centerX = layout.screen().getWidth() * 0.64D;
        double centerY = layout.topBar().bottom()
                + (layout.bottomBar().getY() - layout.topBar().bottom()) * 0.47D;
        int columns = 7;
        int rows = 5;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if ((row == 0 && column < 2) || (row == rows - 1 && column > 4)) continue;
                double x = centerX + (column - 3) * 18 + row * 4;
                double y = centerY + (row - 2) * 15 - column * 2;
                UiRect block = new UiRect(x, y, 16, 14);
                canvas.fill(block, UiMainlinePreviewStyle.color(
                        state.phase == UltimineUiPhase.RUNNING ? 0x353FD36B : 0x2447E47A));
                canvas.stroke(block, UiMainlinePreviewStyle.color(0xFF58F287));
            }
        }
    }

    private void drawCulling(BufferedImageUiCanvas canvas, UiPreviewLayout layout,
                             CullingUiState state) {
        if (state.boxCount == 0 && state.phase == CullingUiPhase.IDLE) return;
        double x = layout.screen().getWidth() * 0.46D;
        double y = layout.topBar().bottom() + 92;
        double w = state.phase == CullingUiPhase.NEED_HEIGHT ? 180 : 210;
        double h = state.phase == CullingUiPhase.NEED_HEIGHT ? 112 : 132;
        UiRect box = new UiRect(x, y, w, h);
        canvas.fill(box, UiMainlinePreviewStyle.color(0x244D9BD6));
        canvas.stroke(box, UiMainlinePreviewStyle.color(0xFF8EC8FF));
        if (state.hasSelection()) {
            double cy = y + h / 2.0D;
            canvas.fill(new UiRect(x + w, cy - 2, 52, 4),
                    UiMainlinePreviewStyle.color(0xFFD95757));
            canvas.stroke(new UiRect(x + w + 45, cy - 7, 12, 14),
                    UiMainlinePreviewStyle.color(0xFFFF8A8A));
            canvas.fill(new UiRect(x + w / 2.0D - 2, y - 48, 4, 48),
                    UiMainlinePreviewStyle.color(0xFF65D477));
            canvas.fill(new UiRect(x - 42, y + h - 2, 42, 4),
                    UiMainlinePreviewStyle.color(0xFF5E86E8));
        }
    }
}
