package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBrowseLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelBrowseStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;

/**
 * 底栏搜索清除键与分页键的 Minecraft 绘制适配器。
 *
 * <p>真实 EditBox 仍由 BuilderScreen 创建和渲染；本类只绘制围绕它的确定性工具条，
 * 不修改搜索值、不翻页，也不持有焦点。</p>
 */
public final class BottomPanelBrowseRenderer {
    private BottomPanelBrowseRenderer() {
    }

    public static void renderControls(
            RtsGuiContext graphics,
            Font font,
            BottomPanelBrowseLayout layout,
            boolean searchFocused,
            boolean hasSearchValue,
            int page,
            int pageCount) {
        drawFrame(
                new MinecraftUiCanvas(graphics, font),
                layout.clearSearch,
                BottomPanelBrowseStyle.clearBackground(searchFocused),
                BottomPanelBrowseStyle.CLEAR_BORDER_LIGHT,
                BottomPanelBrowseStyle.CLEAR_BORDER_DARK);
        drawCenteredNoShadow(
                graphics, font, "x", layout.clearSearch,
                BottomPanelBrowseStyle.clearText(hasSearchValue).toArgb());

        fill(graphics, layout.previousPage,
                BottomPanelBrowseStyle.PAGE_BUTTON_BACKGROUND.toArgb());
        fill(graphics, layout.nextPage,
                BottomPanelBrowseStyle.PAGE_BUTTON_BACKGROUND.toArgb());
        drawCenteredNoShadow(
                graphics, font, "<", layout.previousPage,
                BottomPanelBrowseStyle.TEXT.toArgb());
        drawCenteredNoShadow(
                graphics, font, ">", layout.nextPage,
                BottomPanelBrowseStyle.TEXT.toArgb());
        graphics.drawString(
                font,
                (Math.max(0, page) + 1) + "/" + Math.max(1, pageCount),
                layout.pageTextX(),
                layout.previousPage.y + BottomPanelBrowseLayout.PAGE_TEXT_TOP,
                BottomPanelBrowseStyle.TEXT.toArgb(),
                false);
    }

    private static void drawFrame(
            MinecraftUiCanvas canvas,
            BottomPanelBrowseLayout.Area area,
            UiColor background,
            UiColor light,
            UiColor dark) {
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(area.x, area.y, area.width, area.height),
                background,
                light,
                dark);
    }

    private static void drawCenteredNoShadow(
            RtsGuiContext graphics,
            Font font,
            String text,
            BottomPanelBrowseLayout.Area area,
            int color) {
        int x = area.x + (area.width - font.width(text)) / 2;
        int y = area.y + Math.max(0, (area.height - font.lineHeight) / 2);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static void fill(
            RtsGuiContext graphics,
            BottomPanelBrowseLayout.Area area,
            int color) {
        graphics.fill(
                area.x, area.y,
                area.x + area.width, area.y + area.height,
                color);
    }
}
