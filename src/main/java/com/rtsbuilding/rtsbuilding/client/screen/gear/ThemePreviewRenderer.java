package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.network.chat.Component;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * 只负责绘制主题预览，不拥有当前选择、文件读写或应用状态。
 *
 * <p>预览与设置窗口分离后，后续调整高 GUI 缩放布局时不会触碰导入、导出和激活逻辑。</p>
 */
final class ThemePreviewRenderer {
    private static final int SLOT_Y_OFFSET = 38;
    private static final int STATUS_Y_OFFSET = 46;
    private static final int CHIP_Y_OFFSET = 48;

    private ThemePreviewRenderer() {
    }

    static void render(RtsGuiContext graphics, MinecraftUiCanvas canvas, Font font,
                       UiThemeDefinition theme, int x, int y, int width, int height) {
        UiColor canvasColor = theme.color(UiThemeToken.CANVAS);
        UiColor surface = theme.color(UiThemeToken.SURFACE);
        UiColor raised = theme.color(UiThemeToken.SURFACE_RAISED);
        UiColor border = theme.color(UiThemeToken.BORDER_STRONG);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, height), surface, border,
                theme.color(UiThemeToken.BORDER_SOFT));
        graphics.fill(x + PREVIEW_INSET, y + PREVIEW_INSET,
                x + width - PREVIEW_INSET, y + PREVIEW_CANVAS_Y - PREVIEW_INSET,
                theme.color(UiThemeToken.TOP_BAR).toArgb());
        graphics.drawString(font, text("screen.rtsbuilding.theme.preview"),
                x + PREVIEW_TITLE_X, y + PREVIEW_TITLE_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        graphics.fill(x + PREVIEW_INSET, y + PREVIEW_CANVAS_Y,
                x + width - PREVIEW_INSET, y + height - PREVIEW_INSET, canvasColor.toArgb());

        int controlY = y + PREVIEW_CONTROL_Y;
        int controlWidth = Math.max(PREVIEW_CONTROL_MIN_W,
                (width - PREVIEW_CONTROL_WIDTH_RESERVE) / PREVIEW_CONTROL_COUNT);
        UiThemeToken[] tokens = {UiThemeToken.CONTROL_IDLE,
                UiThemeToken.CONTROL_HOVER, UiThemeToken.CONTROL_SELECTED};
        String[] labels = {"screen.rtsbuilding.theme.sample.idle",
                "screen.rtsbuilding.theme.sample.hover",
                "screen.rtsbuilding.theme.sample.active"};
        for (int i = 0; i < PREVIEW_CONTROL_COUNT; i++) {
            drawSampleControl(graphics, font, theme,
                    x + PREVIEW_CONTROL_START_X + i * (controlWidth + PREVIEW_CONTROL_GAP),
                    controlY, controlWidth, PREVIEW_CONTROL_H, tokens[i], labels[i]);
        }

        int slotY = controlY + SLOT_Y_OFFSET;
        int slotCount = Math.max(PREVIEW_SLOT_MIN_COUNT, Math.min(PREVIEW_SLOT_MAX_COUNT,
                (width - PREVIEW_SLOT_WIDTH_RESERVE) / PREVIEW_SLOT_PITCH));
        for (int i = 0; i < slotCount; i++) {
            int slotX = x + PREVIEW_SLOT_START_X + i * PREVIEW_SLOT_PITCH;
            graphics.fill(slotX, slotY, slotX + PREVIEW_SLOT_SIZE, slotY + PREVIEW_SLOT_SIZE,
                    theme.color(i == PREVIEW_SLOT_HOVER_INDEX
                            ? UiThemeToken.SLOT_HOVER : UiThemeToken.SLOT_IDLE).toArgb());
            graphics.renderOutline(slotX, slotY, PREVIEW_SLOT_SIZE, PREVIEW_SLOT_SIZE,
                    border.toArgb());
        }
        graphics.fill(x + width - PREVIEW_SCROLL_TRACK_RIGHT, slotY,
                x + width - PREVIEW_SCROLL_TRACK_END, slotY + PREVIEW_SCROLL_H,
                theme.color(UiThemeToken.SCROLLBAR_TRACK).toArgb());
        graphics.fill(x + width - PREVIEW_SCROLL_THUMB_RIGHT,
                slotY + PREVIEW_SCROLL_THUMB_Y, x + width - PREVIEW_SCROLL_THUMB_END,
                slotY + PREVIEW_SCROLL_THUMB_END_Y,
                theme.color(UiThemeToken.SCROLLBAR_THUMB).toArgb());

        int statusY = slotY + STATUS_Y_OFFSET;
        graphics.fill(x + PREVIEW_STATUS_X, statusY, x + width - PREVIEW_STATUS_RIGHT,
                statusY + PREVIEW_STATUS_H, raised.toArgb());
        int statusTextWidth = Math.max(1,
                width - PREVIEW_STATUS_TEXT_X - PREVIEW_STATUS_RIGHT);
        graphics.drawString(font, trim(font, "screen.rtsbuilding.theme.sample.primary", statusTextWidth),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_PRIMARY_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        graphics.drawString(font, trim(font, "screen.rtsbuilding.theme.sample.secondary", statusTextWidth),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_SECONDARY_Y,
                theme.color(UiThemeToken.TEXT_SECONDARY).toArgb(), false);
        int chipY = statusY + CHIP_Y_OFFSET;
        UiThemeToken[] chips = {UiThemeToken.SUCCESS, UiThemeToken.WARNING,
                UiThemeToken.ERROR, UiThemeToken.ACCENT_PRIMARY};
        for (int i = 0; i < chips.length; i++) {
            graphics.fill(x + PREVIEW_STATUS_X + i * PREVIEW_CHIP_PITCH, chipY,
                    x + PREVIEW_CHIP_END_X + i * PREVIEW_CHIP_PITCH,
                    chipY + PREVIEW_CHIP_H, theme.color(chips[i]).toArgb());
        }
    }

    private static void drawSampleControl(RtsGuiContext graphics, Font font,
                                          UiThemeDefinition theme, int x, int y,
                                          int width, int height, UiThemeToken token,
                                          String labelKey) {
        graphics.fill(x, y, x + width, y + height, theme.color(token).toArgb());
        graphics.renderOutline(x, y, width, height,
                theme.color(UiThemeToken.BORDER_STRONG).toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, font, text(labelKey),
                x + width / 2, y + SAMPLE_TEXT_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb());
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static String trim(Font font, String key, int width) {
        return font.plainSubstrByWidth(text(key), width);
    }
}
