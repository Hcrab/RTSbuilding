package com.rtsbuilding.rtsbuilding.network.builder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止机械回搬时重新引入 ItemStack.EMPTY 才成立的直接 copy 假设。 */
class NullableStackBoundaryContractTest {
    @Test
    void allNullableBuilderHandlersUseThe1710CompatibilityCopy() throws IOException {
        String mining = read("src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/"
                + "RtsMiningHandlers1122.java");
        String placement = read("src/main/java/com/rtsbuilding/rtsbuilding/network/builder/handler/"
                + "RtsPlacementActionHandlers1122.java");

        assertFalse(mining.contains("toolPrototype().copy()"));
        assertEquals(4, occurrences(mining, "StackCompat.copyOrNull(message.toolPrototype())"));
        assertFalse(placement.contains("itemPrototype().copy()"));
        assertEquals(3, occurrences(placement, "StackCompat.copyOrNull(message.itemPrototype())"));
    }

    @Test
    void allRemoteInteractionRemaindersUseThe1710CompatibilityCopy() throws IOException {
        String helper = read("src/main/java/com/rtsbuilding/rtsbuilding/server/util/InteractionHelper.java");
        String linked = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/interaction/"
                + "RtsLinkedItemInteractor.java");

        assertFalse(helper.contains("player.getHeldItem().copy()"));
        assertEquals(5, occurrences(helper, "StackCompat.copyOrNull(player.getHeldItem())"));
        assertFalse(linked.contains("remainder().copy()"));
        assertEquals(3, occurrences(linked, "StackCompat.copyOrNull("));
    }

    @Test
    void pinnedInteractionCarriesAndConsumesTheSelectedStackPrototype() throws IOException {
        String payload = read("src/main/java/com/rtsbuilding/rtsbuilding/network/builder/"
                + "C2SRtsInteractPayload.java");
        String client = read("src/main/java/com/rtsbuilding/rtsbuilding/client/service/"
                + "BuildPlacementService.java");
        String linked = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/interaction/"
                + "RtsLinkedItemInteractor.java");

        assertTrue(payload.contains("RtsPacketBuffer.writeItemStack(b, itemPrototype)"));
        assertTrue(payload.contains("RtsPacketBuffer.readItemStack(b)"));
        assertTrue(occurrences(client, "this.selectedItemPreview") >= 2);
        assertTrue(linked.contains("RtsPlacementExtractor.sanitizePrototype(itemId, itemPrototype)"));
        assertTrue(linked.contains("RtsPlacementExtractor.creativeStack(item, preferredStack)"));
    }

    @Test
    void realClientSmokeExercisesStartAndAbortWithNoTool() throws IOException {
        String smoke = read("src/main/java/com/rtsbuilding/rtsbuilding/client/compat/"
                + "RtsClientStartupSmoke.java");
        assertTrue(smoke.contains("EMPTY_TOOL_MINE_START_SENT"));
        assertTrue(smoke.contains("sendMineStart("));
        assertTrue(smoke.contains("EMPTY_TOOL_MINE_ABORT_SENT"));
        assertTrue(smoke.contains("sendMineAbort("));
        assertTrue(smoke.contains("EMPTY_TOOL_MINING_ROUND_TRIP_OK"));
        assertTrue(smoke.contains("EMPTY_HAND_INTERACTION_SENT"));
        assertTrue(smoke.contains("EMPTY_HAND_INTERACTION_ROUND_TRIP_OK"));
        assertTrue(smoke.contains("CREATIVE_PINNED_PROTOTYPE_SENT"));
        assertTrue(smoke.contains("CREATIVE_PINNED_PROTOTYPE_ROUND_TRIP_OK"));
        assertTrue(smoke.contains("new ItemStack(Blocks.wool, 1, 14)"));
        assertTrue(smoke.contains("isServerRunning()"));
    }

    @Test
    void realClientSmokeRejectsUnresolvedTranslations() throws IOException {
        String smoke = read("src/main/java/com/rtsbuilding/rtsbuilding/client/compat/"
                + "RtsClientStartupSmoke.java");
        assertTrue(smoke.contains("verifyTranslation(minecraft, \"screen.rtsbuilding.plugins\")"));
        assertTrue(smoke.contains("key.equals(translated)"));
        assertTrue(smoke.contains("I18N_OK key="));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(token, index)) >= 0; index += token.length()) {
            count++;
        }
        return count;
    }
}
