package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 在 RTS 虚拟视口中绘制 Quick Build 的标题与说明提示。
 *
 * <p>它不调用 Minecraft 原生 tooltip，因为原生定位读取外层 GUI 视口，而 Quick Build
 * 的命中和绘制使用 RTS 缩放后的虚拟坐标。这里让 Shapes 与 Tools 共享同一套定位、换行
 * 和主题外观；业务状态与按钮命中仍由控制面负责。</p>
 */
final class QuickBuildHoverTooltipRenderer {
    private static final int MAX_TEXT_WIDTH = 116;
    private static final int PADDING = 4;
    private static final int TITLE_DETAIL_GAP = 2;
    private static final int BORDER = 1;

    static void render(GuiGraphics graphics, BuilderScreen screen,
                       Component title, Component detail, int mouseX, int mouseY) {
        List<FormattedCharSequence> detailLines = screen.font().split(detail, MAX_TEXT_WIDTH);
        int textWidth = screen.font().width(title);
        for (FormattedCharSequence line : detailLines) {
            textWidth = Math.max(textWidth, screen.font().width(line));
        }
        int lineHeight = screen.font().lineHeight;
        int contentHeight = lineHeight;
        if (!detailLines.isEmpty()) {
            contentHeight += TITLE_DETAIL_GAP + detailLines.size() * lineHeight;
        }
        UiRect bounds = QuickBuildWindowLayout.tooltipBounds(
                screen.width, screen.height, mouseX, mouseY,
                textWidth + PADDING * 2, contentHeight + PADDING * 2);
        int x = (int) Math.round(bounds.getX());
        int y = (int) Math.round(bounds.getY());
        int width = (int) Math.round(bounds.getWidth());
        int height = (int) Math.round(bounds.getHeight());

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        try {
            graphics.fill(x, y, x + width, y + height,
                    RtsMainlineTheme.TOOLTIP_BORDER.toArgb());
            graphics.fill(x + BORDER, y + BORDER,
                    x + width - BORDER, y + height - BORDER,
                    RtsMainlineTheme.TOOLTIP_BACKGROUND.toArgb());
            int textX = x + PADDING;
            int textY = y + PADDING;
            graphics.drawString(screen.font(), title, textX, textY,
                    QuickBuildStyle.MODE_TEXT.toArgb(), false);
            textY += lineHeight + TITLE_DETAIL_GAP;
            for (FormattedCharSequence line : detailLines) {
                graphics.drawString(screen.font(), line, textX, textY,
                        QuickBuildStyle.SECTION_TEXT.toArgb(), false);
                textY += lineHeight;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private QuickBuildHoverTooltipRenderer() {
    }
}
