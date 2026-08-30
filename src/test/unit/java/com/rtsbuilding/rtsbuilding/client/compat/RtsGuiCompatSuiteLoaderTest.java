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
                  "interactionItemId":"ars_nouveau:novice_spell_book",
                  "hitFace":"north","hitOffsetY":0.25,"hitOffsetZ":-0.45
                }]}
                """);

        RtsGuiCompatCase guiCase = RtsGuiCompatSuiteLoader.load(suitePath).cases().getFirst();

        assertEquals(80, guiCase.setupWaitTicks());
        assertEquals("ars_nouveau:novice_spell_book", guiCase.interactionItemId());
        assertEquals("NORTH", guiCase.hitFace());
        assertEquals(0.0D, guiCase.hitOffsetX());
        assertEquals(0.25D, guiCase.hitOffsetY());
        assertEquals(-0.45D, guiCase.hitOffsetZ());
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

        Path unsupportedFace = this.temporaryDirectory.resolve("face.json");
        Files.writeString(unsupportedFace, """
                {"suiteId":"bad","cases":[{"id":"x","blockId":"minecraft:chest",
                "hitFace":"diagonal"}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> RtsGuiCompatSuiteLoader.load(unsupportedFace));

        Path invalidOffset = this.temporaryDirectory.resolve("offset.json");
        Files.writeString(invalidOffset, """
                {"suiteId":"bad","cases":[{"id":"x","blockId":"minecraft:chest",
                "hitOffsetX":0.75}]}
                """);
        assertThrows(IllegalArgumentException.class, () -> RtsGuiCompatSuiteLoader.load(invalidOffset));
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

    @Test
    void acceptsSpecialStructureAdapters() throws Exception {
        Path suitePath = this.temporaryDirectory.resolve("special-structures.json");
        Files.writeString(suitePath, """
                {
                  "suiteId": "special-structures",
                  "cases": [
                    {
                      "id": "scanner",
                      "blockId": "securitycraft:inventory_scanner",
                      "distanceProfile": "FAR_24",
                      "setupAdapter": "securitycraft_inventory_scanner_pair"
                    },
                    {
                      "id": "foundry",
                      "blockId": "productivemetalworks:black_foundry_controller",
                      "distanceProfile": "COLD_160",
                      "setupAdapter": "productive_metalworks_minimal_foundry"
                    },
                    {
                      "id": "reactor",
                      "blockId": "bigreactors:basic_reactorcontroller",
                      "distanceProfile": "FAR_24",
                      "setupAdapter": "extreme_reactors_minimal_reactor"
                    },
                    {
                      "id": "integrated-terminal",
                      "blockId": "integrateddynamics:cable",
                      "distanceProfile": "COLD_160",
                      "setupAdapter": "integrated_terminal_storage_part"
                    }
                  ]
                }
                """);

        var loaded = RtsGuiCompatSuiteLoader.load(suitePath);

        assertEquals("securitycraft_inventory_scanner_pair", loaded.cases().get(0).setupAdapter());
        assertEquals("productive_metalworks_minimal_foundry", loaded.cases().get(1).setupAdapter());
        assertEquals("extreme_reactors_minimal_reactor", loaded.cases().get(2).setupAdapter());
        assertEquals("integrated_terminal_storage_part", loaded.cases().get(3).setupAdapter());
    }
}
