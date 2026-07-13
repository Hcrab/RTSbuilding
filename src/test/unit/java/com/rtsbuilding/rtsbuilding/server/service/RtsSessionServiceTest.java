package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RtsSessionService} — focuses on session-query methods
 * and null-guard behavior that do NOT reach {@code ItemStack.EMPTY} or
 * require a bootstrapped Minecraft environment.
 *
 * <p>Methods that internally create a {@code new RtsStorageSession()} (e.g.
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
        Map<UUID, RtsStorageSession> sessions = RtsSessionService.allSessions();
        assertThrows(UnsupportedOperationException.class,
                () -> sessions.put(UUID.randomUUID(), null));
    }

    // ======================================================================
    //  markStorageViewDirty — null guard
    // ======================================================================

    @Test
    void markStorageViewDirtyNullPlayerDoesNotThrow() {
        assertDoesNotThrow(() -> RtsSessionService.markStorageViewDirty(null, null));
    }
}
