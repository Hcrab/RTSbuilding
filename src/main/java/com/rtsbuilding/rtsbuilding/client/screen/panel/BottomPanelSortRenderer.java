package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelSortLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelSortStyle;
import net.minecraft.client.gui.FontRenderer;

/**
 * 底栏排序与高度按钮的 Minecraft 绘制适配器。
 *
 * <p>它只消费已解析的 Kit 几何和当前方向文本，不修改排序或面板高度；点击后的副作用由
 * {@link BottomPanel} 根据同一布局返回的控件语义执行。</p>
 */
public final class BottomPanelSortRenderer {
    private BottomPanelSortRenderer() {
    }

    public static void render(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            BottomPanelSortLayout layout,
            String sortLabel,
            boolean ascending) {
        drawButton(graphics, font, layout.cycleSort, "S");
        drawButton(graphics, font, layout.toggleDirection, ascending ? "A" : "D");
        drawButton(graphics, font, layout.increaseHeight, "+");
        drawButton(graphics, font, layout.decreaseHeight, "-");
        graphics.drawString(
                font, sortLabel,
                layout.labelX(), layout.labelY(),
                BottomPanelSortStyle.LABEL_TEXT.toArgb(),
                false);
    }

    private static void drawButton(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            BottomPanelSortLayout.Area area,
            String label) {
        UiCompactFrameRenderer.frame(
                new MinecraftUiCanvas(graphics, font),
                new UiRect(area.x, area.y, area.width, area.height),
                BottomPanelSortStyle.BUTTON_BACKGROUND,
                BottomPanelSortStyle.BUTTON_BORDER_LIGHT,
                BottomPanelSortStyle.BUTTON_BORDER_DARK);
        int textX = area.x + (area.width - font.getStringWidth(label)) / 2;
        int textY = area.y + Math.max(0, (area.height - font.FONT_HEIGHT) / 2);
        graphics.drawString(
                font, label, textX, textY,
                BottomPanelSortStyle.BUTTON_TEXT.toArgb(),
                false);
    }
}
