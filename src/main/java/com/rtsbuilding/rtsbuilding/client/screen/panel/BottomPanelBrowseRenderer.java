package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelBrowseLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelBrowseStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 底栏搜索清除与分页控件的 26.1 Extractor 绘制适配器。
 *
 * <p>真实 EditBox 仍由 BuilderScreen 管理；本类只绘制围绕它的确定性控件，不修改
 * 搜索值、焦点或页码。</p>
 */
public final class BottomPanelBrowseRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 3);

    private BottomPanelBrowseRenderer() {
    }

    public static void renderControls(
            GuiGraphicsExtractor graphics,
            Font font,
            BottomPanelBrowseLayout layout,
            boolean searchFocused,
            boolean hasSearchValue,
            int page,
            int pageCount,
            int mouseX,
            int mouseY) {
        double clearHover = hover("clear", layout.clearSearch.contains(mouseX, mouseY));
        double previousHover = hover("previous", layout.previousPage.contains(mouseX, mouseY));
        double nextHover = hover("next", layout.nextPage.contains(mouseX, mouseY));
        drawFrame(new MinecraftUiCanvas(graphics, font), layout.clearSearch,
                BottomPanelBrowseStyle.clearBackground(searchFocused, clearHover),
                BottomPanelBrowseStyle.CLEAR_BORDER_LIGHT,
                BottomPanelBrowseStyle.CLEAR_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, "x", layout.clearSearch,
                BottomPanelBrowseStyle.clearText(hasSearchValue).toArgb());
        fill(graphics, layout.previousPage,
                BottomPanelBrowseStyle.pageBackground(previousHover).toArgb());
        fill(graphics, layout.nextPage,
                BottomPanelBrowseStyle.pageBackground(nextHover).toArgb());
        drawCenteredNoShadow(graphics, font, "<", layout.previousPage,
                BottomPanelBrowseStyle.TEXT.toArgb());
        drawCenteredNoShadow(graphics, font, ">", layout.nextPage,
                BottomPanelBrowseStyle.TEXT.toArgb());
        graphics.text(font, (Math.max(0, page) + 1) + "/" + Math.max(1, pageCount),
                layout.pageTextX(),
                layout.previousPage.y + BottomPanelBrowseLayout.PAGE_TEXT_TOP,
                BottomPanelBrowseStyle.TEXT.toArgb(), false);
    }

    private static double hover(String id, boolean hovered) {
        return ANIMATIONS.update(id,
                UiControlState.enabled().withInteraction(hovered, false, false),
                Config.isUiAnimationsEnabled()).hover();
    }

    private static void drawFrame(
            MinecraftUiCanvas canvas,
            BottomPanelBrowseLayout.Area area,
            UiColor background,
            UiColor light,
            UiColor dark) {
        UiCompactFrameRenderer.frame(canvas,
                new UiRect(area.x, area.y, area.width, area.height),
                background, light, dark);
    }

    private static void drawCenteredNoShadow(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            BottomPanelBrowseLayout.Area area,
            int color) {
        int x = area.x + (area.width - font.width(text)) / 2;
        int y = area.y + Math.max(0, (area.height - font.lineHeight) / 2);
        graphics.text(font, text, x, y, color, false);
    }

    private static void fill(
            GuiGraphicsExtractor graphics,
            BottomPanelBrowseLayout.Area area,
            int color) {
        graphics.fill(area.x, area.y, area.x + area.width, area.y + area.height, color);
    }
}
