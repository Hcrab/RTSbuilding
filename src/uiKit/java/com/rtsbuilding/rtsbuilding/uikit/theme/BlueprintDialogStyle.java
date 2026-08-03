package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;

/** 蓝图命名与材料窗口在生产/离屏间共享的业务色板。 */
public final class BlueprintDialogStyle {
    public static final UiColor PRIMARY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor LABEL_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFB7CDE2);
    public static final UiColor CAPTURE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFFCDEBFF);
    public static final UiColor CURRENT_NAME_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.TEXT_PRIMARY, 0XFF9EACB9);
    public static final UiColor READY = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.SUCCESS, 0XFF8EEA9B);
    public static final UiColor WARNING = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.WARNING, 0XFFFFC06C);
    public static final UiColor MISSING = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.ERROR, 0XFFFF9E88);
    public static final UiColor INPUT_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.SURFACE_SUNKEN, 0XDD05070B);
    public static final UiColor INPUT_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0XFF8BA4B8);
    public static final UiColor DARK_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_SOFT, 0XFF0B0E13);
    public static final UiColor LIST_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_IDLE, 0X99101620);
    public static final UiColor LIST_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0XFF415266);
    public static final UiColor ROW_HOVER = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_HOVER, 0X66324126);
    public static final UiColor MISSING_ICON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.ERROR, 0XAA36506A);
    public static final UiColor MISSING_ICON_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.ERROR, 0XFF58708A);
    public static final UiColor MISSING_ICON_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.ERROR, 0XFFFFD080);
    public static final UiColor SCROLL_TRACK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.SCROLLBAR_TRACK, 0X66566A7C);
    public static final UiColor SCROLL_THUMB = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.SCROLLBAR_THUMB, 0XFF8EA5B8);
    public static final UiColor BUTTON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_IDLE, 0XAA24303C);
    public static final UiColor BUTTON_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_HOVER, 0XCC334052);
    public static final UiColor BUTTON_ACTIVE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.CONTROL_SELECTED, 0XCC2E6A50);
    public static final UiColor BUTTON_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_STRONG, 0XFF64788E);
    public static final UiColor BUTTON_DARK_BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BLUEPRINT, UiThemeToken.BORDER_SOFT, 0XFF0D1015);

    private BlueprintDialogStyle() {
    }

    public static UiColor materialTone(BlueprintMaterialUiState.Tone tone) {
        if (tone == BlueprintMaterialUiState.Tone.MISSING) {
            return MISSING;
        }
        if (tone == BlueprintMaterialUiState.Tone.READY) {
            return READY;
        }
        return WARNING;
    }
}
