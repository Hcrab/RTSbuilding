package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 世界空间覆盖层的语义颜色边界。
 *
 * <p>Legacy Direct 保留每个渲染器原先的精确 RGB；Palette 模式把同类反馈合并到正式世界令牌。
 * 渲染器仍自行拥有透明度、线宽和动画，不把空间交互语义倒灌进通用主题系统。</p>
 */
public final class UiThemeWorldColors {
    private static final UiThemeCoverageCatalog.ComponentFamily FAMILY =
            UiThemeCoverageCatalog.ComponentFamily.WORLD_RENDERING;

    public static final UiColor STORAGE_LINK = color(UiThemeToken.WORLD_LINK_ENDPOINT, 0xFF3D8CFF);
    public static final UiColor STORAGE_EXTRACT = color(UiThemeToken.ACCENT_SECONDARY, 0xFFFF4DD1);
    public static final UiColor BLUEPRINT_VALID = color(UiThemeToken.WORLD_GHOST_VALID, 0xFF59F2B8);
    public static final UiColor BLUEPRINT_INVALID = color(UiThemeToken.WORLD_GHOST_INVALID, 0xFFFFB838);
    public static final UiColor BLUEPRINT_MISSING = color(UiThemeToken.WORLD_INVALID, 0xFFFF4040);
    public static final UiColor CULLING_IDLE = color(UiThemeToken.WORLD_SELECTION, 0xFF338FFF);
    public static final UiColor CULLING_HOVER = color(UiThemeToken.WARNING, 0xFFFFD129);
    public static final UiColor CULLING_SELECTED = color(UiThemeToken.ACCENT_PRIMARY, 0xFF8FD6FF);
    public static final UiColor MOVE_TARGET = color(UiThemeToken.WORLD_SELECTION, 0xFF2994FF);
    public static final UiColor BUILD_PREVIEW = color(UiThemeToken.WORLD_GHOST_VALID, 0xFF4CBFFF);
    public static final UiColor CAPTURE_INCLUDED = color(UiThemeToken.WORLD_SELECTION, 0xFF1F8FFF);
    public static final UiColor CAPTURE_EXCLUDED = color(UiThemeToken.WORLD_INVALID, 0xFFFF5C1F);
    public static final UiColor CAPTURE_BOUNDARY = color(UiThemeToken.WORLD_SELECTION, 0xFF59C7FF);
    public static final UiColor PENDING_GHOST = color(UiThemeToken.WORLD_GHOST_VALID, 0xFF4CBFFF);
    public static final UiColor ULTIMINE_BLOCK = color(UiThemeToken.WARNING, 0xFFFFB83D);
    public static final UiColor ULTIMINE_ENTITY = color(UiThemeToken.WORLD_SELECTION, 0xFF80CCFF);
    public static final UiColor DESTRUCTIVE_PENDING = color(UiThemeToken.WORLD_INVALID, 0xFFFF75A3);
    public static final UiColor DESTRUCTIVE_READY = color(UiThemeToken.WARNING, 0xFFFFF273);
    public static final UiColor DESTRUCTIVE_PENDING_FILL = color(UiThemeToken.WORLD_INVALID, 0xFFFF4070);
    public static final UiColor DESTRUCTIVE_READY_FILL = color(UiThemeToken.WARNING, 0xFFFFB83D);
    public static final UiColor DESTRUCTIVE_COMPLETE = color(UiThemeToken.SUCCESS, 0xFF61FF6B);
    public static final UiColor DESTRUCTIVE_COMPLETE_FILL = color(UiThemeToken.SUCCESS, 0xFF4CF25C);
    public static final UiColor DESTRUCTIVE_ENVELOPE_START = color(UiThemeToken.WARNING, 0xFFFFDB38);
    public static final UiColor DESTRUCTIVE_ENVELOPE_START_FILL = color(UiThemeToken.WARNING, 0xFFFFDB2E);
    public static final UiColor BUILD_READY = color(UiThemeToken.SUCCESS, 0xFF73F273);
    public static final UiColor SHAPE_SELECTION = color(UiThemeToken.WORLD_SELECTION, 0xFF4CBFFF);
    public static final UiColor CHUNK_GUIDE_PRIMARY = color(UiThemeToken.WORLD_SELECTION, 0xFF29C7FF);
    public static final UiColor CHUNK_GUIDE_SECONDARY = color(UiThemeToken.WARNING, 0xFFFFE029);
    public static final UiColor PLACEMENT_CONFIRMED = color(UiThemeToken.WORLD_GHOST_VALID, 0xFF4CD9FF);
    public static final UiColor PLACEMENT_CONFIRMED_FILL = color(UiThemeToken.WORLD_GHOST_VALID, 0xFF66D9E6);
    public static final UiColor DESTROY_CONFIRMED = color(UiThemeToken.SUCCESS, 0xFF61FF6B);
    public static final UiColor DESTROY_CONFIRMED_FILL = color(UiThemeToken.SUCCESS, 0xFF4CF25C);
    public static final UiColor INTERACTION_ENTITY = color(UiThemeToken.WORLD_SELECTION, 0xFF80CCFF);
    public static final UiColor INTERACTION_BLOCK = color(UiThemeToken.WARNING, 0xFFF79B31);
    public static final UiColor INTERACTION_NEAR = color(UiThemeToken.ACCENT_PRIMARY, 0xFFFFE621);
    public static final UiColor AXIS_X = color(UiThemeToken.WORLD_AXIS_X, 0xFFFF5752);
    public static final UiColor AXIS_Y = color(UiThemeToken.WORLD_AXIS_Y, 0xFF5CFF6B);
    public static final UiColor AXIS_Z = color(UiThemeToken.WORLD_AXIS_Z, 0xFF61A3FF);
    public static final UiColor HANDLE_ACTIVE = color(UiThemeToken.WORLD_HANDLE_ACTIVE, 0xFFFFC72E);

    public static float red(UiColor color) { return color.red() / 255.0F; }
    public static float green(UiColor color) { return color.green() / 255.0F; }
    public static float blue(UiColor color) { return color.blue() / 255.0F; }

    private static UiColor color(UiThemeToken token, int legacyArgb) {
        return UiColor.themeComponent(FAMILY, token, legacyArgb);
    }

    private UiThemeWorldColors() {
    }
}
