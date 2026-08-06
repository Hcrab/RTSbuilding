package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelSortLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelSortStyle;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;

/**
 * 底栏排序与高度按钮的 Minecraft 绘制适配器。
 *
 * <p>它只消费已解析的 Kit 几何和当前方向文本，不修改排序或面板高度；点击后的副作用由
 * {@link BottomPanel} 根据同一布局返回的控件语义执行。</p>
 */
public final class BottomPanelSortRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 4);

    private BottomPanelSortRenderer() {
    }

    public static void render(
            RtsGuiContext graphics,
            Font font,
            BottomPanelSortLayout layout,
            String sortLabel,
            boolean ascending,
            int mouseX,
            int mouseY) {
        drawButton(graphics, font, layout.cycleSort, "S",
                hover("sort", layout.cycleSort.contains(mouseX, mouseY)));
        drawButton(graphics, font, layout.toggleDirection, ascending ? "A" : "D",
                hover("direction", layout.toggleDirection.contains(mouseX, mouseY)));
        drawButton(graphics, font, layout.increaseHeight, "+",
                hover("increase", layout.increaseHeight.contains(mouseX, mouseY)));
        drawButton(graphics, font, layout.decreaseHeight, "-",
                hover("decrease", layout.decreaseHeight.contains(mouseX, mouseY)));
        graphics.drawString(
                font, sortLabel,
                layout.labelX(), layout.labelY(),
                BottomPanelSortStyle.LABEL_TEXT.toArgb(),
                false);
    }

    private static void drawButton(
            RtsGuiContext graphics,
            Font font,
            BottomPanelSortLayout.Area area,
            String label,
            double hoverStrength) {
        UiCompactFrameRenderer.frame(
                new MinecraftUiCanvas(graphics, font),
                new UiRect(area.x, area.y, area.width, area.height),
                BottomPanelSortStyle.buttonBackground(hoverStrength),
                BottomPanelSortStyle.BUTTON_BORDER_LIGHT,
                BottomPanelSortStyle.BUTTON_BORDER_DARK);
        int textX = area.x + (area.width - font.width(label)) / 2;
        int textY = area.y + Math.max(0, (area.height - font.lineHeight) / 2);
        graphics.drawString(
                font, label, textX, textY,
                BottomPanelSortStyle.BUTTON_TEXT.toArgb(),
                false);
    }

    private static double hover(String id, boolean hovered) {
        return ANIMATIONS.update(
                id,
                UiControlState.enabled().withInteraction(hovered, false, false),
                Config.isUiAnimationsEnabled()).hover();
    }
}
