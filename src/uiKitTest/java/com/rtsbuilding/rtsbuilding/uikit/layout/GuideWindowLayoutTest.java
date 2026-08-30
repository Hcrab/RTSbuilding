package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
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

    @Test
    void anchoredBottomOpeningMatchesProductionGeometry() {
        assertRect(690, 444, 330, 198,
                GuideWindowLayout.openingRect(GuideUiContext.BOTTOM,
                        1280, 720, 330, 198, 1000, 650, 24, 520, 220));
    }

    @Test
    void settingsOpeningUsesAvailableSideWithoutOverlappingSettings() {
        assertRect(154, 250, 330, 198,
                GuideWindowLayout.openingRect(GuideUiContext.SETTINGS,
                        1280, 720, 330, 198, -1, -1, 24, 520, 220));
    }

    @Test
    void geometryOwnsTopicTextAndScrollbarOffsets() {
        GuideWindowLayout.Geometry geometry = GuideWindowLayout.geometry(
                new UiRect(101, 220, 330, 177), false);

        assertEquals(new UiRect(109, 228, 20, 161), geometry.topicArea);
        assertEquals(new UiRect(109, 228, 28, 161), geometry.topicScrollRoute);
        assertEquals(new UiRect(132, 228, 3, 161), geometry.topicScrollbar);
        assertEquals(new UiRect(139, 230, 268, 10), geometry.title);
        assertEquals(new UiRect(139, 246, 268, 141), geometry.body);
        assertEquals(new UiRect(423, 246, 3, 141), geometry.bodyScrollbar);
        assertEquals(7, geometry.visibleTopicRows);
        assertEquals(11, geometry.visibleTextLines);
    }

    @Test
    void hitRoutingUsesHalfOpenRowsAndSeparatesTopicFromTextScroll() {
        GuideWindowLayout.Geometry geometry = GuideWindowLayout.geometry(
                new UiRect(101, 220, 330, 177), false);

        GuideWindowLayout.Hit topic = GuideWindowLayout.hitAt(
                geometry, 109, 228, 2, 12);
        assertEquals(GuideWindowLayout.Target.TOPIC, topic.target);
        assertEquals(2, topic.topicIndex);
        assertEquals(GuideWindowLayout.Target.TOPIC_SCROLL,
                GuideWindowLayout.hitAt(geometry, 129, 228, 2, 12).target);
        assertEquals(GuideWindowLayout.Target.TEXT_SCROLL,
                GuideWindowLayout.hitAt(geometry, 140, 228, 2, 12).target);
        assertEquals(GuideWindowLayout.Target.NONE,
                GuideWindowLayout.hitAt(geometry, 431, 228, 2, 12).target);
    }

    private static void assertRect(int x, int y, int w, int h, GuideWindowLayout.Rect rect) {
        assertEquals(x, rect.x);
        assertEquals(y, rect.y);
        assertEquals(w, rect.w);
        assertEquals(h, rect.h);
    }
}
