package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeCoverageTest {
    @Test
    void everyPlayerVisibleFamilyIsRegisteredAndCollectivelyUsesEveryCoreToken() {
        assertEquals(UiThemeCoverageCatalog.ComponentFamily.values().length,
                UiThemeCoverageCatalog.snapshot().size());
        EnumSet<UiThemeToken> used = EnumSet.noneOf(UiThemeToken.class);
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            assertFalse(UiThemeCoverageCatalog.required(family).isEmpty(), family.name());
            used.addAll(UiThemeCoverageCatalog.required(family));
        }
        assertEquals(EnumSet.allOf(UiThemeToken.class), used);
    }

    @Test
    void productionStyleClassesCannotReturnToFixedOpaqueColors() throws Exception {
        Path root = Paths.get("src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme");
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Style.java"))
                    .forEach(path -> {
                        try {
                            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                            assertFalse(source.matches("(?s).*public static final UiColor [A-Z0-9_]+ = new UiColor\\(0x(?!00000000).*"),
                                    path.toString());
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    });
        }
    }

    @Test
    void existingStyleObjectsChangeInPaletteButReturnExactlyToLegacy() {
        UiThemeManager manager = UiThemeRuntime.manager();
        manager.fallBackToLegacy();
        int legacyWindow = RtsMainlineTheme.WINDOW_BACKGROUND.toArgb();
        int legacyTerminalSlot = CraftTerminalStyle.SLOT.toArgb();
        try {
            manager.activate(UiThemeBuiltins.CARBON_ID);
            assertEquals(UiThemeBuiltins.carbonOperations().color(UiThemeToken.SURFACE).toArgb(),
                    RtsMainlineTheme.WINDOW_BACKGROUND.toArgb());
            assertEquals(UiThemeBuiltins.carbonOperations().color(UiThemeToken.SLOT_IDLE).toArgb(),
                    CraftTerminalStyle.SLOT.toArgb());
            assertNotEquals(legacyWindow, RtsMainlineTheme.WINDOW_BACKGROUND.toArgb());
        } finally {
            manager.fallBackToLegacy();
        }
        assertEquals(legacyWindow, RtsMainlineTheme.WINDOW_BACKGROUND.toArgb());
        assertEquals(legacyTerminalSlot, CraftTerminalStyle.SLOT.toArgb());
    }
}
