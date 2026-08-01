package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsRemoteMenuTraceStateTest {
    private static final long START = 1_000_000_000L;

    @Test
    void compressesOpenMissingRecoveredAndClosedLifecycle() {
        RtsRemoteMenuTraceState state = new RtsRemoteMenuTraceState();
        state.receiveHint("120,64,120", START);

        assertEquals(RtsRemoteMenuTraceState.Event.MENU_OPENED,
                state.observe(7, "example.Menu", "example.Screen", 80, START + 1).event());
        assertEquals(RtsRemoteMenuTraceState.Event.SCREEN_MISSING,
                state.observe(7, "example.Menu", "", 0, START + 2).event());
        assertEquals(RtsRemoteMenuTraceState.Event.NONE,
                state.observe(7, "example.Menu", "", 0, START + 3).event());
        assertEquals(RtsRemoteMenuTraceState.Event.SCREEN_RECOVERED,
                state.observe(7, "example.Menu", "example.Screen", 0, START + 4).event());
        assertEquals(RtsRemoteMenuTraceState.Event.MENU_CLOSED,
                state.observe(0, "", "", 0, START + 5).event());
    }

    @Test
    void reportsHintExpiryWhenNoMenuEverArrives() {
        RtsRemoteMenuTraceState state = new RtsRemoteMenuTraceState();
        state.receiveHint("20,70,-30", START);

        assertEquals(RtsRemoteMenuTraceState.Event.NONE,
                state.observe(0, "", "example.BuilderScreen", 1, START + 1).event());
        RtsRemoteMenuTraceState.Transition expired =
                state.observe(0, "", "example.BuilderScreen", 0, START + 2_000_000L);

        assertEquals(RtsRemoteMenuTraceState.Event.HINT_EXPIRED, expired.event());
        assertEquals("20,70,-30", expired.target());
        assertEquals(1L, expired.sessionId());
    }

    @Test
    void reportsContainerOrMenuReplacementOnlyOnce() {
        RtsRemoteMenuTraceState state = new RtsRemoteMenuTraceState();
        state.receiveHint("0,64,0", START);
        state.observe(3, "example.FirstMenu", "example.FirstScreen", 80, START + 1);

        assertEquals(RtsRemoteMenuTraceState.Event.MENU_CHANGED,
                state.observe(4, "example.SecondMenu", "example.SecondScreen", 0, START + 2).event());
        assertEquals(RtsRemoteMenuTraceState.Event.NONE,
                state.observe(4, "example.SecondMenu", "example.SecondScreen", 0, START + 3).event());
    }
}
