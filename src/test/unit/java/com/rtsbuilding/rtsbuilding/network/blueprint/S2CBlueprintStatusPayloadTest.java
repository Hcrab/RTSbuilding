package com.rtsbuilding.rtsbuilding.network.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class S2CBlueprintStatusPayloadTest {
    @Test
    void constructorBoundsUntrustedFailureDetailWithoutSplittingSurrogatePair() {
        String prefix = "x".repeat(S2CBlueprintStatusPayload.MAX_TEXT_CHARS - 1);
        String oversized = prefix + "\uD83D\uDE31" + "secret-path";

        S2CBlueprintStatusPayload payload = new S2CBlueprintStatusPayload(
                S2CBlueprintStatusPayload.ERROR, "key", oversized);

        assertEquals(S2CBlueprintStatusPayload.MAX_TEXT_CHARS - 1, payload.detail().length());
        assertFalse(Character.isHighSurrogate(payload.detail().charAt(payload.detail().length() - 1)));
    }
}
