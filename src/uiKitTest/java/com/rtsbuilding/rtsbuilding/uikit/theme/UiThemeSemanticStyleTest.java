package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 防止背景、状态条和深色选中控件再次误绑到文字令牌。 */
class UiThemeSemanticStyleTest {
    @Test
    void calibratedThemeKeepsDarkControlsReadableAndStatusBarsSemantic() {
        UiThemeManager manager = UiThemeRuntime.manager();
        String previous = manager.active().id();
        try {
            manager.activate(UiThemeBuiltins.CALIBRATED_ID);
            UiThemeDefinition active = manager.active();

            assertEquals(0xCC000000
                            | (active.color(UiThemeToken.SURFACE_SUNKEN).toArgb() & 0x00FFFFFF),
                    SettingsWindowStyle.VALUE_BACKGROUND.toArgb());
            assertEquals(active.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                    BottomPanelCategoryStyle.ROW_SELECTED_TEXT.toArgb());
            assertEquals(active.color(UiThemeToken.ERROR).toArgb(),
                    PlayerStatusStyle.HEALTH_HIGH.toArgb());
            assertNotEquals(SettingsWindowStyle.VALUE_BACKGROUND.toArgb(),
                    SettingsWindowStyle.VALUE.toArgb());
            assertNotEquals(BottomPanelCategoryStyle.ROW_SELECTED_BACKGROUND.toArgb(),
                    BottomPanelCategoryStyle.ROW_SELECTED_TEXT.toArgb());
        } finally {
            manager.activate(previous);
        }
    }
}
