package com.rtsbuilding.rtsbuilding.network.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RtsPluginPayloadTextTest {

    @Test
    void reportedTwoHundredFifteenCharacterTeamNameFitsPacketBoundary() {
        String oversized = "x".repeat(215);

        assertEquals(128, RtsPluginPayloadText.fit(oversized, 128).length());
    }

    @Test
    void truncationDoesNotSplitSurrogatePair() {
        String oversized = "a".repeat(127) + "😀" + "tail";
        String fitted = RtsPluginPayloadText.fit(oversized, 128);

        assertEquals(127, fitted.length());
        assertFalse(Character.isHighSurrogate(fitted.charAt(fitted.length() - 1)));
    }
}
