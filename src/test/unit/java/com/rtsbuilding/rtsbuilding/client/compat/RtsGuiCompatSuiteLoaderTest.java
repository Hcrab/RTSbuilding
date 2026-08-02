package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatSuiteLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsFarAndColdProfilesWithoutGuessing() throws Exception {
        Path suitePath = this.temporaryDirectory.resolve("suite.json");
        Files.writeString(suitePath, """
                {
                  "suiteId": "atm10-p0",
                  "stableTicks": 60,
                  "openTimeoutTicks": 120,
                  "cases": [
                    {
                      "id": "chest_far",
                      "blockId": "minecraft:chest",
                      "distanceProfile": "FAR_24",
                      "depth": "VANILLA_INTERACTION",
                      "setupAdapter": "vanilla_chest",
                      "expectedMenuRegex": ".*ChestMenu",
                      "expectedScreenRegex": ".*ContainerScreen"
                    },
                    {
                      "id": "enchant_cold",
                      "blockId": "minecraft:enchanting_table",
                      "distanceProfile": "COLD_160",
                      "depth": "VANILLA_INTERACTION",
                      "setupAdapter": "vanilla_enchanting",
                      "expectedMenuRegex": ".*EnchantmentMenu",
                      "expectedScreenRegex": ".*EnchantmentScreen"
                    }
                  ]
                }
                """);

        RtsGuiCompatSuiteLoader.RtsGuiCompatSuite suite = RtsGuiCompatSuiteLoader.load(suitePath);

        assertEquals("atm10-p0", suite.suiteId());
        assertEquals(60, suite.stableTicks());
        assertEquals(24, suite.cases().get(0).distance());
        assertEquals(160, suite.cases().get(1).distance());
        assertEquals(40, suite.cases().get(1).setupWaitTicks());
        assertTrue(suite.cases().get(1).setupCommand().contains(" 160 vanilla_enchanting"));
    }

    @Test
    void loadsReusableSetupWaitAndInteractionItem() throws Exception {
        Path suitePath = this.temporaryDirectory.resolve("modded.json");
        Files.writeString(suitePath, """
                {"suiteId":"modded","cases":[{
                  "id":"ars_scribe","blockId":"ars_nouveau:scribes_table",
                  "setupAdapter":"single_block","setupWaitTicks":80,
                  "interactionItemId":"ars_nouveau:novice_spell_book"
                }]}
                """);

        RtsGuiCompatCase guiCase = RtsGuiCompatSuiteLoader.load(suitePath).cases().getFirst();

        assertEquals(80, guiCase.setupWaitTicks());
        assertEquals("ars_nouveau:novice_spell_book", guiCase.interactionItemId());
        assertTrue(guiCase.setupCommand().endsWith("single_block ars_nouveau:novice_spell_book"));
    }

    @Test
    void rejectsUnknownFieldsAndUnsupportedAdapters() throws Exception {
        Path unknownField = this.temporaryDirectory.resolve("unknown.json");
        Files.writeString(unknownField, """
                {"suiteId":"bad","mystery":true,"cases":[{"id":"x","blockId":"minecraft:chest"}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> RtsGuiCompatSuiteLoader.load(unknownField));

        Path unsupportedAdapter = this.temporaryDirectory.resolve("adapter.json");
        Files.writeString(unsupportedAdapter, """
                {"suiteId":"bad","cases":[{"id":"x","blockId":"minecraft:chest",
                "setupAdapter":"invent_something"}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> RtsGuiCompatSuiteLoader.load(unsupportedAdapter));
    }

    @Test
    void rejectsDuplicateCaseIds() throws Exception {
        Path suitePath = this.temporaryDirectory.resolve("duplicate.json");
        Files.writeString(suitePath, """
                {"suiteId":"duplicate","cases":[
                  {"id":"same","blockId":"minecraft:chest"},
                  {"id":"same","blockId":"minecraft:furnace"}
                ]}
                """);

        assertThrows(IllegalArgumentException.class, () -> RtsGuiCompatSuiteLoader.load(suitePath));
    }
}
