package com.rtsbuilding.rtsbuilding.uicore.settings;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;

/** 设置正式目录的 internal/unstable 平台无关贡献载荷。 */
public final class SettingsUiContribution {
    private final SettingsId id;
    private final UiControlRole role;

    public SettingsUiContribution(SettingsId id, UiControlRole role) {
        if (id == null || role == null) {
            throw new IllegalArgumentException("id and role must not be null");
        }
        this.id = id;
        this.role = role;
    }

    public SettingsId getId() { return id; }
    public UiControlRole getRole() { return role; }
    public String getLabelKey() { return id.labelKey; }
    public String getHintKey() { return id.hintKey; }
}
