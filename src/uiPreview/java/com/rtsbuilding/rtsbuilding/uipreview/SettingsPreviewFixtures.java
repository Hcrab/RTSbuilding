package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsSectionId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiValue;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsWindowLayout;

import java.util.EnumMap;
import java.util.EnumSet;

/** 设置窗离屏场景填入真实 Core 目录的确定性值。 */
final class SettingsPreviewFixtures {
    private SettingsPreviewFixtures() {
    }

    static SettingsUiState forScenario(UiPreviewScenario scenario, UiLanguageBundle language,
                                       BufferedImageUiCanvas canvas, int contentX, int contentWidth) {
        EnumMap<SettingsId, SettingsUiValue> values = new EnumMap<SettingsId, SettingsUiValue>(SettingsId.class);
        for (SettingsId id : SettingsId.values()) {
            if (id.kind == SettingsRowKind.SENSITIVITY) {
                values.put(id, SettingsUiValue.value("Normal", 2, 5));
            } else if (id == SettingsId.UI_SCALE) {
                values.put(id, SettingsUiValue.value(String.format("%.1fx", scenario.rtsScale()), 2, 7));
            } else if (id == SettingsId.BLOCK_SOUNDS_PER_TICK) {
                values.put(id, SettingsUiValue.value("8", 7, 16));
            } else {
                values.put(id, SettingsUiValue.toggle(defaultToggle(id)));
            }
        }
        // Jade 存在/不存在都要有确定场景；空/失败场景模拟未安装 Jade。
        if (scenario.variant() == UiPreviewScenario.Variant.EMPTY_LOADING_FAILED_DISABLED
                || scenario.variant() == UiPreviewScenario.Variant.SETTINGS_NO_JADE) {
            values.remove(SettingsId.JADE_TRACK_MOUSE);
            values.remove(SettingsId.JADE_HIDDEN);
        }

        EnumSet<SettingsSectionId> sections = EnumSet.noneOf(SettingsSectionId.class);
        if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_CONTROLS) {
            sections.add(SettingsSectionId.CONTROLS);
        } else if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_SOUND) {
            sections.add(SettingsSectionId.SOUND);
        } else if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_ANIMATION) {
            sections.add(SettingsSectionId.ANIMATION);
        } else if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_NO_JADE) {
            sections.add(SettingsSectionId.DISPLAY);
        } else if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_LONG_HINT) {
            sections.add(SettingsSectionId.HELPERS);
        } else if (scenario.variant() == UiPreviewScenario.Variant.BASELINE
                || scenario.variant() == UiPreviewScenario.Variant.STORAGE_2000) {
            sections.add(SettingsSectionId.DISPLAY);
            sections.add(SettingsSectionId.HELPERS);
        } else if (scenario.variant() == UiPreviewScenario.Variant.SETTINGS_TOOLTIP
                || scenario.variant() == UiPreviewScenario.Variant.JADE_MODES) {
            sections.add(SettingsSectionId.DISPLAY);
        } else {
            sections.add(SettingsSectionId.HELPERS);
        }

        EnumSet<SettingsId> expandable = EnumSet.noneOf(SettingsId.class);
        int hintX = contentX + 16 + SettingsWindowLayout.HINT_EXPAND_BUTTON_SIZE + 4;
        int hintW = Math.max(24, contentX + contentWidth - 92 - hintX - 8);
        for (SettingsId id : values.keySet()) {
            if (id.kind == SettingsRowKind.HINT_TOGGLE
                    && canvas.textWidth(language.text(id.hintKey)) > hintW) {
                expandable.add(id);
            }
        }
        EnumSet<SettingsId> expandedHints = EnumSet.noneOf(SettingsId.class);
        if (scenario.variant() == UiPreviewScenario.Variant.LONG_ENGLISH
                || scenario.variant() == UiPreviewScenario.Variant.SETTINGS_LONG_HINT) {
            expandedHints.add(SettingsId.STORAGE_AUTO_REFRESH);
        }
        int scroll = (scenario.variant() == UiPreviewScenario.Variant.BASELINE
                || scenario.variant() == UiPreviewScenario.Variant.STORAGE_2000) ? 226
                : scenario.variant() == UiPreviewScenario.Variant.SETTINGS_LONG_HINT ? 92 : 0;
        return SettingsUiCatalog.create(values, sections, expandable, expandedHints, scroll);
    }

    private static boolean defaultToggle(SettingsId id) {
        switch (id) {
            case STORAGE_REFRESH_QUIET:
            case PLACED_RECOVERY:
            case JADE_HIDDEN:
            case PLACEMENT_WIREFRAME_PREVIEW:
                return false;
            default:
                return true;
        }
    }
}
