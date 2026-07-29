package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsAiResponseSanitizerTest {
    @Test
    void removesAllBoldMarkersButKeepsTheirText() {
        String response = "**直接回答**\n请按 **G** 键。";

        assertEquals("直接回答\n请按 G 键。",
                RtsAiResponseSanitizer.forInGameDisplay(response));
    }

    @Test
    void handlesMarkersThatWereSplitAcrossStreamingChunks() {
        String firstChunk = "*";
        String secondChunk = "*Reference**";

        assertEquals("Reference",
                RtsAiResponseSanitizer.forInGameDisplay(firstChunk + secondChunk));
    }

    @Test
    void preservesSingleStarsAndHandlesNull() {
        assertEquals("* item", RtsAiResponseSanitizer.forInGameDisplay("* item"));
        assertEquals("", RtsAiResponseSanitizer.forInGameDisplay(null));
    }
}
