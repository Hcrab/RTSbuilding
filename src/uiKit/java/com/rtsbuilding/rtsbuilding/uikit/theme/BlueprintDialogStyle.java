package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;

/** 蓝图命名与材料窗口在生产/离屏间共享的业务色板。 */
public final class BlueprintDialogStyle {
    public static final UiColor PRIMARY_TEXT = new UiColor(0xFFEAF2FF);
    public static final UiColor LABEL_TEXT = new UiColor(0xFFB7CDE2);
    public static final UiColor CAPTURE_TEXT = new UiColor(0xFFCDEBFF);
    public static final UiColor CURRENT_NAME_TEXT = new UiColor(0xFF9EACB9);
    public static final UiColor READY = new UiColor(0xFF8EEA9B);
    public static final UiColor WARNING = new UiColor(0xFFFFC06C);
    public static final UiColor MISSING = new UiColor(0xFFFF9E88);
    public static final UiColor INPUT_BACKGROUND = new UiColor(0xDD05070B);
    public static final UiColor INPUT_BORDER = new UiColor(0xFF8BA4B8);
    public static final UiColor DARK_BORDER = new UiColor(0xFF0B0E13);
    public static final UiColor LIST_BACKGROUND = new UiColor(0x99101620);
    public static final UiColor LIST_BORDER = new UiColor(0xFF415266);
    public static final UiColor ROW_HOVER = new UiColor(0x66324126);
    public static final UiColor MISSING_ICON_BACKGROUND = new UiColor(0xAA36506A);
    public static final UiColor MISSING_ICON_BORDER = new UiColor(0xFF58708A);
    public static final UiColor MISSING_ICON_TEXT = new UiColor(0xFFFFD080);
    public static final UiColor SCROLL_TRACK = new UiColor(0x66566A7C);
    public static final UiColor SCROLL_THUMB = new UiColor(0xFF8EA5B8);
    public static final UiColor BUTTON_BACKGROUND = new UiColor(0xAA24303C);
    public static final UiColor BUTTON_HOVER_BACKGROUND = new UiColor(0xCC334052);
    public static final UiColor BUTTON_ACTIVE_BACKGROUND = new UiColor(0xCC2E6A50);
    public static final UiColor BUTTON_BORDER = new UiColor(0xFF64788E);
    public static final UiColor BUTTON_DARK_BORDER = new UiColor(0xFF0D1015);

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
