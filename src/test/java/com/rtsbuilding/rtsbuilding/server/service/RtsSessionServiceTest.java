package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for session-query methods and null-guard behavior.
 *
 * <p>Uses {@link ServiceRegistry#init()} to instantiate services;
 * methods that internally create a {@code new RtsStorageSession()} (e.g.
 * {@code getOrCreate}, {@code onRtsEnabled}, {@code onRtsDisabled}) cannot
 * be tested here because the constructor touches {@code ItemStack.EMPTY}.
 * Likewise, methods calling {@code RtsProgressionManager}, {@code
 * PacketDistributor}, or other static NeoForge entry-points are tested only
 * for null-guard paths.
 */
class RtsSessionServiceTest {

    // ======================================================================
    //  allSessions
    // ======================================================================

    @Test
    void allSessionsReturnsUnmodifiableMap() {
        ServiceRegistry.init();
        Map<UUID, RtsStorageSession> sessions = ServiceRegistry.getInstance().session().allSessions();
        assertThrows(UnsupportedOperationException.class,
                () -> sessions.put(UUID.randomUUID(), null));
    }

    // ======================================================================
    //  markStorageViewDirty — null guard
    // ======================================================================

    @Test
    void markStorageViewDirtyNullPlayerDoesNotThrow() {
        assertDoesNotThrow(() -> ServiceRegistry.getInstance().page().markStorageViewDirty(null, null));
    }
}
