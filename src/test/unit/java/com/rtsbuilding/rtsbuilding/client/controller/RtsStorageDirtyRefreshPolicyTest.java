package com.rtsbuilding.rtsbuilding.client.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsStorageDirtyRefreshPolicyTest {
    private static final long FALLBACK_MS = 30_000L;

    @Test
    void visibleDirtySnapshotRequestsOnNextTick() {
        assertTrue(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, true, false, true, false, 0L, 1_000L, 1_001L, FALLBACK_MS));
    }

    @Test
    void unopenedViewNeverRequestsEvenAfterFallbackInterval() {
        assertFalse(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, false, false, true, false, 0L, 1_000L, 61_000L, FALLBACK_MS));
        assertFalse(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, false, false, true, true, 1_000L, 1_000L, 61_000L, FALLBACK_MS));
    }

    @Test
    void visiblePendingRequestOnlyRetriesAfterFallback() {
        assertFalse(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, true, true, true, true, 1_000L, 1_000L, 30_999L, FALLBACK_MS));
        assertTrue(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, true, true, true, true, 1_000L, 1_000L, 31_000L, FALLBACK_MS));
    }

    @Test
    void missingDirtyStateOrSnapshotSendsNothing() {
        assertFalse(RtsStorageDirtyRefreshPolicy.shouldRequest(
                false, true, false, true, false, 0L, 0L, 1_001L, FALLBACK_MS));
        assertFalse(RtsStorageDirtyRefreshPolicy.shouldRequest(
                true, true, false, false, false, 0L, 1_000L, 1_001L, FALLBACK_MS));
    }
}
