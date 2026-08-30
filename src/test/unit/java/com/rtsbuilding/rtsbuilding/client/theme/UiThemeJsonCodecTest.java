package com.rtsbuilding.rtsbuilding.client.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeBuiltins;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeJsonCodecTest {
    private final UiThemeJsonCodec codec = new UiThemeJsonCodec();

    @Test
    void paletteRoundTripKeepsEveryCoreAndComponentColor() {
        UiThemeDefinition source = userCopy("test:round_trip", UiThemeBuiltins.nordCommand());
        UiThemeDefinition decoded = codec.decode(codec.encode(source));
        assertEquals(source.id(), decoded.id());
        assertEquals(UiThemeRenderMode.PALETTE, decoded.renderMode());
        assertEquals(UiThemeToken.values().length, decoded.tokens().size());
        assertEquals(UiThemeCoverageCatalog.ComponentFamily.values().length,
                decoded.components().size());
        for (UiThemeToken token : UiThemeToken.values()) {
            assertEquals(source.color(token), decoded.color(token), token.name());
        }
    }

    @Test
    void missingAndUnknownFieldsAreHardErrorsWithExactPath() {
        JsonObject missingRoot = JsonParser.parseString(codec.encode(
                userCopy("test:strict", UiThemeBuiltins.carbonOperations()))).getAsJsonObject();
        missingRoot.getAsJsonObject("tokens").remove("world_invalid");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(missingRoot.toString()));
        assertTrue(missing.getMessage().contains("world_invalid"));

        JsonObject unknownRoot = JsonParser.parseString(codec.encode(
                userCopy("test:strict2", UiThemeBuiltins.materialField()))).getAsJsonObject();
        unknownRoot.getAsJsonObject("components").getAsJsonObject("craft_terminal")
                .addProperty("typo_color", "#FFFFFFFF");
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(unknownRoot.toString()));
        assertTrue(unknown.getMessage().contains("typo_color"));
    }

    @Test
    void userJsonCannotSelectLegacyOrReplaceBuiltins() {
        JsonObject root = JsonParser.parseString(codec.encode(
                userCopy("test:mode", UiThemeBuiltins.calibratedDark()))).getAsJsonObject();
        root.addProperty("renderMode", "legacy_direct");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> codec.decode(root.toString())).getMessage().contains("palette"));

        root.addProperty("renderMode", "palette");
        root.addProperty("id", UiThemeBuiltins.LEGACY_ID);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> codec.decode(root.toString())).getMessage().contains("built-in"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.encode(UiThemeBuiltins.legacy()));
    }

    @Test
    void unreadableContrastIsRejectedBeforeRegistration() {
        UiThemeDefinition source = userCopy("test:contrast", UiThemeBuiltins.calibratedDark());
        JsonObject root = JsonParser.parseString(codec.encode(source)).getAsJsonObject();
        root.getAsJsonObject("tokens").addProperty("text_primary", "#FF171F28");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> codec.decode(root.toString()));
        assertTrue(error.getMessage().contains("contrast text_primary/surface"));
    }

    @Test
    void allFourPaletteBuiltinsMeetTheAutomaticContrastGate() {
        assertDoesNotThrow(() -> UiThemeValidator.validateContrast(UiThemeBuiltins.calibratedDark()));
        assertDoesNotThrow(() -> UiThemeValidator.validateContrast(UiThemeBuiltins.nordCommand()));
        assertDoesNotThrow(() -> UiThemeValidator.validateContrast(UiThemeBuiltins.carbonOperations()));
        assertDoesNotThrow(() -> UiThemeValidator.validateContrast(UiThemeBuiltins.materialField()));
    }

    private static UiThemeDefinition userCopy(String id, UiThemeDefinition source) {
        EnumMap<UiThemeToken, UiColor> tokens = new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
        tokens.putAll(source.tokens());
        EnumMap<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> components =
                new EnumMap<UiThemeCoverageCatalog.ComponentFamily,
                        Map<UiThemeToken, UiColor>>(UiThemeCoverageCatalog.ComponentFamily.class);
        for (Map.Entry<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> entry
                : source.components().entrySet()) {
            EnumMap<UiThemeToken, UiColor> colors = new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
            colors.putAll(entry.getValue());
            components.put(entry.getKey(), colors);
        }
        return new UiThemeDefinition(id, "Test Theme", "Codex", "Test",
                UiThemeRenderMode.PALETTE, UiThemeBuiltins.PIXEL_TEXTURE_SET,
                true, tokens, components);
    }
}
