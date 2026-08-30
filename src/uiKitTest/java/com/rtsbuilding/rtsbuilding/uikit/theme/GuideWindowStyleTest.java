package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class GuideWindowStyleTest {
    @Test
    void semanticStatesPreserveProductionColors() {
        assertEquals(0xCC355A71, GuideWindowStyle.topicBackground(true).toArgb());
        assertEquals(0x88303A45, GuideWindowStyle.topicBackground(false).toArgb());
        assertEquals(0xFF8FB4D0, GuideWindowStyle.topicBorderLight(true).toArgb());
        assertEquals(0xFF4A5665, GuideWindowStyle.topicBorderLight(false).toArgb());
        assertEquals(0xFFF4FBFF, GuideWindowStyle.topicContent(true).toArgb());
        assertEquals(0xFFB9C7D5, GuideWindowStyle.topicContent(false).toArgb());
        assertEquals(RtsMainlineTheme.GUIDE_HINT, GuideWindowStyle.TITLE_TEXT);
        assertEquals(RtsMainlineTheme.GUIDE_HINT, GuideWindowStyle.HINT_TEXT);
    }

    @Test
    void topicHoverAndSelectionExposeRealIntermediateColors() {
        UiColor middle = GuideWindowStyle.topicBackground(0.5D, 0.0D);
        assertNotEquals(GuideWindowStyle.TOPIC_IDLE_BACKGROUND, middle);
        assertNotEquals(GuideWindowStyle.TOPIC_SELECTED_BACKGROUND, middle);

        UiColor hover = GuideWindowStyle.topicBorderLight(0.0D, 0.5D);
        assertNotEquals(GuideWindowStyle.TOPIC_IDLE_BORDER_LIGHT, hover);
        assertNotEquals(GuideWindowStyle.TOPIC_HOVER_BORDER_LIGHT, hover);
    }
}
