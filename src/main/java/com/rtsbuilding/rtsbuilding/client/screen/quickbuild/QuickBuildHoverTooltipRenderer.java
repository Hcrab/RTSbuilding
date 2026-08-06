package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick Build 的 RTS 虚拟视口 Tooltip 绘制器。
 *
 * <p>它只负责将已经确定的标题和说明排版到 Tooltip 框；不决定悬停命中、工具状态或
 * 世界操作。LegacyGuiGraphics 的原生 Tooltip 依赖外层 Minecraft GUI 视口，不能用于
 * 已经按 RTS 缩放坐标命中的 Quick Build 控件，因此这里必须使用
 * {@link QuickBuildWindowLayout#tooltipBounds(int, int, int, int, int, int)} 统一翻转和夹紧。</p>
 */
final class QuickBuildHoverTooltipRenderer {
    private static final int MAX_TEXT_WIDTH = 116;
    private static final int PADDING = 4;
    private static final int TITLE_DETAIL_GAP = 2;
    private static final int BORDER_THICKNESS = 1;

    private QuickBuildHoverTooltipRenderer() {
    }

    static void render(LegacyGuiGraphics graphics, BuilderScreen screen,
            String title, String detail, int mouseX, int mouseY) {
        if (graphics == null || screen == null || title == null || title.isEmpty()) return;
        List<String> detailLines = detail == null || detail.isEmpty()
                ? new ArrayList<String>() : new ArrayList<String>(
                screen.font().listFormattedStringToWidth(detail, MAX_TEXT_WIDTH));
        int textWidth = screen.font().getStringWidth(title);
        for (String line : detailLines) {
            textWidth = Math.max(textWidth, screen.font().getStringWidth(line));
        }
        int lineHeight = screen.font().FONT_HEIGHT;
        int contentHeight = lineHeight;
        if (!detailLines.isEmpty()) contentHeight += TITLE_DETAIL_GAP + detailLines.size() * lineHeight;
        UiRect bounds = QuickBuildWindowLayout.tooltipBounds(screen.width, screen.height,
                mouseX, mouseY, textWidth + PADDING * 2, contentHeight + PADDING * 2);
        int x = (int) Math.round(bounds.getX());
        int y = (int) Math.round(bounds.getY());
        int width = (int) Math.round(bounds.getWidth());
        int height = (int) Math.round(bounds.getHeight());
        graphics.fill(x, y, x + width, y + height, QuickBuildStyle.TOOLTIP_BACKGROUND.toArgb());
        graphics.fill(x, y, x + width, y + BORDER_THICKNESS, QuickBuildStyle.TOOLTIP_BORDER.toArgb());
        graphics.fill(x, y + height - BORDER_THICKNESS, x + width, y + height,
                QuickBuildStyle.TOOLTIP_BORDER.toArgb());
        graphics.fill(x, y, x + BORDER_THICKNESS, y + height, QuickBuildStyle.TOOLTIP_BORDER.toArgb());
        graphics.fill(x + width - BORDER_THICKNESS, y, x + width, y + height,
                QuickBuildStyle.TOOLTIP_BORDER.toArgb());
        int textY = y + PADDING;
        graphics.drawString(screen.font(), title, x + PADDING, textY,
                QuickBuildStyle.TOOLTIP_TEXT.toArgb(), false);
        textY += lineHeight + TITLE_DETAIL_GAP;
        for (String line : detailLines) {
            graphics.drawString(screen.font(), line, x + PADDING, textY,
                    QuickBuildStyle.TOOLTIP_TEXT.toArgb(), false);
            textY += lineHeight;
        }
    }
}
