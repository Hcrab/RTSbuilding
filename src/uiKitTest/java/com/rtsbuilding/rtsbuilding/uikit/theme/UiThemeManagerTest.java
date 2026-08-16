package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeManagerTest {
    @Test
    void userThemeCanBeReplacedButBuiltInNamespaceCannot() {
        UiThemeRegistry registry = UiThemeBuiltins.createRegistry();
        UiThemeDefinition first = new UiThemeDefinition("user:test", "Test", "User", "Test",
                UiThemeRenderMode.PALETTE, UiThemeBuiltins.PIXEL_TEXTURE_SET, true,
                UiThemeBuiltins.nordCommand().tokens());
        UiThemeDefinition second = new UiThemeDefinition("user:test", "Test 2", "User", "Test",
                UiThemeRenderMode.PALETTE, UiThemeBuiltins.PIXEL_TEXTURE_SET, true,
                UiThemeBuiltins.carbonOperations().tokens());
        registry.registerOrReplaceUser(first);
        registry.registerOrReplaceUser(second);
        assertSame(second, registry.require("user:test"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerOrReplaceUser(UiThemeBuiltins.nordCommand()));
    }
    @Test
    void startsInConfiguredDefaultAndNotifiesOnlyForRealChanges() {
        UiThemeRegistry registry = UiThemeBuiltins.createRegistry();
        UiThemeManager manager = new UiThemeManager(registry, UiThemeBuiltins.CARBON_ID);
        AtomicInteger changes = new AtomicInteger();
        manager.addListener((previous, current) -> changes.incrementAndGet());

        assertEquals(UiThemeBuiltins.CARBON_ID, manager.active().id());
        manager.activate(UiThemeBuiltins.CARBON_ID);
        assertEquals(0, changes.get());

        manager.activate(UiThemeBuiltins.NORD_ID);
        assertEquals(1, changes.get());
        assertEquals(UiThemeRenderMode.PALETTE, manager.active().renderMode());

        manager.resetToDefault();
        assertEquals(2, changes.get());
        assertEquals(UiThemeBuiltins.CARBON_ID, manager.active().id());
    }

    @Test
    void unknownThemeNeverChangesTheActiveDefinition() {
        UiThemeManager manager = new UiThemeManager(
                UiThemeBuiltins.createRegistry(), UiThemeBuiltins.LEGACY_ID);
        assertThrows(IllegalArgumentException.class, () -> manager.activate("test:missing"));
        assertEquals(UiThemeBuiltins.LEGACY_ID, manager.active().id());
    }
}
