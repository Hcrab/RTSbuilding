package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeBuiltins;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * UI 主题设置窗口的离屏验收入口。
 *
 * <p>这里复用生产布局常量、主题令牌、正式语言文件和色轮纹理，只替换最终画布；
 * 不创建 Minecraft 客户端，也不读取或修改玩家当前主题配置。</p>
 */
public final class UiThemeSettingsPreviewMain {
    private static final int OUTPUT_W = 1920;
    private static final int OUTPUT_H = 1080;
    private static final double SCALE = 2.0D;
    private static final int LOGICAL_W = (int) (OUTPUT_W / SCALE);
    private static final int LOGICAL_H = (int) (OUTPUT_H / SCALE);
    private static final int TITLE_H = 20;
    private static final int LIST_W = 150;
    private static final int ROW_H = 34;
    private static final int EDITOR_W = 180;
    private static final int BUTTON_H = 22;
    private static final int WHEEL_SIZE = 95;
    private static final int VALUE_W = 10;
    private static final int VALUE_GAP = 8;

    private final UiMainlineAssets assets = new UiMainlineAssets();
    private final UiLanguageBundle language = assets.language("zh_cn");

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        File output = UiPreviewMain.outputDirectory(args);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Cannot create theme settings preview directory: " + output);
        }
        UiThemeRuntime.manager().activate(UiThemeBuiltins.CALIBRATED_ID);
        try (BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(OUTPUT_W, OUTPUT_H, SCALE)) {
            UiThemeSettingsPreviewMain renderer = new UiThemeSettingsPreviewMain();
            renderer.render(canvas);
            ImageIO.write(canvas.image(), "png", new File(output,
                    "01_theme_settings_calibrated_dark_1920x1080.png"));
        } finally {
            UiThemeRuntime.manager().fallBackToLegacy();
        }
        System.out.println("Rendered UI theme settings preview to " + output.getAbsolutePath());
    }

    private void render(BufferedImageUiCanvas canvas) {
        canvas.configureFont("zh_cn");
        drawBackground(canvas);
        int windowW = ThemeSettingsLayout.preferredWindowWidth(LOGICAL_W);
        int windowH = ThemeSettingsLayout.preferredWindowHeight(LOGICAL_H);
        int windowX = (LOGICAL_W - windowW) / 2;
        int windowY = (LOGICAL_H - windowH) / 2;
        drawWindow(canvas, windowX, windowY, windowW, windowH);
    }

    private void drawBackground(BufferedImageUiCanvas canvas) {
        UiThemeDefinition theme = UiThemeRuntime.manager().active();
        UiColor first = theme.color(UiThemeToken.CANVAS);
        UiColor second = theme.color(UiThemeToken.SURFACE_SUNKEN);
        canvas.clear(new Color(first.toArgb(), true));
        int tile = 72;
        for (int y = 0; y < LOGICAL_H; y += tile) {
            for (int x = 0; x < LOGICAL_W; x += tile) {
                canvas.fill(x, y, tile, tile, ((x / tile + y / tile) & 1) == 0 ? first : second);
            }
        }
        canvas.fill(0, 0, LOGICAL_W, LOGICAL_H, first.withAlpha(85));
    }

    private void drawWindow(BufferedImageUiCanvas canvas, int windowX, int windowY,
                            int windowW, int windowH) {
        UiChromeRenderer.frame(canvas, new UiRect(windowX, windowY, windowW, windowH), 1.0D,
                RtsMainlineTheme.WINDOW_BACKGROUND, RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                RtsMainlineTheme.WINDOW_BORDER_DARK);
        canvas.fill(windowX + 1, windowY + 1, windowW - 2, TITLE_H - 1,
                RtsMainlineTheme.WINDOW_TITLE);
        canvas.text(language.text("screen.rtsbuilding.theme.title"), windowX + 8, windowY + 5,
                RtsMainlineTheme.WINDOW_TITLE_TEXT);
        BufferedImage close = assets.closeButton();
        canvas.imageRegion(close, new UiRect(0, 0, 450, 450),
                new UiRect(windowX + windowW - 17, windowY + 3, 14, 14));

        int contentX = windowX + 1;
        int contentY = windowY + TITLE_H;
        int contentW = windowW - 2;
        int contentH = windowH - TITLE_H - 1;
        int x = contentX + OUTER_INSET;
        int y = contentY + OUTER_INSET;
        int h = contentH - OUTER_INSET * 2;

        frame(canvas, x, y, LIST_W, h, SettingsWindowStyle.VALUE_BACKGROUND,
                SettingsWindowStyle.VALUE_BORDER, SettingsWindowStyle.VALUE_DARK_BORDER);
        drawThemeList(canvas, x, y, h);

        int previewX = x + LIST_W + COLUMN_GAP;
        int previewW = contentW - LIST_W - EDITOR_W - COLUMN_WIDTH_RESERVE;
        UiThemeDefinition draft = UiThemeRuntime.manager().active();
        drawThemePreview(canvas, draft, previewX, y, previewW, h - BODY_FOOTER_RESERVE);

        int editorX = previewX + previewW + COLUMN_GAP;
        drawEditor(canvas, draft, editorX, y, EDITOR_W, h - BODY_FOOTER_RESERVE);
        drawActions(canvas, x, y + h - FOOTER_TOP_RESERVE,
                contentW - OUTER_INSET * 2, draft);
    }

    private void drawThemeList(BufferedImageUiCanvas canvas, int x, int y, int h) {
        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int rowY = y + LIST_INSET;
        int visibleRows = Math.max(1, (h - LIST_INSET - LIST_FOOTER_RESERVE) / ROW_H);
        for (int i = 0; i < themes.size() && i < visibleRows; i++) {
            UiThemeDefinition theme = themes.get(i);
            boolean selected = theme.id().equals(UiThemeBuiltins.CALIBRATED_ID);
            frame(canvas, x + LIST_INSET, rowY, LIST_W - DOUBLE_LIST_INSET, ROW_H - 3,
                    selected ? SettingsWindowStyle.TOGGLE_ON : SettingsWindowStyle.STEP_BACKGROUND,
                    selected ? SettingsWindowStyle.TOGGLE_ON_BORDER : SettingsWindowStyle.STEP_BORDER,
                    SettingsWindowStyle.STEP_DARK_BORDER);
            canvas.text(language.text(theme.nameKey()), x + LIST_INSET + THEME_NAME_X,
                    rowY + THEME_NAME_Y, SettingsWindowStyle.VALUE);
            String mode = theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT
                    ? language.text("screen.rtsbuilding.theme.mode.legacy")
                    : language.text("screen.rtsbuilding.theme.mode.palette");
            canvas.text(mode, x + LIST_INSET + THEME_NAME_X,
                    rowY + THEME_MODE_Y, SettingsWindowStyle.HINT);
            rowY += ROW_H;
        }
    }

    private void drawThemePreview(BufferedImageUiCanvas canvas, UiThemeDefinition theme,
                                  int x, int y, int width, int height) {
        UiColor surface = theme.color(UiThemeToken.SURFACE);
        UiColor border = theme.color(UiThemeToken.BORDER_STRONG);
        frame(canvas, x, y, width, height, surface, border,
                theme.color(UiThemeToken.BORDER_SOFT));
        canvas.fill(x + PREVIEW_INSET, y + PREVIEW_INSET,
                width - PREVIEW_INSET * 2, PREVIEW_CANVAS_Y - PREVIEW_INSET * 2,
                theme.color(UiThemeToken.TOP_BAR));
        canvas.text(language.text("screen.rtsbuilding.theme.preview"),
                x + PREVIEW_TITLE_X, y + PREVIEW_TITLE_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY));
        canvas.fill(x + PREVIEW_INSET, y + PREVIEW_CANVAS_Y,
                width - PREVIEW_INSET * 2, height - PREVIEW_CANVAS_Y - PREVIEW_INSET,
                theme.color(UiThemeToken.CANVAS));

        int controlY = y + PREVIEW_CONTROL_Y;
        int controlW = Math.max(PREVIEW_CONTROL_MIN_W,
                (width - PREVIEW_CONTROL_WIDTH_RESERVE) / PREVIEW_CONTROL_COUNT);
        UiThemeToken[] controls = {UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.CONTROL_SELECTED};
        String[] labels = {"screen.rtsbuilding.theme.sample.idle",
                "screen.rtsbuilding.theme.sample.hover", "screen.rtsbuilding.theme.sample.active"};
        for (int i = 0; i < controls.length; i++) {
            int controlX = x + PREVIEW_CONTROL_START_X
                    + i * (controlW + PREVIEW_CONTROL_GAP);
            canvas.fill(controlX, controlY, controlW, PREVIEW_CONTROL_H, theme.color(controls[i]));
            canvas.stroke(new UiRect(controlX, controlY, controlW, PREVIEW_CONTROL_H),
                    new Color(border.toArgb(), true));
            centered(canvas, language.text(labels[i]), controlX, controlY + SAMPLE_TEXT_Y,
                    controlW, theme.color(UiThemeToken.TEXT_PRIMARY));
        }

        int slotY = controlY + 38;
        int slotCount = Math.max(PREVIEW_SLOT_MIN_COUNT, Math.min(PREVIEW_SLOT_MAX_COUNT,
                (width - PREVIEW_SLOT_WIDTH_RESERVE) / PREVIEW_SLOT_PITCH));
        for (int i = 0; i < slotCount; i++) {
            int slotX = x + PREVIEW_SLOT_START_X + i * PREVIEW_SLOT_PITCH;
            canvas.fill(slotX, slotY, PREVIEW_SLOT_SIZE, PREVIEW_SLOT_SIZE,
                    theme.color(i == PREVIEW_SLOT_HOVER_INDEX
                            ? UiThemeToken.SLOT_HOVER : UiThemeToken.SLOT_IDLE));
            canvas.stroke(new UiRect(slotX, slotY, PREVIEW_SLOT_SIZE, PREVIEW_SLOT_SIZE),
                    new Color(border.toArgb(), true));
        }
        canvas.fill(x + width - PREVIEW_SCROLL_TRACK_RIGHT, slotY,
                PREVIEW_SCROLL_TRACK_RIGHT - PREVIEW_SCROLL_TRACK_END, PREVIEW_SCROLL_H,
                theme.color(UiThemeToken.SCROLLBAR_TRACK));
        canvas.fill(x + width - PREVIEW_SCROLL_THUMB_RIGHT,
                slotY + PREVIEW_SCROLL_THUMB_Y,
                PREVIEW_SCROLL_THUMB_RIGHT - PREVIEW_SCROLL_THUMB_END,
                PREVIEW_SCROLL_THUMB_END_Y - PREVIEW_SCROLL_THUMB_Y,
                theme.color(UiThemeToken.SCROLLBAR_THUMB));

        int statusY = slotY + 46;
        canvas.fill(x + PREVIEW_STATUS_X, statusY,
                width - PREVIEW_STATUS_X - PREVIEW_STATUS_RIGHT, PREVIEW_STATUS_H,
                theme.color(UiThemeToken.SURFACE_RAISED));
        canvas.text(language.text("screen.rtsbuilding.theme.sample.primary"),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_PRIMARY_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY));
        canvas.text(language.text("screen.rtsbuilding.theme.sample.secondary"),
                x + PREVIEW_STATUS_TEXT_X, statusY + PREVIEW_STATUS_SECONDARY_Y,
                theme.color(UiThemeToken.TEXT_SECONDARY));
        UiThemeToken[] chips = {UiThemeToken.SUCCESS, UiThemeToken.WARNING,
                UiThemeToken.ERROR, UiThemeToken.ACCENT_PRIMARY};
        int chipY = statusY + 48;
        for (int i = 0; i < chips.length; i++) {
            canvas.fill(x + PREVIEW_STATUS_X + i * PREVIEW_CHIP_PITCH, chipY,
                    PREVIEW_CHIP_END_X - PREVIEW_STATUS_X, PREVIEW_CHIP_H,
                    theme.color(chips[i]));
        }
    }

    private void drawEditor(BufferedImageUiCanvas canvas, UiThemeDefinition theme,
                            int x, int y, int width, int height) {
        frame(canvas, x, y, width, height, SettingsWindowStyle.VALUE_BACKGROUND,
                SettingsWindowStyle.VALUE_BORDER, SettingsWindowStyle.VALUE_DARK_BORDER);
        canvas.text(language.text("screen.rtsbuilding.theme.editor"),
                x + EDITOR_TITLE_X, y + EDITOR_TITLE_Y, SettingsWindowStyle.VALUE);

        UiThemeToken[] tokens = UiThemeToken.values();
        int listY = y + EDITOR_LIST_Y;
        int listW = width - EDITOR_LIST_WIDTH_RESERVE;
        int pickerTop = height - WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        int visibleRows = Math.max(2, Math.min(7, (pickerTop - EDITOR_LIST_Y - 6) / 18));
        for (int row = 0; row < visibleRows && row < tokens.length; row++) {
            UiThemeToken token = tokens[row];
            int rowY = listY + row * 18;
            canvas.fill(x + EDITOR_LIST_INSET, rowY, listW, 17,
                    SettingsWindowStyle.STEP_BACKGROUND);
            canvas.fill(x + width - EDITOR_SWATCH_RIGHT, rowY + EDITOR_SWATCH_TOP,
                    EDITOR_SWATCH_RIGHT - EDITOR_SWATCH_END,
                    18 - EDITOR_SWATCH_TOP - EDITOR_SWATCH_BOTTOM, theme.color(token));
            canvas.text(language.text("screen.rtsbuilding.theme.token." + token.serializedId()),
                    x + EDITOR_LABEL_X, rowY + EDITOR_LABEL_Y, SettingsWindowStyle.VALUE);
        }

        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + height - WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        drawPicker(canvas, theme.color(UiThemeToken.ACCENT_PRIMARY), pickerX, pickerY);
    }

    private void drawPicker(BufferedImageUiCanvas canvas, UiColor selected, int x, int y) {
        canvas.image(assets.image("textures/gui/color/colorwheel.png"),
                new UiRect(x, y, WHEEL_SIZE, WHEEL_SIZE));
        float[] hsb = Color.RGBtoHSB(selected.red(), selected.green(), selected.blue(), null);
        int valueX = x + WHEEL_SIZE + VALUE_GAP;
        int fullRgb = Color.HSBtoRGB(hsb[0], hsb[1], 1.0F);
        int red = fullRgb >>> 16 & 0xFF;
        int green = fullRgb >>> 8 & 0xFF;
        int blue = fullRgb & 0xFF;
        for (int row = 0; row < WHEEL_SIZE; row++) {
            float value = 1.0F - row / (float) (WHEEL_SIZE - 1);
            canvas.fill(valueX, y + row, VALUE_W, 1,
                    UiColor.opaque(Math.round(red * value), Math.round(green * value),
                            Math.round(blue * value)));
        }
        double angle = hsb[0] * Math.PI * 2.0D;
        double radius = hsb[1] * WHEEL_SIZE * 0.46D;
        int indicatorX = (int) Math.round(x + WHEEL_SIZE / 2.0D + Math.cos(angle) * radius);
        int indicatorY = (int) Math.round(y + WHEEL_SIZE / 2.0D + Math.sin(angle) * radius);
        canvas.imageRegion(assets.image("textures/gui/color/color_palette_indicator.png"),
                new UiRect(0, 0, 72, 72),
                new UiRect(indicatorX - 3, indicatorY - 3, 7, 7));
        int valueY = y + Math.round((1.0F - hsb[2]) * (WHEEL_SIZE - 1));
        canvas.stroke(new UiRect(valueX - 2, valueY - 2, VALUE_W + 4, 5),
                new Color(SettingsWindowStyle.LABEL.toArgb(), true));
        canvas.text(String.format(java.util.Locale.ROOT, "#%08X", selected.toArgb()),
                valueX + VALUE_W + EDITOR_HEX_GAP, y + EDITOR_HEX_Y,
                SettingsWindowStyle.VALUE);
        canvas.text(language.text("screen.rtsbuilding.theme.editor.drag_hint"),
                valueX + VALUE_W + EDITOR_HEX_GAP, y + EDITOR_HINT_Y,
                SettingsWindowStyle.HINT);
    }

    private void drawActions(BufferedImageUiCanvas canvas, int x, int y, int width,
                             UiThemeDefinition draft) {
        button(canvas, x, y, 78, language.text("screen.rtsbuilding.theme.import"), true);
        button(canvas, x + ACTION_SECOND_X, y, 78,
                language.text("screen.rtsbuilding.theme.folder"), true);
        button(canvas, x + width - ACTION_EXPORT_RIGHT, y, 74,
                language.text("screen.rtsbuilding.theme.export"),
                draft.renderMode() == UiThemeRenderMode.PALETTE);
        button(canvas, x + width - ACTION_CANCEL_RIGHT, y, 74,
                "取消", true);
        button(canvas, x + width - ACTION_APPLY_RIGHT, y, 86,
                language.text("screen.rtsbuilding.theme.apply"), true);
        canvas.text(language.text("screen.rtsbuilding.theme.status.draft"),
                x, y + ACTION_STATUS_Y, SettingsWindowStyle.HINT);
    }

    private void button(BufferedImageUiCanvas canvas, int x, int y, int width,
                        String label, boolean enabled) {
        frame(canvas, x, y, width, BUTTON_H,
                enabled ? SettingsWindowStyle.STEP_BACKGROUND : SettingsWindowStyle.VALUE_BACKGROUND,
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        centered(canvas, label, x, y + ACTION_TEXT_Y, width,
                enabled ? SettingsWindowStyle.VALUE : SettingsWindowStyle.DISABLED_TEXT);
    }

    private static void frame(BufferedImageUiCanvas canvas, int x, int y, int width, int height,
                              UiColor center, UiColor light, UiColor dark) {
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, height), center, light, dark);
    }

    private static void centered(BufferedImageUiCanvas canvas, String text, int x, int topY,
                                 int width, UiColor color) {
        canvas.text(text, x + (width - canvas.textWidth(text)) / 2.0D, topY, color);
    }

    private UiThemeSettingsPreviewMain() {
    }
}
