package com.rtsbuilding.rtsbuilding.uicore.bottom;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;

/**
 * 底部面板入口的 internal/unstable 平台无关描述。
 *
 * <p>它只拥有稳定入口 ID、翻译键、控件语义和访问条件，不接触 Minecraft
 * Screen、绘制或网络。生产布局与离屏预览都必须消费同一份目录。</p>
 */
public final class PanelUiContribution {
    public enum Access {
        ALWAYS,
        CREATIVE_PLAYER,
        BLUEPRINT_PLUGIN
    }

    private final BottomBarUiTab tab;
    private final String labelKey;
    private final UiControlRole role;
    private final Access access;

    public PanelUiContribution(BottomBarUiTab tab, String labelKey,
                               UiControlRole role, Access access) {
        if (tab == null || role == null || access == null) {
            throw new IllegalArgumentException("panel contribution fields must not be null");
        }
        if (labelKey == null || !labelKey.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("labelKey must be a stable translation key");
        }
        this.tab = tab;
        this.labelKey = labelKey;
        this.role = role;
        this.access = access;
    }

    public BottomBarUiTab getTab() {
        return tab;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public UiControlRole getRole() {
        return role;
    }

    public Access getAccess() {
        return access;
    }

    public boolean isVisible(boolean creativeAccess, boolean blueprintAccess) {
        switch (access) {
            case ALWAYS:
                return true;
            case CREATIVE_PLAYER:
                return creativeAccess;
            case BLUEPRINT_PLUGIN:
                return blueprintAccess;
            default:
                throw new IllegalStateException("unknown panel access: " + access);
        }
    }
}
