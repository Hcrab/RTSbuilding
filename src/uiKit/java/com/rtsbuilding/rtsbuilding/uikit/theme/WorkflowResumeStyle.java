package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 普通放置恢复窗与蓝图材料恢复窗的共享语义色。
 *
 * <p>颜色只表达统计、材料充足性和恢复策略，不拥有扫描结果或网络命令。</p>
 */
public final class WorkflowResumeStyle {
    public static final UiColor DIVIDER = color(0xFF405064);
    public static final UiColor LABEL_TEXT = color(0xFFB0C0D0);
    public static final UiColor PRIMARY_TEXT = color(0xFFEAF2FF);
    public static final UiColor SECONDARY_TEXT = color(0xFF88BEF4);
    public static final UiColor PROGRESS_TEXT = color(0xFFE7C46A);
    public static final UiColor SUCCESS_TEXT = color(0xFF88F4BE);
    public static final UiColor WARNING_TEXT = color(0xFFFFC070);
    public static final UiColor ERROR_TEXT = color(0xFFFF7070);
    public static final UiColor ITEM_TEXT = color(0xFFFFFFFF);
    public static final UiColor DISABLED_TEXT = color(0xFF888888);

    public static final UiColor PLACEHOLDER_BACKGROUND = color(0xAA101820);
    public static final UiColor PLACEHOLDER_BORDER = color(0xFF566D83);

    private WorkflowResumeStyle() {
    }

    public static ActionVisual action(
            ActionKind kind,
            boolean enabled,
            boolean hovered) {
        return action(kind, enabled, hovered ? 1.0D : 0.0D);
    }

    public static ActionVisual action(
            ActionKind kind,
            boolean enabled,
            double hoverStrength) {
        if (!enabled) {
            return new ActionVisual(
                    color(0xCC444444),
                    color(0xFF666666),
                    kind == ActionKind.OVERWRITE
                            ? color(0xFF1A1A1A)
                            : color(0xFF1A2A1A),
                    DISABLED_TEXT);
        }
        if (kind == ActionKind.OVERWRITE) {
            return new ActionVisual(
                    UiColor.interpolate(color(0xCC4A3A1A),
                            color(0xCC6A4A2A), hoverStrength),
                    PROGRESS_TEXT,
                    color(0xFF2A1A0A),
                    ITEM_TEXT);
        }
        if (kind == ActionKind.SKIP) {
            return new ActionVisual(
                    UiColor.interpolate(color(0xCC2A4A2A),
                            color(0xCC3A6A3A), hoverStrength),
                    color(0xFF74E88C),
                    color(0xFF1A2A1A),
                    ITEM_TEXT);
        }
        return new ActionVisual(
                UiColor.interpolate(color(0xCC2C873F),
                        color(0xCC3AA156), hoverStrength),
                color(0xFF74E88C),
                color(0xFF1A2A1A),
                ITEM_TEXT);
    }

    public enum ActionKind {
        RESUME,
        SKIP,
        OVERWRITE
    }

    public static final class ActionVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor darkBorder;
        public final UiColor text;

        private ActionVisual(
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

    private static UiColor color(int argb) {
        return new UiColor(argb);
    }
}
