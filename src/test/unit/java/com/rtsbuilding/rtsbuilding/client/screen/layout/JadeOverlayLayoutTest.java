package com.rtsbuilding.rtsbuilding.client.screen.layout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JadeOverlayLayoutTest {

    @AfterEach
    void clearReservation() {
        JadeOverlayLayout.clearReservation();
    }

    @Test
    void anchoredPositionStaysImmediatelyLeftOfGearAtOneX() {
        JadeOverlayLayout.Position position = JadeOverlayLayout.anchored(
                960, 540, 180, 80, 1.0D);

        assertEquals(732, position.x());
        assertEquals(4, position.y());
    }

    @Test
    void anchoredPositionScalesTopBarMarginsAtTwoX() {
        JadeOverlayLayout.Position position = JadeOverlayLayout.anchored(
                960, 540, 180, 80, 2.0D);

        assertEquals(684, position.x());
        assertEquals(8, position.y());
    }

    @Test
    void cursorPositionFlipsToLeftNearRightEdge() {
        JadeOverlayLayout.Position position = JadeOverlayLayout.followingCursor(
                300, 200, 100, 60, 280, 100);

        assertEquals(172, position.x());
        assertEquals(70, position.y());
    }

    @Test
    void oversizedPanelIsClampedInsideScreen() {
        JadeOverlayLayout.Position position = JadeOverlayLayout.anchored(
                120, 80, 240, 160, 1.0D);

        assertEquals(0, position.x());
        assertEquals(0, position.y());
    }

    @Test
    void reservationConvertsBackToRtsVirtualCoordinates() {
        JadeOverlayLayout.publishAnchoredReservation(685, 2.0D);

        assertEquals(342, JadeOverlayLayout.currentReservedLeftVirtualX());
    }
}
