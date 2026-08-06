package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiCategory;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCategoryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCategoryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 底栏分类树的 Minecraft 绘制适配器。
 *
 * <p>本类只把 Core 分类行、Kit 几何和共享主题翻译为 GuiGraphics 调用；分类注册表查询、
 * 滚动状态、选择/展开动作与网络副作用仍由 BottomPanel 和其 adapter 编排。所有文字均
 * 关闭阴影，避免分类栏被后绘制的半透明窗口覆盖时发生文字穿透。</p>
 */
public final class BottomPanelCategoryRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 64);

    private BottomPanelCategoryRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            Font font,
            Component title,
            List<BottomBarUiCategory> categories,
            BottomPanelCategoryLayout layout,
            int mouseX,
            int mouseY) {
        fill(graphics, layout.panel, BottomPanelCategoryStyle.PANEL_BACKGROUND.toArgb());
        int titleX = layout.panel.x
                + (layout.panel.width - font.width(title)) / 2;
        graphics.drawString(
                font, title,
                titleX, layout.panel.y + BottomPanelCategoryLayout.TITLE_TOP,
                BottomPanelCategoryStyle.TITLE_TEXT.toArgb(),
                false);

        fill(graphics, layout.scrollUp,
                BottomPanelCategoryStyle.buttonBackground(
                        BottomPanelCategoryStyle.SCROLL_BUTTON_BACKGROUND,
                        hover("scroll.up", layout.scrollUp.contains(mouseX, mouseY), false))
                        .toArgb());
        fill(graphics, layout.scrollDown,
                BottomPanelCategoryStyle.buttonBackground(
                        BottomPanelCategoryStyle.SCROLL_BUTTON_BACKGROUND,
                        hover("scroll.down", layout.scrollDown.contains(mouseX, mouseY), false))
                        .toArgb());
        drawCenteredNoShadow(graphics, font, "^", layout.scrollUp);
        drawCenteredNoShadow(graphics, font, "v", layout.scrollDown);

        int to = Math.min(categories.size(), layout.scroll + layout.visibleCount());
        for (int categoryIndex = layout.scroll; categoryIndex < to; categoryIndex++) {
            BottomBarUiCategory category = categories.get(categoryIndex);
            BottomPanelCategoryLayout.Area row = layout.rowArea(categoryIndex);
            int visibleRow = categoryIndex - layout.scroll;
            double rowHover = hover(
                    "row." + visibleRow,
                    row.contains(mouseX, mouseY),
                    category.selected);
            fill(graphics, row,
                    BottomPanelCategoryStyle.rowBackground(
                            category.selected, rowHover).toArgb());

            int labelX = layout.panel.x + BottomPanelCategoryLayout.TEXT_LEFT_INSET
                    + category.depth * BottomPanelCategoryLayout.DEPTH_INDENT;
            int labelRight = layout.panel.x + layout.panel.width
                    - BottomPanelCategoryLayout.TEXT_LEFT_INSET;
            if (category.expandable) {
                BottomPanelCategoryLayout.Area toggle = layout.toggleArea(categoryIndex);
                double toggleHover = hover(
                        "toggle." + visibleRow,
                        toggle.contains(mouseX, mouseY),
                        category.expanded);
                fill(graphics, toggle,
                        BottomPanelCategoryStyle.buttonBackground(
                                BottomPanelCategoryStyle.TOGGLE_BACKGROUND,
                                toggleHover).toArgb());
                drawCenteredNoShadow(
                        graphics, font, category.expanded ? "-" : "+", toggle);
                labelRight = toggle.x - BottomPanelCategoryLayout.LABEL_TOGGLE_GAP;
            }
            drawScaledLabel(
                    graphics,
                    font,
                    category.label,
                    labelX,
                    labelRight,
                    row,
                    BottomPanelCategoryStyle.rowText(category.selected).toArgb());
        }
    }

    private static double hover(String id, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return ANIMATIONS.update(
                id, state, Config.isUiAnimationsEnabled()).hover();
    }

    private static void drawScaledLabel(
            GuiGraphics graphics,
            Font font,
            String text,
            int left,
            int right,
            BottomPanelCategoryLayout.Area row,
            int color) {
        int availableWidth = Math.max(8, right - left);
        int unscaledWidth = Math.max(8, (int) Math.floor(
                availableWidth / BottomPanelCategoryLayout.TEXT_SCALE));
        String label = font.plainSubstrByWidth(text == null ? "" : text, unscaledWidth);
        int scaledTextWidth = (int) Math.ceil(
                font.width(label) * BottomPanelCategoryLayout.TEXT_SCALE);
        int x = left + Math.max(0, (availableWidth - scaledTextWidth) / 2);
        int scaledTextHeight = Math.max(1, (int) Math.ceil(
                font.lineHeight * BottomPanelCategoryLayout.TEXT_SCALE));
        int y = row.y + Math.max(0, (row.height - scaledTextHeight) / 2);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(
                BottomPanelCategoryLayout.TEXT_SCALE,
                BottomPanelCategoryLayout.TEXT_SCALE,
                1.0F);
        graphics.drawString(font, label, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawCenteredNoShadow(
            GuiGraphics graphics,
            Font font,
            String text,
            BottomPanelCategoryLayout.Area area) {
        int x = area.x + (area.width - font.width(text)) / 2;
        int y = area.y + Math.max(0, (area.height - font.lineHeight) / 2);
        graphics.drawString(
                font, text, x, y,
                BottomPanelCategoryStyle.TITLE_TEXT.toArgb(),
                false);
    }

    private static void fill(
            GuiGraphics graphics,
            BottomPanelCategoryLayout.Area area,
            int color) {
        graphics.fill(
                area.x, area.y,
                area.x + area.width, area.y + area.height,
                color);
    }
}
