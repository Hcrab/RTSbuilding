package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiStatus;

/**
 * 绑定储存详情的共享语义色板。
 *
 * <p>这里只解释加载状态、行 hover、优先级、仅提取和解绑状态，不拥有文本、ItemStack、
 * EditBox 或网络副作用。</p>
 */
public final class StorageWindowStyle {
    public static final UiColor HEADER_TEXT = color(0xFFD8E3EE);
    public static final UiColor COLUMN_TEXT = color(0xFF9FB3C8);
    public static final UiColor ROW_LABEL_TEXT = color(0xFFEAF2FF);
    public static final UiColor ROW_POSITION_TEXT = COLUMN_TEXT;
    public static final UiColor STATUS_WARNING_TEXT = color(0xFFFFD480);
    public static final UiColor STATUS_FAILED_TEXT = color(0xFFFF9AA8);
    public static final UiColor STATUS_DETAIL_TEXT = color(0xFFBFD0E0);

    public static final UiColor ROW_BACKGROUND = color(0xAA1A222D);
    public static final UiColor ROW_HOVER_BACKGROUND = color(0xCC243244);
    public static final UiColor ROW_BORDER = color(0xFF566D83);
    public static final UiColor ROW_DARK_BORDER = color(0xFF0D1117);

    public static final UiColor PLACEHOLDER_BACKGROUND = color(0xAA101820);
    public static final UiColor PLACEHOLDER_BORDER = ROW_BORDER;

    public static final UiColor PRIORITY_BACKGROUND = color(0xAA101820);
    public static final UiColor PRIORITY_HOVER_BACKGROUND = color(0xCC26394A);
    public static final UiColor PRIORITY_HOVER_BORDER = color(0xFF8EA9C4);
    public static final UiColor PRIORITY_TEXT = ROW_LABEL_TEXT;

    public static final UiColor EXTRACT_IDLE_BACKGROUND = ROW_BACKGROUND;
    public static final UiColor EXTRACT_IDLE_HOVER_BACKGROUND =
            PRIORITY_HOVER_BACKGROUND;
    public static final UiColor EXTRACT_ACTIVE_BACKGROUND = color(0xFF4A253F);
    public static final UiColor EXTRACT_ACTIVE_HOVER_BACKGROUND =
            color(0xFF5A2D50);
    public static final UiColor EXTRACT_ACTIVE_BORDER = color(0xFFFF74C9);
    public static final UiColor EXTRACT_ACTIVE_HOVER_BORDER =
            color(0xFFFF9DDE);
    public static final UiColor EXTRACT_IDLE_TEXT = color(0xFFCDE7D2);
    public static final UiColor EXTRACT_ACTIVE_TEXT = color(0xFFFFECFA);

    public static final UiColor UNLINK_BACKGROUND = color(0xAA2A2228);
    public static final UiColor UNLINK_HOVER_BACKGROUND = color(0xCC5A2B34);
    public static final UiColor UNLINK_BORDER = color(0xFF7B5660);
    public static final UiColor UNLINK_HOVER_BORDER = color(0xFFE28A96);
    public static final UiColor UNLINK_DARK_BORDER = color(0xFF180B0E);
    public static final UiColor UNLINK_TEXT = color(0xFFFFF0F0);

    public static final UiColor SCROLLBAR_TRACK = PRIORITY_BACKGROUND;
    public static final UiColor SCROLLBAR_INSET = color(0x88303B47);
    public static final UiColor SCROLLBAR_THUMB = PRIORITY_HOVER_BORDER;

    private StorageWindowStyle() {
    }

    public static UiColor statusText(StorageUiStatus status) {
        return status == StorageUiStatus.FAILED
                ? STATUS_FAILED_TEXT
                : STATUS_WARNING_TEXT;
    }

    public static FrameVisual row(boolean hovered) {
        return row(hovered ? 1.0D : 0.0D);
    }

    public static FrameVisual row(double hoverStrength) {
        return new FrameVisual(
                UiColor.interpolate(ROW_BACKGROUND, ROW_HOVER_BACKGROUND, hoverStrength),
                ROW_BORDER,
                ROW_DARK_BORDER,
                ROW_LABEL_TEXT);
    }

    public static FrameVisual priority(boolean hovered) {
        return priority(hovered ? 1.0D : 0.0D);
    }

    public static FrameVisual priority(double hoverStrength) {
        return new FrameVisual(
                UiColor.interpolate(PRIORITY_BACKGROUND,
                        PRIORITY_HOVER_BACKGROUND, hoverStrength),
                UiColor.interpolate(ROW_BORDER,
                        PRIORITY_HOVER_BORDER, hoverStrength),
                ROW_DARK_BORDER,
                PRIORITY_TEXT);
    }

    public static FrameVisual extract(
            boolean extractOnly,
            boolean hovered) {
        return extract(extractOnly, hovered ? 1.0D : 0.0D);
    }

    public static FrameVisual extract(
            boolean extractOnly,
            double hoverStrength) {
        if (extractOnly) {
            return new FrameVisual(
                    UiColor.interpolate(EXTRACT_ACTIVE_BACKGROUND,
                            EXTRACT_ACTIVE_HOVER_BACKGROUND, hoverStrength),
                    UiColor.interpolate(EXTRACT_ACTIVE_BORDER,
                            EXTRACT_ACTIVE_HOVER_BORDER, hoverStrength),
                    ROW_DARK_BORDER,
                    EXTRACT_ACTIVE_TEXT);
        }
        return new FrameVisual(
                UiColor.interpolate(EXTRACT_IDLE_BACKGROUND,
                        EXTRACT_IDLE_HOVER_BACKGROUND, hoverStrength),
                UiColor.interpolate(ROW_BORDER,
                        PRIORITY_HOVER_BORDER, hoverStrength),
                ROW_DARK_BORDER,
                EXTRACT_IDLE_TEXT);
    }

    public static FrameVisual unlink(boolean hovered) {
        return unlink(hovered ? 1.0D : 0.0D);
    }

    public static FrameVisual unlink(double hoverStrength) {
        return new FrameVisual(
                UiColor.interpolate(UNLINK_BACKGROUND,
                        UNLINK_HOVER_BACKGROUND, hoverStrength),
                UiColor.interpolate(UNLINK_BORDER,
                        UNLINK_HOVER_BORDER, hoverStrength),
                UNLINK_DARK_BORDER,
                UNLINK_TEXT);
    }

    private static UiColor color(int argb) {
        return new UiColor(argb);
    }

    public static final class FrameVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor darkBorder;
        public final UiColor text;

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
