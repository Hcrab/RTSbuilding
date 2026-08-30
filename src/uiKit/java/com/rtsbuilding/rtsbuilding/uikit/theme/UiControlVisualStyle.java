package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;

/**
 * 将平台无关的控件角色/状态解析为主线 chrome 色值。
 *
 * <p>它负责普通控件的公共视觉语义，不决定文字、图标、命中区域或业务 Action。
 * 生产与离屏 renderer 共用本结果，避免同一个按钮在两侧复制颜色分支。</p>
 */
public final class UiControlVisualStyle {
    private final UiColor background;
    private final UiColor borderLight;
    private final UiColor borderDark;
    private final UiColor text;
    private final UiColor overlay;

    private UiControlVisualStyle(UiColor background, UiColor borderLight,
                                 UiColor borderDark, UiColor text, UiColor overlay) {
        this.background = background;
        this.borderLight = borderLight;
        this.borderDark = borderDark;
        this.text = text;
        this.overlay = overlay;
    }

    public static UiControlVisualStyle resolve(UiControlRole role, UiControlState state) {
        if (role == null || state == null) {
            throw new IllegalArgumentException("role and state must not be null");
        }
        UiColor background = background(role);
        UiColor light = RtsMainlineTheme.BUTTON_BORDER_LIGHT;
        UiColor text = RtsMainlineTheme.BUTTON_TEXT;
        UiColor overlay = RtsMainlineTheme.TRANSPARENT;

        if (state.isSelected()) {
            background = RtsMainlineTheme.CONTROL_SELECTED_BACKGROUND;
            light = RtsMainlineTheme.CONTROL_SELECTED_BORDER_LIGHT;
            text = RtsMainlineTheme.CONTROL_SELECTED_ICON;
        }
        if (state.isHovered() || state.isFocused()) {
            light = RtsMainlineTheme.CONTROL_HOVER_BORDER_LIGHT;
            text = RtsMainlineTheme.CONTROL_HOVER_ICON;
        }
        if (state.isPressed()) {
            background = RtsMainlineTheme.CONTROL_PRESSED_BACKGROUND;
            light = RtsMainlineTheme.CONTROL_PRESSED_BORDER_LIGHT;
        }
        if (state.isPending()) {
            light = RtsMainlineTheme.CONTROL_PENDING;
        } else if (state.isError()) {
            light = RtsMainlineTheme.CONTROL_ERROR;
        }
        if (!state.isEnabled()) {
            overlay = RtsMainlineTheme.CONTROL_DISABLED_OVERLAY;
        }
        return new UiControlVisualStyle(background, light,
                RtsMainlineTheme.BUTTON_BORDER_DARK, text, overlay);
    }

    private static UiColor background(UiControlRole role) {
        switch (role) {
            case PRIMARY_ACTION:
                return RtsMainlineTheme.BUTTON_PRIMARY_BACKGROUND;
            case DESTRUCTIVE:
            case DESTRUCTIVE_CONFIRM:
                return RtsMainlineTheme.BUTTON_DESTRUCTIVE_BACKGROUND;
            default:
                return RtsMainlineTheme.BUTTON_BACKGROUND;
        }
    }

    /**
     * 计算普通控件的动画边框色。
     *
     * <p>悬停与选中仅在边框亮度上插值；按下状态立即覆盖两者。这样业务状态和
     * 命中无需等待动画，同时不会用半透明整块覆盖层盖住图标并留下残影。</p>
     */
    public static UiColor animatedBorder(UiControlRole role, double hoverStrength,
                                         double selectionStrength, boolean pressed) {
        UiControlVisualStyle idle = resolve(role, UiControlState.enabled());
        UiControlVisualStyle hover = resolve(role,
                UiControlState.enabled().withInteraction(true, false, false));
        UiControlVisualStyle selected = resolve(role,
                new UiControlState(true, true, false, false, ""));
        UiControlVisualStyle pressedStyle = resolve(role,
                UiControlState.enabled().withInteraction(true, false, true));

        UiColor result = UiColor.interpolate(idle.borderLight, hover.borderLight, hoverStrength);
        result = UiColor.interpolate(result, selected.borderLight, selectionStrength);
        return pressed ? pressedStyle.borderLight : result;
    }

    /**
     * 用通用动画强度混合控件 chrome；业务状态和点击结果仍然即时生效。
     */
    public static UiControlVisualStyle animated(
            UiControlRole role,
            UiControlAnimationState.Snapshot animation) {
        if (role == null || animation == null) {
            throw new IllegalArgumentException("role and animation");
        }
        UiControlVisualStyle result = resolve(role, UiControlState.enabled());
        result = interpolate(result, resolve(role,
                        UiControlState.enabled().withInteraction(
                                true, false, false)),
                animation.hover());
        result = interpolate(result, resolve(role,
                        new UiControlState(true, true, false, false, "")),
                animation.selection());
        result = interpolate(result, resolve(role,
                        UiControlState.enabled().withInteraction(
                                true, false, true)),
                animation.press());
        UiColor disabledOverlay = UiColor.interpolate(
                RtsMainlineTheme.TRANSPARENT,
                RtsMainlineTheme.CONTROL_DISABLED_OVERLAY,
                animation.disabled());
        return new UiControlVisualStyle(
                result.background,
                result.borderLight,
                result.borderDark,
                UiColor.interpolate(
                        result.text,
                        RtsMainlineTheme.MUTED_TEXT,
                        animation.disabled()),
                disabledOverlay);
    }

    private static UiControlVisualStyle interpolate(
            UiControlVisualStyle from,
            UiControlVisualStyle to,
            double strength) {
        return new UiControlVisualStyle(
                UiColor.interpolate(from.background, to.background, strength),
                UiColor.interpolate(from.borderLight, to.borderLight, strength),
                UiColor.interpolate(from.borderDark, to.borderDark, strength),
                UiColor.interpolate(from.text, to.text, strength),
                UiColor.interpolate(from.overlay, to.overlay, strength));
    }

    public UiColor getBackground() { return background; }
    public UiColor getBorderLight() { return borderLight; }
    public UiColor getBorderDark() { return borderDark; }
    public UiColor getText() { return text; }
    public UiColor getOverlay() { return overlay; }
}
