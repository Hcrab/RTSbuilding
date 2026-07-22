package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuideWindowLayoutTest {
    @Test
    void exactDefaultContentFormulasRemainShared() {
        assertEquals(92, GuideWindowLayout.topicTabWidth(true));
        assertEquals(20, GuideWindowLayout.topicTabWidth(false));
        assertEquals(7, GuideWindowLayout.visibleTopicRows(177));
        assertEquals(268, GuideWindowLayout.textMaxWidth(330, 20));
    }
}
