package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 将小型索引纹理确定性地烘焙为某一主题状态；不依赖 Minecraft 或 GPU。 */
public final class UiPaletteTextureBaker {
    public static int[] bake(int[] sourceArgb, UiIndexedTextureSpec spec,
                             UiThemeDefinition theme, UiTextureState state) {
        if (sourceArgb == null) throw new IllegalArgumentException("sourceArgb must not be null");
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        if (theme == null) throw new IllegalArgumentException("theme must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (theme.renderMode() != UiThemeRenderMode.PALETTE) {
            throw new IllegalArgumentException("Legacy Direct theme must never enter the baker");
        }

        int[] result = new int[sourceArgb.length];
        for (int index = 0; index < sourceArgb.length; index++) {
            int source = sourceArgb[index];
            int sourceAlpha = source >>> 24;
            if (sourceAlpha == 0) {
                result[index] = 0;
                continue;
            }
            UiIndexedTextureSpec.Role role = spec.role(source);
            if (role == null) {
                throw new IllegalArgumentException(String.format(
                        "unknown indexed texture pixel #%08X at %d", source, index));
            }
            UiColor color = roleColor(theme, state, role);
            int combinedAlpha = sourceAlpha * color.alpha() / 255;
            result[index] = combinedAlpha << 24 | color.toArgb() & 0x00FFFFFF;
        }
        return result;
    }

    private static UiColor roleColor(UiThemeDefinition theme, UiTextureState state,
                                     UiIndexedTextureSpec.Role role) {
        switch (role) {
            case BACKGROUND:
                return theme.color(backgroundToken(state));
            case DARK_EDGE:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ACCENT_SECONDARY)
                        : theme.color(UiThemeToken.SURFACE_SUNKEN);
            case GLYPH:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ICON_ON_ACCENT)
                        : state == UiTextureState.INACTIVE
                                ? theme.color(UiThemeToken.ICON_MUTED)
                                : theme.color(UiThemeToken.ICON_PRIMARY);
            case GLYPH_SHADOW:
                UiColor glyph = state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ICON_ON_ACCENT)
                        : state == UiTextureState.INACTIVE
                                ? theme.color(UiThemeToken.ICON_MUTED)
                                : theme.color(UiThemeToken.ICON_PRIMARY);
                UiColor shade = state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ACCENT_SECONDARY)
                        : theme.color(UiThemeToken.SURFACE_SUNKEN);
                return UiColor.interpolate(glyph, shade, 0.55D);
            case SUCCESS:
                return theme.color(UiThemeToken.SUCCESS);
            case SUCCESS_DARK:
                return theme.color(UiThemeToken.ACCENT_SECONDARY);
            case ERROR:
                return theme.color(UiThemeToken.ERROR);
            case ERROR_DARK:
                return theme.color(UiThemeToken.DESTRUCTIVE);
            case BUTTON_HIGHLIGHT:
                return buttonHighlight(theme, state);
            case BUTTON_CORNER:
                return UiColor.interpolate(
                        theme.color(backgroundToken(state)),
                        theme.color(UiThemeToken.SURFACE_SUNKEN),
                        0.55D);
            case BUTTON_SHADOW:
                return theme.color(UiThemeToken.SURFACE_SUNKEN);
            case INDICATOR_OUTSIDE:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.CONTROL_IDLE)
                        : theme.color(UiThemeToken.SURFACE_SUNKEN);
            case INDICATOR_HIGHLIGHT:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ICON_ON_ACCENT)
                        : state == UiTextureState.HOVER
                                ? theme.color(UiThemeToken.TEXT_PRIMARY)
                                : theme.color(UiThemeToken.ICON_PRIMARY);
            case INDICATOR_FILL:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.SWITCH_THUMB)
                        : state == UiTextureState.HOVER
                                ? theme.color(UiThemeToken.ICON_PRIMARY)
                                : theme.color(UiThemeToken.ICON_MUTED);
            case INDICATOR_SHADOW:
                return state == UiTextureState.ACTIVE
                        ? theme.color(UiThemeToken.ACCENT_SECONDARY)
                        : theme.color(UiThemeToken.BORDER_SOFT);
            default:
                throw new IllegalStateException("unsupported indexed role: " + role);
        }
    }

    private static UiThemeToken backgroundToken(UiTextureState state) {
        switch (state) {
            case HOVER: return UiThemeToken.CONTROL_HOVER;
            case ACTIVE: return UiThemeToken.CONTROL_SELECTED;
            case PRESSED: return UiThemeToken.CONTROL_PRESSED;
            case INACTIVE:
            default: return UiThemeToken.CONTROL_IDLE;
        }
    }

    private static UiColor buttonHighlight(
            UiThemeDefinition theme, UiTextureState state) {
        switch (state) {
            case HOVER:
                return theme.color(UiThemeToken.FOCUS_RING);
            case ACTIVE:
                return theme.color(UiThemeToken.ACCENT_PRIMARY);
            case PRESSED:
                return theme.color(UiThemeToken.ACCENT_SECONDARY);
            case INACTIVE:
            default:
                return theme.color(UiThemeToken.BORDER_STRONG);
        }
    }

    private UiPaletteTextureBaker() {
    }
}
