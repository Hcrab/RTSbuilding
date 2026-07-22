package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 设置窗全部无副作用交互的权威 reducer。 */
public final class SettingsUiReducer {
    private SettingsUiReducer() {
    }

    public static SettingsUiTransition apply(SettingsUiState state, SettingsUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        switch (action.type) {
            case TOGGLE_SECTION: {
                SettingsUiSection section = state.section(action.sectionId);
                if (section == null) return none(state, action);
                return view(state.replaceSection(section.withExpanded(!section.expanded)), action);
            }
            case TOGGLE_HINT: {
                SettingsUiRow row = state.row(action.settingId);
                if (row == null || !row.hintExpandable) return none(state, action);
                return view(state.replaceRow(row.withHintExpanded(!row.hintExpanded)), action);
            }
            case TOGGLE_VALUE: {
                SettingsUiRow row = state.row(action.settingId);
                if (row == null || !row.enabled
                        || (row.id.kind != SettingsRowKind.SIMPLE_TOGGLE
                        && row.id.kind != SettingsRowKind.HINT_TOGGLE)) return none(state, action);
                return new SettingsUiTransition(state.replaceRow(row.withActive(!row.active)),
                        SettingsUiTransition.Command.TOGGLE_VALUE, action);
            }
            case ADJUST_VALUE: {
                SettingsUiRow row = state.row(action.settingId);
                if (row == null || !row.enabled || row.id.kind != SettingsRowKind.STEP_VALUE
                        || action.amount == 0) return none(state, action);
                int index = Math.max(0, Math.min(row.valueCount - 1,
                        row.valueIndex + action.amount));
                return new SettingsUiTransition(state.replaceRow(row.withValueIndex(index)),
                        SettingsUiTransition.Command.ADJUST_VALUE, action);
            }
            case SET_SENSITIVITY: {
                SettingsUiRow row = state.row(action.settingId);
                if (row == null || !row.enabled || row.id.kind != SettingsRowKind.SENSITIVITY) {
                    return none(state, action);
                }
                double fraction = Math.max(0.0D, Math.min(1.0D, action.fraction));
                int index = (int) Math.round(fraction * Math.max(0, row.valueCount - 1));
                return new SettingsUiTransition(state.replaceRow(row.withValueIndex(index)),
                        SettingsUiTransition.Command.SET_SENSITIVITY, action);
            }
            case SET_SCROLL:
                return view(state.withScroll(Math.max(0, Math.min(action.maximum, action.amount))), action);
            default:
                return none(state, action);
        }
    }

    private static SettingsUiTransition view(SettingsUiState state, SettingsUiAction action) {
        return new SettingsUiTransition(state, SettingsUiTransition.Command.APPLY_VIEW_STATE, action);
    }

    private static SettingsUiTransition none(SettingsUiState state, SettingsUiAction action) {
        return new SettingsUiTransition(state, SettingsUiTransition.Command.NONE, action);
    }
}
