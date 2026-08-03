package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeManagerTest {
    @Test
    void startsInLegacyAndNotifiesOnlyForRealChanges() {
        UiThemeRegistry registry = UiThemeBuiltins.createRegistry();
        UiThemeManager manager = new UiThemeManager(registry, UiThemeBuiltins.LEGACY_ID);
        AtomicInteger changes = new AtomicInteger();
        manager.addListener((previous, current) -> changes.incrementAndGet());

        assertEquals(UiThemeRenderMode.LEGACY_DIRECT, manager.active().renderMode());
        manager.activate(UiThemeBuiltins.LEGACY_ID);
        assertEquals(0, changes.get());

        manager.activate(UiThemeBuiltins.NORD_ID);
        assertEquals(1, changes.get());
        assertEquals(UiThemeRenderMode.PALETTE, manager.active().renderMode());

        manager.fallBackToLegacy();
        assertEquals(2, changes.get());
        assertEquals(UiThemeBuiltins.LEGACY_ID, manager.active().id());
    }

    @Test
    void unknownThemeNeverChangesTheActiveDefinition() {
        UiThemeManager manager = new UiThemeManager(
                UiThemeBuiltins.createRegistry(), UiThemeBuiltins.LEGACY_ID);
        assertThrows(IllegalArgumentException.class, () -> manager.activate("test:missing"));
        assertEquals(UiThemeBuiltins.LEGACY_ID, manager.active().id());
    }
}
