package com.rtsbuilding.rtsbuilding.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSnapshotRefreshGateTest {
    @Test
    void stableSnapshotRefreshesOnlyAtConfiguredCycle() {
        NetworkSnapshotRefreshGate gate = new NetworkSnapshotRefreshGate();

        assertFalse(gate.shouldRefresh(3));
        assertFalse(gate.shouldRefresh(3));
        assertTrue(gate.shouldRefresh(3));

        gate.markRefreshed();
        assertFalse(gate.shouldRefresh(3));
    }

    @Test
    void mutationForcesNextCacheCycleToRefresh() {
        NetworkSnapshotRefreshGate gate = new NetworkSnapshotRefreshGate();
        gate.markStale();

        assertTrue(gate.shouldRefresh(200));
        gate.markRefreshed();
        assertFalse(gate.shouldRefresh(200));
    }

    @Test
    void invalidThrottleIsClampedToOneCycle() {
        NetworkSnapshotRefreshGate gate = new NetworkSnapshotRefreshGate();
        assertTrue(gate.shouldRefresh(0));
    }
}
