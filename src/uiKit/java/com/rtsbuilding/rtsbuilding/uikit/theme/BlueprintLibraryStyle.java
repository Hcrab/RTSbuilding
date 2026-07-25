package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底部蓝图库的共享语义色板。
 *
 * <p>本类只解释搜索、卡片、损坏条目、材料完成度和动作状态，不拥有本地化文本、
 * 文件 IO、真实 ItemStack 或蓝图选择状态机。</p>
 */
public final class BlueprintLibraryStyle {
    public static final UiColor FRAME_BACKGROUND = color(0x8811161E);
    public static final UiColor FRAME_BORDER = color(0xFF415266);
    public static final UiColor FRAME_DARK_BORDER = color(0xFF0B0E13);

    public static final UiColor BUTTON_BACKGROUND = color(0xAA24303C);
    public static final UiColor BUTTON_HOVER_BACKGROUND = color(0xCC334052);
    public static final UiColor BUTTON_ACTIVE_BACKGROUND = color(0xCC2E6A50);
    public static final UiColor BUTTON_BORDER = color(0xFF64788E);
    public static final UiColor BUTTON_DARK_BORDER = color(0xFF0D1015);
    public static final UiColor BUTTON_TEXT = color(0xFFEAF2FF);

    public static final UiColor SEARCH_BACKGROUND = color(0xAA111820);
    public static final UiColor SEARCH_FOCUSED_BACKGROUND = color(0xCC09111B);
    public static final UiColor SEARCH_BORDER = color(0xFF6B8095);
    public static final UiColor SEARCH_DARK_BORDER = color(0xFF0C1118);
    public static final UiColor SEARCH_PLACEHOLDER_TEXT = color(0x8898A8B8);
    public static final UiColor SEARCH_TEXT = BUTTON_TEXT;

    public static final UiColor ROW_IDLE_BACKGROUND = color(0x7731363E);
    public static final UiColor ROW_READY_BACKGROUND = color(0x77253832);
    public static final UiColor ROW_HOVER_BACKGROUND = color(0xAA2B3542);
    public static final UiColor ROW_SELECTED_BACKGROUND = color(0xCC2E654B);
    public static final UiColor ROW_INVALID_BACKGROUND = color(0x77503A36);
    public static final UiColor ROW_INVALID_SELECTED_BACKGROUND = color(0xCC694238);
    public static final UiColor ROW_NAME_TEXT = BUTTON_TEXT;
    public static final UiColor ROW_INVALID_TEXT = color(0xFFFFB0A0);
    public static final UiColor ROW_SIZE_TEXT = color(0xFF8FA2B7);
    public static final UiColor ROW_PERCENT_READY_TEXT = color(0xFF9BE6A5);
    public static final UiColor ROW_PERCENT_TEXT = color(0xFF9CA6B2);

    public static final UiColor PROGRESS_TRACK = color(0xAA0C1118);
    public static final UiColor PROGRESS_READY = color(0xFF62D77A);
    public static final UiColor PROGRESS_PARTIAL = color(0xFFE4B04D);

    public static final UiColor PRIMARY_TEXT = BUTTON_TEXT;
    public static final UiColor SECONDARY_TEXT = color(0xFF9EACB9);
    public static final UiColor INVALID_TEXT = color(0xFFFFA0A0);
    public static final UiColor READY_TEXT = color(0xFF8EEA9B);
    public static final UiColor WARNING_TEXT = color(0xFFFFC06C);
    public static final UiColor STATUS_DEFAULT_TEXT = color(0xFFB8C7D6);
    public static final UiColor STATUS_SUCCESS_TEXT = color(0xFF81E58E);
    public static final UiColor STATUS_ERROR_TEXT = color(0xFFFF8A8A);
    public static final UiColor CAPTURE_WARNING_TEXT = WARNING_TEXT;
    public static final UiColor PREVIEW_SLOT_BACKGROUND = color(0xAA1A2029);

    private BlueprintLibraryStyle() {
    }

    public static FrameVisual button(
            boolean hovered,
            boolean active) {
        return new FrameVisual(
                active
                        ? BUTTON_ACTIVE_BACKGROUND
                        : hovered
                                ? BUTTON_HOVER_BACKGROUND
                                : BUTTON_BACKGROUND,
                BUTTON_BORDER,
                BUTTON_DARK_BORDER,
                BUTTON_TEXT);
    }

    public static FrameVisual search(boolean focused) {
        return new FrameVisual(
                focused
                        ? SEARCH_FOCUSED_BACKGROUND
                        : SEARCH_BACKGROUND,
                SEARCH_BORDER,
                SEARCH_DARK_BORDER,
                focused ? SEARCH_TEXT : SEARCH_PLACEHOLDER_TEXT);
    }

    public static UiColor rowBackground(
            boolean valid,
            boolean ready,
            boolean selected,
            boolean hovered) {
        if (!valid) {
            return selected
                    ? ROW_INVALID_SELECTED_BACKGROUND
                    : ROW_INVALID_BACKGROUND;
        }
        if (selected) {
            return ROW_SELECTED_BACKGROUND;
        }
        if (hovered) {
            return ROW_HOVER_BACKGROUND;
        }
        return ready ? ROW_READY_BACKGROUND : ROW_IDLE_BACKGROUND;
    }

    public static UiColor progress(boolean ready) {
        return ready ? PROGRESS_READY : PROGRESS_PARTIAL;
    }

    private static UiColor color(int argb) {
        return new UiColor(argb);
    }

    public static final class FrameVisual {
        public final UiColor background, border, darkBorder, text;

        private FrameVisual(
                UiColor background,
                UiColor border,
                UiColor darkBorder,
                UiColor text) {
            this.background = background;
            this.border = border;
            this.darkBorder = darkBorder;
            this.text = text;
        }
    }
}
