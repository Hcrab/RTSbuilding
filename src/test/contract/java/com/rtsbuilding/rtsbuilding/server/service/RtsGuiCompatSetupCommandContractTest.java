package com.rtsbuilding.rtsbuilding.server.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RtsGuiCompatSetupCommandContractTest {
    @Test
    void namespacedBlockIdsUseTheResourceLocationArgument() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsGuiCompatSetupCommand.java"));

        assertTrue(source.contains("Commands.argument(\"blockId\", ResourceLocationArgument.id())"));
        assertTrue(source.contains("ResourceLocationArgument.getId(context, \"blockId\").toString()"));
        assertTrue(source.contains("level.setChunkForced(targetChunk.x, targetChunk.z, true)"));
        assertTrue(source.contains("target changed after placement"));
        assertTrue(source.contains("player.getAbilities().invulnerable = true"));
        assertTrue(source.contains("block.setPlacedBy"));
        assertTrue(source.contains("prepareInteractionItem"));
        assertTrue(source.contains("prepareOritechMachine"));
        assertTrue(source.contains("preparePipezItemExtract"));
        assertTrue(source.contains("\"setExtracting\""));
        assertTrue(source.contains("Direction.NORTH"));
        assertTrue(source.contains("prepareSecurityCraftInventoryScannerPair"));
        assertTrue(source.contains("prepareProductiveMetalworksFoundry"));
        assertTrue(source.contains("productivemetalworks:powered_heating_coil"));
        assertTrue(source.contains("int foundryDepth = 4"));
        assertTrue(source.contains("z == foundryDepth - 1"));
        assertTrue(source.contains("\"detectMultiblock\", Level.class, BlockPos.class"));
        assertTrue(source.contains("\"setMultiBlockData\", detect.getReturnType()"));
        assertTrue(source.contains("prepareExtremeReactorsMinimalReactor"));
        assertTrue(source.contains("bigreactors:basic_reactorfuelrod"));
        assertTrue(source.contains("prepareIntegratedTerminalStoragePart"));
        assertTrue(source.contains("integratedterminals:part_terminal_storage"));
        assertTrue(source.contains("initialStateForAdapter"));
        assertTrue(source.contains("withBooleanProperty(state, \"core\", true)"));
    }
}
