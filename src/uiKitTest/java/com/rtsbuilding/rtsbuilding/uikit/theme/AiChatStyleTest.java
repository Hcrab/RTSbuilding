package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AiChatStyleTest {
    @Test
    void semanticStatesPreserveImportedChatColors() {
        assertEquals(0xFF91A4B8, AiChatStyle.LIMIT_TEXT.toArgb());
        assertEquals(0xB8141B23, AiChatStyle.TRANSCRIPT_BACKGROUND.toArgb());
        assertEquals(0xFFB9C9D8, AiChatStyle.WELCOME_TEXT.toArgb());
        assertEquals(0xFFE7C46A, AiChatStyle.PLAYER_TEXT.toArgb());
        assertEquals(0xFFE6EDF8, AiChatStyle.AI_TEXT.toArgb());
        assertEquals(0xFFFF8D8D, AiChatStyle.ERROR_TEXT.toArgb());
        assertEquals(0xFFFFC46A, AiChatStyle.WARNING_TEXT.toArgb());
        assertEquals(0xFF8ED6A7, AiChatStyle.SUCCESS_TEXT.toArgb());
    }
}
