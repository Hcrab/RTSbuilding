package com.rtsbuilding.rtsbuilding.compat.openpac;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsOpenPacCompatContractTest {
    @Test
    void openPacCompatStaysOptionalAndUsesServerApiReflection() throws IOException {
        String facade = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompat.java");
        String impl = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompatImpl.java");

        assertTrue(facade.contains("ModList.get().isLoaded(MOD_ID)"),
                "OpenPAC compat must stay disabled unless the mod is present");
        assertTrue(impl.contains("Class.forName(\"xaero.pac.common.server.api.OpenPACServerAPI\")"),
                "OpenPAC API should be loaded reflectively, not as a hard dependency");
        assertFalse(impl.contains("import xaero."),
                "runtime compat should not import OpenPAC classes directly");
    }

    @Test
    void openPacCompatChecksPartiesAndActionSpecificClaimProtection() throws IOException {
        String impl = read("src/main/java/com/rtsbuilding/rtsbuilding/compat/openpac/RtsOpenPacCompatImpl.java");

        assertTrue(impl.contains("getPartyByMember"),
                "OpenPAC party lookup should use the player's current party");
        assertTrue(impl.contains("getDefaultName"),
                "OpenPAC team label should come from the party name");
        assertTrue(impl.contains("onBlockInteraction"),
                "block interaction and break checks must use OpenPAC action API");
        assertTrue(impl.contains("onEntityPlaceBlock"),
                "block placement must use OpenPAC placement API");
        assertTrue(impl.contains("onEntityInteraction"),
                "entity interaction must use OpenPAC entity interaction API");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
