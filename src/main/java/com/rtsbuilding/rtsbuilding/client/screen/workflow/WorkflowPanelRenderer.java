package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 工作流共享 chrome 到 Minecraft 字体与字形的薄适配层。
 *
 * <p>本类只补齐需要字体度量的无阴影文本和紧凑动作字形，不拥有工作流状态，不处理点击，
 * 也不发送网络命令。框体、进度条、状态色和 hover 均由 Kit 统一决定。</p>
 */
final class WorkflowPanelRenderer {
    private WorkflowPanelRenderer() {
    }

    static void renderRow(
            GuiGraphics graphics,
            Font font,
            MinecraftUiCanvas canvas,
            WorkflowWindowLayout.RowGeometry geometry,
            WorkflowUiRow row,
            double rowHover,
            double protectHover,
            double actionHover,
            double deleteHover) {
        WorkflowChromeRenderer.renderRow(
                canvas,
                geometry,
                row,
                rowHover,
                protectHover,
                actionHover,
                deleteHover);

        WorkflowStyle.RowVisual rowVisual = WorkflowStyle.row(
                row.suspended,
                row.protectedWorkflow,
                rowHover);
        graphics.drawString(
                font,
                RtsClientUiUtil.trimToWidth(
                        font,
                        row.label,
                        (int) geometry.row.getWidth() - 8),
                (int) geometry.row.getX()
                        + WorkflowWindowLayout.LABEL_X,
                (int) geometry.row.getY()
                        + WorkflowWindowLayout.LABEL_Y,
                rowVisual.labelText.toArgb(),
                false);
        graphics.drawString(
                font,
                RtsClientUiUtil.trimToWidth(
                        font,
                        row.progressText,
                        (int) geometry.progress.getWidth() - 4),
                (int) geometry.progress.getX()
                        + WorkflowWindowLayout.PROGRESS_TEXT_X,
                (int) geometry.progress.getY()
                        + WorkflowWindowLayout.PROGRESS_TEXT_Y,
                rowVisual.progressText.toArgb(),
                false);

        drawCenteredGlyph(
                graphics,
                font,
                geometry.protect,
                row.protectedWorkflow ? "◆" : "◇",
                WorkflowStyle.protect(
                        row.protectedWorkflow,
                        protectHover)
                        .text.toArgb());
        drawCenteredGlyph(
                graphics,
                font,
                geometry.action,
                row.suspended || row.paused ? "▶" : "⏸",
                WorkflowStyle.action(
                        row.suspended,
                        row.paused,
                        actionHover)
                        .text.toArgb());
        drawCenteredGlyph(
                graphics,
                font,
                geometry.delete,
                "✖",
                WorkflowStyle.delete(deleteHover)
                        .text.toArgb());
    }

    private static void drawCenteredGlyph(
            GuiGraphics graphics,
            Font font,
            UiRect bounds,
            String glyph,
            int color) {
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                glyph,
                (int) bounds.getX()
                        + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + 4,
                color);
    }
}
