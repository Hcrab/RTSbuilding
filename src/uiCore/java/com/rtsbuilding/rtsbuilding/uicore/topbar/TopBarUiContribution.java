package com.rtsbuilding.rtsbuilding.uicore.topbar;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;

import java.util.Locale;

/**
 * 内建顶栏入口的 internal/unstable 平台无关贡献描述。
 *
 * <p>它提供稳定按钮 ID、控件角色、四语言键与 Action 工厂；可见、可用和选中
 * 状态由当前 Minecraft Adapter 供应。本类型不暴露 Screen、GuiGraphics 或 Loader API，
 * 也尚不承诺第三方二进制兼容。</p>
 */
public final class TopBarUiContribution {
    private final TopBarUiButtonId buttonId;
    private final UiControlRole role;
    private final String tooltipKey;
    private final String disabledReasonKey;

    public TopBarUiContribution(TopBarUiButtonId buttonId, UiControlRole role,
                                String tooltipKey, String disabledReasonKey) {
        if (buttonId == null || role == null) {
            throw new IllegalArgumentException("buttonId and role must not be null");
        }
        this.buttonId = buttonId;
        this.role = role;
        this.tooltipKey = requireTranslationKey(tooltipKey, "tooltipKey");
        this.disabledReasonKey = disabledReasonKey == null || disabledReasonKey.isEmpty()
                ? "" : requireTranslationKey(disabledReasonKey, "disabledReasonKey");
    }

    public static TopBarUiContribution builtIn(TopBarUiButtonId id, UiControlRole role,
                                                boolean pluginGated) {
        String suffix = id.name().toLowerCase(Locale.ROOT);
        return new TopBarUiContribution(id, role,
                "screen.rtsbuilding.topbar.tooltip." + suffix,
                pluginGated ? "message.rtsbuilding.plugin_required" : "");
    }

    public TopBarUiAction action() {
        return TopBarUiAction.click(buttonId);
    }

    public TopBarUiButtonId getButtonId() { return buttonId; }
    public UiControlRole getRole() { return role; }
    public String getTooltipKey() { return tooltipKey; }
    public String getDisabledReasonKey() { return disabledReasonKey; }

    private static String requireTranslationKey(String value, String name) {
        if (value == null || !value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(name + " must be a stable translation key");
        }
        return value;
    }
}
