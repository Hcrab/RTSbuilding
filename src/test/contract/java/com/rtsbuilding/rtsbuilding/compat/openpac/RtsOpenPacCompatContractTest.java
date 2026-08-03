package com.rtsbuilding.rtsbuilding.compat.openpac;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsOpenPacCompatContractTest {
    @Test
    void openPacCompatStaysOptionalAndRejectsUnsupportedModernApi() throws IOException {
        String facade = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompat.java");
        String impl = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompatImpl.java");

        assertTrue(facade.contains("Loader.isModLoaded(MOD_ID)"),
                "OpenPAC compat must stay disabled unless the mod is present");
        assertTrue(impl.contains("has no official Minecraft 1.12.2 release or API"),
                "1.12 must explicitly report that the modern OpenPAC API is unavailable");
        assertFalse(impl.contains("Class.forName(\"xaero."),
                "1.12 must not pretend a modern OpenPAC API can be loaded reflectively");
        assertFalse(impl.contains("import xaero."),
                "runtime compat should not import OpenPAC classes directly");
    }

    @Test
    void unsupportedOpenPacFailsClosedWhileFtbClaimsRemainActionSpecific() throws IOException {
        String facade = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompat.java");

        assertTrue(facade.contains("RtsFtbCompat.canEditBlock(player, pos)"),
                "1.12 block break/place claims must use the supported FTB Utilities bridge");
        assertTrue(facade.contains("RtsFtbCompat.canInteractBlock(player, pos, face, hand, heldItem)"),
                "1.12 block interaction claims must preserve action context");
        assertTrue(facade.contains("RtsFtbCompat.canInteractEntity(player, target, hand, heldItem, attack)"),
                "1.12 entity claims must preserve interaction/attack intent");
        assertTrue(count(facade, "&& !OPENPAC_LOADED") == 4,
                "an unknown OpenPAC backport must fail closed for every world action");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static int count(String source, String token) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(token, cursor)) >= 0) {
            count++;
            cursor += token.length();
        }
        return count;
    }
}
