package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.tooltip.UiTooltipPlacement;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 工作流正文详情的有界 tooltip 适配层。
 *
 * <p>只负责把 UI 投影中的详情行做像素裁切、屏幕钳位和无阴影绘制；不解析服务端
 * 文本、不判断原因，也不发送动作。将它从窗口状态机拆出后，滚动/命中代码仍只关心
 * 当前可见行，详情不会重新引入一个无限内容层。</p>
 */
final class WorkflowDetailTooltipRenderer {
    private static final int MAX_TEXT_WIDTH = 236;
    private static final int PADDING = 4;

    private WorkflowDetailTooltipRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            Font font,
            int screenWidth,
            int screenHeight,
            WorkflowUiRow row,
            UiRect anchor) {
        if (graphics == null || font == null || row == null
                || anchor == null || row.detailLines.isEmpty()) {
            return;
        }
        int maxLines = Math.max(
                1,
                Math.min(row.detailLines.size(),
                        Math.max(1, (screenHeight - 16) / font.lineHeight) - 1));
        int omitted = row.detailLines.size() - maxLines;
        int lineCount = maxLines + (omitted > 0 ? 1 : 0);
        int textWidth = Math.max(
                1,
                Math.min(MAX_TEXT_WIDTH, screenWidth - PADDING * 2 - 8));
        int tooltipHeight = PADDING * 2 + lineCount * font.lineHeight;
        UiTooltipPlacement.Result placement = UiTooltipPlacement.place(
                new UiRect(0, 0, screenWidth, screenHeight),
                anchor,
                textWidth + PADDING * 2,
                tooltipHeight,
                4,
                UiTooltipPlacement.Direction.RIGHT);
        UiRect bounds = placement.getBounds();
        graphics.fill(
                (int) bounds.getX(),
                (int) bounds.getY(),
                (int) bounds.right(),
                (int) bounds.bottom(),
                UiThemeRuntime.color(UiThemeToken.SURFACE_RAISED).toArgb());
        for (int index = 0; index < maxLines; index++) {
            graphics.drawString(
                    font,
                    RtsClientUiUtil.trimToWidth(
                            font, row.detailLines.get(index), textWidth),
                    (int) bounds.getX() + PADDING,
                    (int) bounds.getY() + PADDING + index * font.lineHeight,
                    UiThemeRuntime.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                    false);
        }
        if (omitted > 0) {
            String more = Component.translatable(
                    "screen.rtsbuilding.workflow.detail.more", omitted).getString();
            graphics.drawString(
                    font,
                    RtsClientUiUtil.trimToWidth(font, more, textWidth),
                    (int) bounds.getX() + PADDING,
                    (int) bounds.getY() + PADDING + maxLines * font.lineHeight,
                    UiThemeRuntime.color(UiThemeToken.TEXT_SECONDARY).toArgb(),
                    false);
        }
    }
}
