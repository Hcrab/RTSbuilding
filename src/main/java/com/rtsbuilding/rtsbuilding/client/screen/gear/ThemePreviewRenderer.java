package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * 主题预览渲染器。
 *
 * <p>它严格复用 {@link com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout}
 * 的几何常量，因此生产窗口和离屏预览不会各自维护一套可能漂移的坐标。</p>
 */
final class ThemePreviewRenderer {
    private ThemePreviewRenderer() {
    }

    static void render(GuiGraphicsExtractor graphics, MinecraftUiCanvas canvas, Font font,
                       UiThemeDefinition theme, int x, int y, int width, int height) {
        UiColor surface = theme.color(UiThemeToken.SURFACE);
        UiColor border = theme.color(UiThemeToken.BORDER_STRONG);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, height), surface, border,
                theme.color(UiThemeToken.BORDER_SOFT));
        graphics.fill(x + PREVIEW_INSET, y + PREVIEW_INSET,
                x + width - PREVIEW_INSET, y + PREVIEW_CANVAS_Y - PREVIEW_INSET,
                theme.color(UiThemeToken.TOP_BAR).toArgb());
        graphics.text(font, text("screen.rtsbuilding.theme.preview"),
                x + PREVIEW_TITLE_X, y + PREVIEW_TITLE_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        graphics.fill(x + PREVIEW_INSET, y + PREVIEW_CANVAS_Y,
                x + width - PREVIEW_INSET, y + height - PREVIEW_INSET,
                theme.color(UiThemeToken.CANVAS).toArgb());

        int controlY = y + PREVIEW_CONTROL_Y;
        int controlWidth = Math.max(PREVIEW_CONTROL_MIN_W,
                (width - PREVIEW_CONTROL_WIDTH_RESERVE) / PREVIEW_CONTROL_COUNT);
        UiThemeToken[] tokens = {UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.CONTROL_SELECTED};
        String[] labels = {"screen.rtsbuilding.theme.sample.idle",
                "screen.rtsbuilding.theme.sample.hover", "screen.rtsbuilding.theme.sample.active"};
        for (int index = 0; index < PREVIEW_CONTROL_COUNT; index++) {
            int controlX = x + PREVIEW_CONTROL_START_X + index * (controlWidth + PREVIEW_CONTROL_GAP);
            frame(graphics, controlX, controlY, controlWidth, PREVIEW_CONTROL_H,
                    theme.color(tokens[index]).toArgb(), border.toArgb());
            RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, text(labels[index]),
                    controlX + controlWidth / 2, controlY + SAMPLE_TEXT_Y,
                    theme.color(UiThemeToken.TEXT_PRIMARY).toArgb());
        }

        int slotY = controlY + 38;
        int slotCount = Math.max(PREVIEW_SLOT_MIN_COUNT, Math.min(PREVIEW_SLOT_MAX_COUNT,
                (width - PREVIEW_SLOT_WIDTH_RESERVE) / PREVIEW_SLOT_PITCH));
        for (int index = 0; index < slotCount; index++) {
            int slotX = x + PREVIEW_SLOT_START_X + index * PREVIEW_SLOT_PITCH;
            frame(graphics, slotX, slotY, PREVIEW_SLOT_SIZE, PREVIEW_SLOT_SIZE,
                    theme.color(index == PREVIEW_SLOT_HOVER_INDEX
                            ? UiThemeToken.SLOT_HOVER : UiThemeToken.SLOT_IDLE).toArgb(),
                    border.toArgb());
        }
        graphics.fill(x + width - PREVIEW_SCROLL_TRACK_RIGHT, slotY,
                x + width - PREVIEW_SCROLL_TRACK_END, slotY + PREVIEW_SCROLL_H,
                theme.color(UiThemeToken.SCROLLBAR_TRACK).toArgb());
        graphics.fill(x + width - PREVIEW_SCROLL_THUMB_RIGHT, slotY + PREVIEW_SCROLL_THUMB_Y,
                x + width - PREVIEW_SCROLL_THUMB_END, slotY + PREVIEW_SCROLL_THUMB_END_Y,
                theme.color(UiThemeToken.SCROLLBAR_THUMB).toArgb());

        int statusY = slotY + 46;
        graphics.fill(x + PREVIEW_STATUS_X, statusY, x + width - PREVIEW_STATUS_RIGHT,
                statusY + PREVIEW_STATUS_H, theme.color(UiThemeToken.SURFACE_RAISED).toArgb());
        int statusTextWidth = Math.max(1, width - PREVIEW_STATUS_TEXT_X - PREVIEW_STATUS_RIGHT);
        graphics.text(font, trim(font, "screen.rtsbuilding.theme.sample.primary", statusTextWidth),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_PRIMARY_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        graphics.text(font, trim(font, "screen.rtsbuilding.theme.sample.secondary", statusTextWidth),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_SECONDARY_Y,
                theme.color(UiThemeToken.TEXT_SECONDARY).toArgb(), false);
        int chipY = statusY + 48;
        UiThemeToken[] chips = {UiThemeToken.SUCCESS, UiThemeToken.WARNING,
                UiThemeToken.ERROR, UiThemeToken.ACCENT_PRIMARY};
        for (int index = 0; index < chips.length; index++) {
            graphics.fill(x + PREVIEW_STATUS_X + index * PREVIEW_CHIP_PITCH, chipY,
                    x + PREVIEW_CHIP_END_X + index * PREVIEW_CHIP_PITCH,
                    chipY + PREVIEW_CHIP_H, theme.color(chips[index]).toArgb());
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + RtsMainlineLayout.D1, border);
        graphics.fill(x, y + height - RtsMainlineLayout.D1, x + width, y + height, border);
        graphics.fill(x, y, x + RtsMainlineLayout.D1, y + height, border);
        graphics.fill(x + width - RtsMainlineLayout.D1, y, x + width, y + height, border);
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static String trim(Font font, String key, int width) {
        return font.plainSubstrByWidth(text(key), width);
    }
}
