package com.rtsbuilding.rtsbuilding.uicore.settings;

import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SettingsSectionUiCatalogTest {
    @Test
    void productionSettingsGroupsExposeStableRegistryOrderAndTranslationKeys() {
        List<UiRegistration<SettingsSectionUiContribution>> registrations =
                SettingsSectionUiCatalog.registrations();

        assertEquals(Arrays.asList(SettingsSectionId.values()),
                registrations.stream()
                        .map(entry -> entry.getValue().getSection())
                        .collect(Collectors.toList()));
        assertEquals(
                registrations.stream()
                        .map(entry -> entry.getValue().getSection().titleKey)
                        .collect(Collectors.toList()),
                registrations.stream()
                        .map(entry -> entry.getValue().getTitleKey())
                        .collect(Collectors.toList()));
    }
}
