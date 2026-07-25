package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowStyle;

/**
 * 工作流行、进度条和动作键的纯 Canvas chrome renderer。
 *
 * <p>本类不绘制文字或图标，不度量字体，也不执行暂停、恢复、保护或删除命令。
 * 平台层只需在共享 chrome 上补无阴影文字，生产和离屏便会保持相同框体、进度取整和
 * hover 状态。</p>
 */
public final class WorkflowChromeRenderer {
    private WorkflowChromeRenderer() {
    }

    /** 离屏或非交互调用使用无 hover 的稳定渲染。 */
    public static void renderRow(
            UiCanvas2D canvas,
            WorkflowWindowLayout.RowGeometry geometry,
            WorkflowUiRow row) {
        renderRow(
                canvas,
                geometry,
                row,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
    }

    public static void renderRow(
            UiCanvas2D canvas,
            WorkflowWindowLayout.RowGeometry geometry,
            WorkflowUiRow row,
            double mouseX,
            double mouseY) {
        if (canvas == null || geometry == null || row == null) {
            throw new IllegalArgumentException(
                    "canvas, geometry and row must not be null");
        }
        WorkflowStyle.RowVisual rowVisual = WorkflowStyle.row(
                row.suspended,
                row.protectedWorkflow,
                geometry.row.contains(mouseX, mouseY));
        frame(
                canvas,
                geometry.row,
                rowVisual.background,
                rowVisual.border,
                rowVisual.darkBorder);

        canvas.fill(geometry.progress, rowVisual.progressTrack);
        int fillWidth = progressFillWidth(
                (int) geometry.progress.getWidth(), row.completed, row.total);
        if (fillWidth > 0) {
            canvas.fill(
                    geometry.progress.getX(),
                    geometry.progress.getY(),
                    fillWidth,
                    geometry.progress.getHeight(),
                    rowVisual.progressFill);
        }
        border(
                canvas,
                geometry.progress,
                rowVisual.progressBorder,
                rowVisual.progressDarkBorder);

        button(
                canvas,
                geometry.protect,
                WorkflowStyle.protect(
                        row.protectedWorkflow,
                        geometry.protect.contains(mouseX, mouseY)));
        button(
                canvas,
                geometry.action,
                WorkflowStyle.action(
                        row.suspended,
                        row.paused,
                        geometry.action.contains(mouseX, mouseY)));
        button(
                canvas,
                geometry.delete,
                WorkflowStyle.delete(
                        geometry.delete.contains(mouseX, mouseY)));
    }

    public static int progressFillWidth(
            int width,
            int completed,
            int total) {
        if (width <= 0 || completed <= 0 || total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(
                width,
                (int) Math.round(width * Math.min(
                        1.0D, completed / (double) total))));
    }

    private static void button(
            UiCanvas2D canvas,
            UiRect bounds,
            WorkflowStyle.ButtonVisual visual) {
        frame(
                canvas,
                bounds,
                visual.background,
                visual.border,
                visual.darkBorder);
    }

    private static void frame(
            UiCanvas2D canvas,
            UiRect bounds,
            UiColor background,
            UiColor light,
            UiColor dark) {
        canvas.fill(bounds, background);
        border(canvas, bounds, light, dark);
    }

    /**
     * 保持主线旧 frame 的外边界：右/下线位于 x+width、y+height。
     */
    private static void border(
            UiCanvas2D canvas,
            UiRect bounds,
            UiColor light,
            UiColor dark) {
        canvas.fill(
                bounds.getX(),
                bounds.getY(),
                bounds.getWidth() + 1.0D,
                1.0D,
                light);
        canvas.fill(
                bounds.getX(),
                bounds.getY(),
                1.0D,
                bounds.getHeight() + 1.0D,
                light);
        canvas.fill(
                bounds.getX(),
                bounds.bottom(),
                bounds.getWidth() + 1.0D,
                1.0D,
                dark);
        canvas.fill(
                bounds.right(),
                bounds.getY(),
                1.0D,
                bounds.getHeight() + 1.0D,
                dark);
    }
}
