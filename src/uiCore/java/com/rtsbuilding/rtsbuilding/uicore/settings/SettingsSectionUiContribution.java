package com.rtsbuilding.rtsbuilding.uicore.settings;

/**
 * 设置分组的 internal/unstable 平台无关贡献描述。
 *
 * <p>分组只声明稳定枚举身份和标题翻译键；展开状态与具体设置值仍属于当前
 * UI 快照，不允许注册目录保存运行时状态。</p>
 */
public final class SettingsSectionUiContribution {
    private final SettingsSectionId section;

    public SettingsSectionUiContribution(SettingsSectionId section) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        this.section = section;
    }

    public SettingsSectionId getSection() {
        return section;
    }

    public String getTitleKey() {
        return section.titleKey;
    }
}
