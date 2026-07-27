package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowStyle;
import net.minecraft.client.gui.FontRenderer;

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
            LegacyGuiGraphics graphics,
            FontRenderer font,
            MinecraftUiCanvas canvas,
            WorkflowWindowLayout.RowGeometry geometry,
            WorkflowUiRow row,
            int mouseX,
            int mouseY) {
        WorkflowChromeRenderer.renderRow(
                canvas,
                geometry,
                row,
                mouseX,
                mouseY);

        WorkflowStyle.RowVisual rowVisual = WorkflowStyle.row(
                row.suspended,
                row.protectedWorkflow,
                geometry.row.contains(mouseX, mouseY));
        graphics.drawString(
                font,
                WorkflowResumeRenderSupport.truncate(
                        row.label, font, (int) geometry.row.getWidth() - 8),
                (int) geometry.row.getX()
                        + WorkflowWindowLayout.LABEL_X,
                (int) geometry.row.getY()
                        + WorkflowWindowLayout.LABEL_Y,
                rowVisual.labelText.toArgb(),
                false);
        graphics.drawString(
                font,
                WorkflowResumeRenderSupport.truncate(
                        row.progressText, font, (int) geometry.progress.getWidth() - 4),
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
                        geometry.protect.contains(mouseX, mouseY))
                        .text.toArgb());
        drawCenteredGlyph(
                graphics,
                font,
                geometry.action,
                row.suspended || row.paused ? "▶" : "⏸",
                WorkflowStyle.action(
                        row.suspended,
                        row.paused,
                        geometry.action.contains(mouseX, mouseY))
                        .text.toArgb());
        drawCenteredGlyph(
                graphics,
                font,
                geometry.delete,
                "✖",
                WorkflowStyle.delete(
                        geometry.delete.contains(mouseX, mouseY))
                        .text.toArgb());
    }

    private static void drawCenteredGlyph(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            UiRect bounds,
            String glyph,
            int color) {
        graphics.drawCenteredString(
                font, glyph,
                (int) bounds.getX()
                        + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + 4,
                color);
    }
}
