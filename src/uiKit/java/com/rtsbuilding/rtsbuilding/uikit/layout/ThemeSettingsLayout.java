package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 主题选择、预览与色轮编辑器共享的稳定几何常量。
 *
 * <p>生产绘制和命中必须共同引用这些值，避免主题窗口再次出现“画在一处、点在另一处”的私有坐标债务。</p>
 */
public final class ThemeSettingsLayout {
    public static final int PREFERRED_WINDOW_W = 700;
    public static final int MIN_WINDOW_W = 520;
    public static final int SCREEN_EDGE_RESERVE = 16;
    public static final int PREFERRED_WINDOW_H = 330;
    public static final int MIN_WINDOW_H = 260;
    public static final int SCREEN_VERTICAL_RESERVE = 64;
    public static final int OUTER_INSET = 8;
    public static final int LIST_INSET = 6;
    public static final int DOUBLE_LIST_INSET = 12;
    public static final int LIST_FOOTER_RESERVE = 54;
    public static final int BODY_FOOTER_RESERVE = 62;
    public static final int FOOTER_TOP_RESERVE = 48;
    public static final int COLUMN_GAP = 10;
    public static final int COLUMN_WIDTH_RESERVE = 46;
    public static final int THEME_LIST_W = 150;
    public static final int THEME_EDITOR_W = 190;

    public static final int THEME_NAME_X = 8;
    public static final int THEME_NAME_Y = 6;
    public static final int THEME_MODE_Y = 18;
    public static final int THEME_ROW_BOTTOM = 3;
    public static final int PREVIEW_INSET = 8;
    public static final int PREVIEW_TITLE_X = 16;
    public static final int PREVIEW_TITLE_Y = 17;
    public static final int PREVIEW_CANVAS_Y = 42;
    public static final int PREVIEW_CONTROL_Y = 54;
    public static final int PREVIEW_CONTROL_START_X = 12;
    public static final int PREVIEW_CONTROL_GAP = 6;
    public static final int PREVIEW_CONTROL_WIDTH_RESERVE = 52;
    public static final int PREVIEW_CONTROL_COUNT = 3;
    public static final int PREVIEW_CONTROL_MIN_W = 42;
    public static final int PREVIEW_CONTROL_H = 24;
    public static final int PREVIEW_SLOT_START_X = 18;
    public static final int PREVIEW_SLOT_PITCH = 34;
    public static final int PREVIEW_SLOT_SIZE = 28;
    public static final int PREVIEW_SLOT_WIDTH_RESERVE = 52;
    public static final int PREVIEW_SLOT_MIN_COUNT = 3;
    public static final int PREVIEW_SLOT_MAX_COUNT = 6;
    public static final int PREVIEW_SLOT_HOVER_INDEX = 2;
    public static final int PREVIEW_SCROLL_TRACK_RIGHT = 28;
    public static final int PREVIEW_SCROLL_TRACK_END = 22;
    public static final int PREVIEW_SCROLL_THUMB_RIGHT = 29;
    public static final int PREVIEW_SCROLL_THUMB_END = 21;
    public static final int PREVIEW_SCROLL_H = 78;
    public static final int PREVIEW_SCROLL_THUMB_Y = 18;
    public static final int PREVIEW_SCROLL_THUMB_END_Y = 47;
    public static final int PREVIEW_STATUS_X = 18;
    public static final int PREVIEW_STATUS_RIGHT = 42;
    public static final int PREVIEW_STATUS_H = 38;
    public static final int PREVIEW_STATUS_TEXT_X = 26;
    public static final int PREVIEW_STATUS_PRIMARY_Y = 7;
    public static final int PREVIEW_STATUS_SECONDARY_Y = 20;
    public static final int PREVIEW_CHIP_PITCH = 42;
    public static final int PREVIEW_CHIP_END_X = 52;
    public static final int PREVIEW_CHIP_H = 10;
    public static final int SAMPLE_TEXT_Y = 8;

    public static final int ACTION_IMPORT_W = 58;
    public static final int ACTION_FOLDER_W = 64;
    public static final int ACTION_SECOND_X = 64;
    public static final int ACTION_EXPORT_RIGHT = 246;
    public static final int ACTION_CANCEL_RIGHT = 166;
    public static final int ACTION_APPLY_RIGHT = 86;
    public static final int ACTION_STATUS_Y = 30;
    public static final int ACTION_STATUS_MAX_LINES = 2;

    public static final int EDITOR_TITLE_X = 8;
    public static final int EDITOR_TITLE_Y = 7;
    public static final int EDITOR_LEGACY_Y = 25;
    public static final int EDITOR_LIST_Y = 22;
    public static final int EDITOR_LIST_INSET = 6;
    public static final int EDITOR_LIST_WIDTH_RESERVE = 12;
    public static final int EDITOR_ROW_BOTTOM = 1;
    public static final int EDITOR_SWATCH_RIGHT = 30;
    public static final int EDITOR_SWATCH_END = 12;
    public static final int EDITOR_SWATCH_TOP = 3;
    public static final int EDITOR_SWATCH_BOTTOM = 4;
    public static final int EDITOR_LABEL_WIDTH_RESERVE = 52;
    public static final int EDITOR_LABEL_X = 10;
    public static final int EDITOR_LABEL_Y = 5;
    public static final int EDITOR_PICKER_INSET = 12;
    public static final int EDITOR_PICKER_BOTTOM = 12;
    public static final int EDITOR_HEX_GAP = 8;
    public static final int EDITOR_HEX_Y = 4;
    public static final int EDITOR_HINT_Y = 19;
    public static final int EDITOR_TEXT_RIGHT_INSET = 6;

    /** 高 GUI 缩放下让主题窗口收进屏幕，同时保留三栏可用的硬下限。 */
    public static int preferredWindowWidth(int screenWidth) {
        return Math.max(MIN_WINDOW_W,
                Math.min(PREFERRED_WINDOW_W, screenWidth - SCREEN_EDGE_RESERVE));
    }

    public static int preferredWindowHeight(int screenHeight) {
        return Math.max(MIN_WINDOW_H,
                Math.min(PREFERRED_WINDOW_H, screenHeight - SCREEN_VERTICAL_RESERVE));
    }

    /** 按真实字体行高垂直居中底部按钮文字，避免预览与游戏各自维护偏移量。 */
    public static int actionTextTop(int buttonY, int buttonHeight, int lineHeight) {
        return buttonY + Math.max(0, (buttonHeight - lineHeight) / 2);
    }

    /** 生产绘制、命中和离屏预览共同使用的三栏及底部动作区几何。 */
    public static Geometry geometry(int contentX, int contentY, int contentWidth, int contentHeight) {
        int x = contentX + OUTER_INSET;
        int y = contentY + OUTER_INSET;
        int height = contentHeight - OUTER_INSET * 2;
        int previewX = x + THEME_LIST_W + COLUMN_GAP;
        int previewWidth = contentWidth - THEME_LIST_W - THEME_EDITOR_W - COLUMN_WIDTH_RESERVE;
        int bodyHeight = height - BODY_FOOTER_RESERVE;
        return new Geometry(
                new UiRect(x, y, THEME_LIST_W, height),
                new UiRect(previewX, y, previewWidth, bodyHeight),
                new UiRect(previewX + previewWidth + COLUMN_GAP, y, THEME_EDITOR_W, bodyHeight),
                new UiRect(x, y + height - FOOTER_TOP_RESERVE,
                        contentWidth - OUTER_INSET * 2, BUTTON_AREA_HEIGHT));
    }

    private static final int BUTTON_AREA_HEIGHT = 42;

    public static final class Geometry {
        public final UiRect list;
        public final UiRect preview;
        public final UiRect editor;
        public final UiRect actions;

        private Geometry(UiRect list, UiRect preview, UiRect editor, UiRect actions) {
            this.list = list;
            this.preview = preview;
            this.editor = editor;
            this.actions = actions;
        }
    }

    private ThemeSettingsLayout() {
    }
}
