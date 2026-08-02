package com.rtsbuilding.rtsbuilding.uicore.settings;

import com.rtsbuilding.rtsbuilding.uicore.registry.UiOrderedRegistry;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;

import java.util.Collections;
import java.util.List;

/** 设置分组的 internal/unstable 注册目录，生产快照必须消费其排序结果。 */
public final class SettingsSectionUiCatalog {
    private static final List<UiRegistration<SettingsSectionUiContribution>> REGISTRATIONS =
            create();

    private SettingsSectionUiCatalog() {
    }

    public static List<UiRegistration<SettingsSectionUiContribution>> registrations() {
        return REGISTRATIONS;
    }

    private static List<UiRegistration<SettingsSectionUiContribution>> create() {
        UiOrderedRegistry<SettingsSectionUiContribution> registry = new UiOrderedRegistry<>();
        int weight = 0;
        for (SettingsSectionId section : SettingsSectionId.values()) {
            registry.register(new UiRegistration<>(
                    "rtsbuilding:settings.section."
                            + section.name().toLowerCase(java.util.Locale.ROOT),
                    "settings_sections",
                    weight++,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    new SettingsSectionUiContribution(section)));
        }
        return registry.snapshot();
    }
}
