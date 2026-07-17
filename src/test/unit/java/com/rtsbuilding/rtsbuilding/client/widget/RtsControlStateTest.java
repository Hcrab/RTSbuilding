package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsControlStateTest {
    @Test
    void enabledControlDropsStaleDisabledReason() {
        RtsControlState state = RtsControlState.enabled(RtsControlRole.COMMAND)
                .withEnabled(false, Component.literal("missing selection"))
                .withEnabled(true, Component.literal("stale"));

        assertTrue(state.enabled());
        assertNull(state.disabledReason());
    }

    @Test
    void failedStateCannotAlsoPretendToBePending() {
        RtsControlState state = RtsControlState.enabled(RtsControlRole.PRIMARY_ACTION)
                .withPending(true)
                .withFailed(true);

        assertTrue(state.failed());
        assertFalse(state.pending());
    }
}
