package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 工作流行、进度条和三枚动作键的共享语义色板。
 *
 * <p>本类只解析活动/挂起、保护、暂停与悬停状态，不拥有文本、工作流命令或网络副作用。
 * 生产和离屏 renderer 共用这些结果，避免同一条工作流在两侧形成不同的警告层级。</p>
 */
public final class WorkflowStyle {
    public static final UiColor ACTIVE_BACKGROUND = new UiColor(0xAA1A222C);
    public static final UiColor ACTIVE_HOVER_BACKGROUND = new UiColor(0xAA2A3A4A);
    public static final UiColor ACTIVE_BORDER = new UiColor(0xFF5E738A);
    public static final UiColor ACTIVE_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor ACTIVE_PROGRESS_TRACK = new UiColor(0xAA202832);
    public static final UiColor ACTIVE_PROGRESS_FILL = new UiColor(0xFF88BEF4);
    public static final UiColor ACTIVE_PROGRESS_BORDER = new UiColor(0xFF405064);
    public static final UiColor ACTIVE_DARK_BORDER = new UiColor(0xFF0D1117);
    public static final UiColor ACTIVE_PROGRESS_DARK_BORDER = new UiColor(0xFF0A0D12);
    public static final UiColor ACTIVE_PROGRESS_TEXT = new UiColor(0xCCFFFFFF);

    public static final UiColor SUSPENDED_BACKGROUND = new UiColor(0xAA2A2820);
    public static final UiColor SUSPENDED_HOVER_BACKGROUND = new UiColor(0xAA4A3A1A);
    public static final UiColor SUSPENDED_BORDER = new UiColor(0xFF8A7A4A);
    public static final UiColor SUSPENDED_TEXT = new UiColor(0xFFE7C46A);
    public static final UiColor SUSPENDED_PROGRESS_TRACK = new UiColor(0xAA303030);
    public static final UiColor SUSPENDED_PROGRESS_FILL = new UiColor(0xAA8A7A3A);
    public static final UiColor SUSPENDED_PROGRESS_BORDER = new UiColor(0xFF5A4A2A);
    public static final UiColor SUSPENDED_DARK_BORDER = new UiColor(0xFF0D0D0A);
    public static final UiColor SUSPENDED_PROGRESS_DARK_BORDER = new UiColor(0xFF0A0A05);
    public static final UiColor SUSPENDED_PROGRESS_TEXT = new UiColor(0xAAFFFFFF);

    public static final UiColor PROTECTED_BACKGROUND = new UiColor(0xAA315B70);
    public static final UiColor PROTECTED_HOVER_BACKGROUND = new UiColor(0xBB3F6E86);
    public static final UiColor PROTECTED_BORDER = new UiColor(0xFFA8E8FF);
    public static final UiColor PROTECTED_TEXT = new UiColor(0xFFEAFBFF);
    public static final UiColor PROTECTED_PROGRESS_FILL = new UiColor(0xDDA8E8FF);
    public static final UiColor PROTECTED_PROGRESS_BORDER = new UiColor(0xFF70B8D0);

    public static final UiColor PROTECT_IDLE_BACKGROUND = new UiColor(0xAA263442);
    public static final UiColor PROTECT_IDLE_HOVER_BACKGROUND = new UiColor(0xAA3A4A5A);
    public static final UiColor PROTECT_ACTIVE_BACKGROUND = new UiColor(0xCC4DAFD8);
    public static final UiColor PROTECT_ACTIVE_HOVER_BACKGROUND = new UiColor(0xD36FC7E8);
    public static final UiColor PROTECT_IDLE_TEXT = new UiColor(0xFFDDEBFF);
    public static final UiColor BUTTON_TEXT = new UiColor(0xFFFFFFFF);

    public static final UiColor RESUME_BACKGROUND = new UiColor(0xCC2C873F);
    public static final UiColor RESUME_HOVER_BACKGROUND = new UiColor(0xCC3AA156);
    public static final UiColor RESUME_BORDER = new UiColor(0xFF74E88C);
    public static final UiColor RESUME_DARK_BORDER = new UiColor(0xFF1A2A1A);
    public static final UiColor SUSPENDED_RESUME_DARK_BORDER = new UiColor(0xFF123A1D);
    public static final UiColor PAUSE_BACKGROUND = new UiColor(0xCC705A1A);
    public static final UiColor PAUSE_HOVER_BACKGROUND = new UiColor(0xCCA07A2A);
    public static final UiColor PAUSE_BORDER = new UiColor(0xFFE7C46A);
    public static final UiColor DELETE_BACKGROUND = new UiColor(0xAA4A2A2A);
    public static final UiColor DELETE_HOVER_BACKGROUND = new UiColor(0xCCB04A4A);
    public static final UiColor DELETE_BORDER = new UiColor(0xFFC07070);
    public static final UiColor DELETE_DARK_BORDER = new UiColor(0xFF1A0D0D);

    private WorkflowStyle() {
    }

    public static RowVisual row(
            boolean suspended,
            boolean protectedWorkflow,
            boolean hovered) {
        UiColor dark = suspended
                ? SUSPENDED_DARK_BORDER
                : ACTIVE_DARK_BORDER;
        UiColor progressTrack = suspended
                ? SUSPENDED_PROGRESS_TRACK
                : ACTIVE_PROGRESS_TRACK;
        UiColor progressDark = suspended
                ? SUSPENDED_PROGRESS_DARK_BORDER
                : ACTIVE_PROGRESS_DARK_BORDER;
        UiColor progressText = suspended
                ? SUSPENDED_PROGRESS_TEXT
                : ACTIVE_PROGRESS_TEXT;
        if (protectedWorkflow) {
            return new RowVisual(
                    hovered
                            ? PROTECTED_HOVER_BACKGROUND
                            : PROTECTED_BACKGROUND,
                    PROTECTED_BORDER,
                    dark,
                    PROTECTED_TEXT,
                    progressTrack,
                    PROTECTED_PROGRESS_FILL,
                    PROTECTED_PROGRESS_BORDER,
                    progressDark,
                    progressText);
        }
        return new RowVisual(
                suspended
                        ? (hovered
                                ? SUSPENDED_HOVER_BACKGROUND
                                : SUSPENDED_BACKGROUND)
                        : (hovered
                                ? ACTIVE_HOVER_BACKGROUND
                                : ACTIVE_BACKGROUND),
                suspended ? SUSPENDED_BORDER : ACTIVE_BORDER,
                dark,
                suspended ? SUSPENDED_TEXT : ACTIVE_TEXT,
                progressTrack,
                suspended
                        ? SUSPENDED_PROGRESS_FILL
                        : ACTIVE_PROGRESS_FILL,
                suspended
                        ? SUSPENDED_PROGRESS_BORDER
                        : ACTIVE_PROGRESS_BORDER,
                progressDark,
                progressText);
    }

    public static ButtonVisual protect(
            boolean protectedWorkflow,
            boolean hovered) {
        return new ButtonVisual(
                protectedWorkflow
                        ? (hovered
                                ? PROTECT_ACTIVE_HOVER_BACKGROUND
                                : PROTECT_ACTIVE_BACKGROUND)
                        : (hovered
                                ? PROTECT_IDLE_HOVER_BACKGROUND
                                : PROTECT_IDLE_BACKGROUND),
                protectedWorkflow ? PROTECTED_BORDER : ACTIVE_BORDER,
                ACTIVE_DARK_BORDER,
                protectedWorkflow ? BUTTON_TEXT : PROTECT_IDLE_TEXT);
    }

    public static ButtonVisual action(
            boolean suspended,
            boolean paused,
            boolean hovered) {
        boolean resume = suspended || paused;
        return new ButtonVisual(
                resume
                        ? (hovered
                                ? RESUME_HOVER_BACKGROUND
                                : RESUME_BACKGROUND)
                        : (hovered
                                ? PAUSE_HOVER_BACKGROUND
                                : PAUSE_BACKGROUND),
                resume ? RESUME_BORDER : PAUSE_BORDER,
                suspended
                        ? SUSPENDED_RESUME_DARK_BORDER
                        : RESUME_DARK_BORDER,
                BUTTON_TEXT);
    }

    public static ButtonVisual delete(boolean hovered) {
        return new ButtonVisual(
                hovered
                        ? DELETE_HOVER_BACKGROUND
                        : DELETE_BACKGROUND,
                DELETE_BORDER,
                DELETE_DARK_BORDER,
                BUTTON_TEXT);
    }

    public static final class RowVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor darkBorder;
        public final UiColor labelText;
        public final UiColor progressTrack;
        public final UiColor progressFill;
        public final UiColor progressBorder;
        public final UiColor progressDarkBorder;
        public final UiColor progressText;

        private RowVisual(
                UiColor background,
                UiColor border,
                UiColor darkBorder,
                UiColor labelText,
                UiColor progressTrack,
                UiColor progressFill,
                UiColor progressBorder,
                UiColor progressDarkBorder,
                UiColor progressText) {
            this.background = background;
            this.border = border;
            this.darkBorder = darkBorder;
            this.labelText = labelText;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
            this.progressBorder = progressBorder;
            this.progressDarkBorder = progressDarkBorder;
            this.progressText = progressText;
        }
    }

    public static final class ButtonVisual {
        public final UiColor background;
        public final UiColor border;
        public final UiColor darkBorder;
        public final UiColor text;

        private ButtonVisual(
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
