package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.regex.Pattern;

/**
 * 一套只含语义颜色的不可变主题 token。
 *
 * <p>面板代码选择“背景、正文、强调、危险”等角色，不直接散落颜色常量；
 * 字体、贴图和按钮状态仍由平台 renderer 决定。</p>
 */
public final class UiThemeTokens {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private final String id;
    private final UiColor screenBackground;
    private final UiColor barBackground;
    private final UiColor panelBackground;
    private final UiColor panelBorder;
    private final UiColor primaryText;
    private final UiColor secondaryText;
    private final UiColor accent;
    private final UiColor danger;
    private final UiColor disabledOverlay;

    public UiThemeTokens(String id, UiColor screenBackground, UiColor barBackground,
                         UiColor panelBackground, UiColor panelBorder,
                         UiColor primaryText, UiColor secondaryText, UiColor accent,
                         UiColor danger, UiColor disabledOverlay) {
        if (id == null || !STABLE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("theme id must be a stable lowercase id");
        }
        this.id = id;
        this.screenBackground = requireColor(screenBackground, "screenBackground");
        this.barBackground = requireColor(barBackground, "barBackground");
        this.panelBackground = requireColor(panelBackground, "panelBackground");
        this.panelBorder = requireColor(panelBorder, "panelBorder");
        this.primaryText = requireColor(primaryText, "primaryText");
        this.secondaryText = requireColor(secondaryText, "secondaryText");
        this.accent = requireColor(accent, "accent");
        this.danger = requireColor(danger, "danger");
        this.disabledOverlay = requireColor(disabledOverlay, "disabledOverlay");
    }

    public String getId() { return id; }
    public UiColor getScreenBackground() { return screenBackground; }
    public UiColor getBarBackground() { return barBackground; }
    public UiColor getPanelBackground() { return panelBackground; }
    public UiColor getPanelBorder() { return panelBorder; }
    public UiColor getPrimaryText() { return primaryText; }
    public UiColor getSecondaryText() { return secondaryText; }
    public UiColor getAccent() { return accent; }
    public UiColor getDanger() { return danger; }
    public UiColor getDisabledOverlay() { return disabledOverlay; }

    private static UiColor requireColor(UiColor color, String name) {
        if (color == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return color;
    }
}
