package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RtsLinkedStorageResolver} — focuses on link-mode
 * normalization, summary building, and storage-availability queries that do
 * NOT reach {@code ItemStack} static initializers or require a bootstrapped
 * Minecraft environment.
 *
 * <p>Methods requiring a live in-game level ({@code canAccessWorldTarget},
 * {@code isLinkedRefWorldVisible}, BD-network paths of
 * {@code hasAnyStorage}/{@code buildAnyStorageSummary}) are not covered
 * here; they would need a fully bootstrapped environment or the
 * Beyond Dimensions mod on the test classpath.
 */
@ExtendWith(MockitoExtension.class)
class RtsLinkedStorageResolverTest {

    // ======================================================================
    //  Helpers
    // ======================================================================

    /**
     * Creates a mock {@link RtsStorageSession} with real collection fields
     * wired in via reflection, so tests can populate {@code linkedStorages},
     * {@code linkedNames}, {@code linkedModes}, and {@code linkedPriorities}
     * without triggering the real constructor (which accesses
     * {@code ItemStack.EMPTY}).
     */
    private static RtsStorageSession createMockSession() {
        RtsStorageSession session = mock(RtsStorageSession.class);
        try {
            Field storagesField = RtsStorageSession.class.getDeclaredField("linkedStorages");
            storagesField.setAccessible(true);
            storagesField.set(session, new ArrayList<LinkedStorageRef>());

            Field namesField = RtsStorageSession.class.getDeclaredField("linkedNames");
            namesField.setAccessible(true);
            namesField.set(session, new HashMap<LinkedStorageRef, String>());

            Field modesField = RtsStorageSession.class.getDeclaredField("linkedModes");
            modesField.setAccessible(true);
            modesField.set(session, new HashMap<LinkedStorageRef, Byte>());

            Field prioritiesField = RtsStorageSession.class.getDeclaredField("linkedPriorities");
            prioritiesField.setAccessible(true);
            prioritiesField.set(session, new HashMap<LinkedStorageRef, Integer>());
        } catch (Exception e) {
            throw new RuntimeException("Failed to wire session fields", e);
        }
        return session;
    }

    /**
     * Creates a minimal {@link LinkedStorageRef} with mocked dimension key.
     */
    private static LinkedStorageRef createRef() {
        @SuppressWarnings("unchecked")
        ResourceKey<Level> dim = (ResourceKey<Level>) mock(ResourceKey.class);
        return new LinkedStorageRef(dim, BlockPos.ZERO);
    }

    // ======================================================================
    //  sanitizeLinkMode
    // ======================================================================

    @Test
    void sanitizeLinkModeBidirectionalReturnsBidirectional() {
        byte result = RtsLinkedStorageResolver.sanitizeLinkMode(
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        assertEquals(RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL, result);
    }

    @Test
    void sanitizeLinkModeExtractOnlyReturnsExtractOnly() {
        // MODE_EXTRACT_ONLY = 1 (from C2SRtsLinkStoragePayload)
        byte result = RtsLinkedStorageResolver.sanitizeLinkMode((byte) 1);
        assertEquals((byte) 1, result);
    }

    @Test
    void sanitizeLinkModeUnknownModeDefaultsToBidirectional() {
        byte result = RtsLinkedStorageResolver.sanitizeLinkMode((byte) 42);
        assertEquals(RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL, result);
    }

    @Test
    void sanitizeLinkModeZeroIsBidirectional() {
        byte result = RtsLinkedStorageResolver.sanitizeLinkMode((byte) 0);
        assertEquals(RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL, result);
    }

    // ======================================================================
    //  sanitizeLinkedStoragePriority
    // ======================================================================

    @Test
    void sanitizePriorityClampsBelowMin() {
        assertEquals(-9999, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(-100000));
    }

    @Test
    void sanitizePriorityClampsAboveMax() {
        assertEquals(9999, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(100000));
    }

    @Test
    void sanitizePriorityPreservesZero() {
        assertEquals(0, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(0));
    }

    @Test
    void sanitizePriorityPreservesMidValue() {
        assertEquals(500, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(500));
    }

    @Test
    void sanitizePriorityPreservesEdgeValues() {
        assertEquals(-9999, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(-9999));
        assertEquals(9999, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(9999));
    }

    // ======================================================================
    //  isExtractOnlyLink
    // ======================================================================

    @Test
    void isExtractOnlyLinkNullSessionReturnsFalse() {
        assertFalse(RtsLinkedStorageResolver.isExtractOnlyLink(null, createRef()));
    }

    @Test
    void isExtractOnlyLinkNullRefReturnsFalse() {
        assertFalse(RtsLinkedStorageResolver.isExtractOnlyLink(createMockSession(), null));
    }

    @Test
    void isExtractOnlyLinkBidirectionalModeReturnsFalse() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef ref = createRef();
        session.linkedModes.put(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
        assertFalse(RtsLinkedStorageResolver.isExtractOnlyLink(session, ref));
    }

    @Test
    void isExtractOnlyLinkExtractOnlyModeReturnsTrue() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef ref = createRef();
        session.linkedModes.put(ref, (byte) 1); // MODE_EXTRACT_ONLY
        assertTrue(RtsLinkedStorageResolver.isExtractOnlyLink(session, ref));
    }

    @Test
    void isExtractOnlyLinkUnknownRefDefaultsToBidirectional() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef knownRef = createRef();
        LinkedStorageRef otherRef = createRef();
        session.linkedModes.put(knownRef, (byte) 1); // EXTRACT_ONLY for knownRef
        assertFalse(RtsLinkedStorageResolver.isExtractOnlyLink(session, otherRef));
    }

    // ======================================================================
    //  buildLinkedSummary
    // ======================================================================

    @Test
    void buildLinkedSummaryEmptyListReturnsNoStorage() {
        RtsStorageSession session = createMockSession();
        assertEquals("No Storage", RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummarySingleBidirectionalReturnsName() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef ref = createRef();
        session.linkedStorages.add(ref);
        session.linkedNames.put(ref, "Chest");
        assertEquals("Chest", RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummarySingleExtractOnlyReturnsExtractSuffix() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef ref = createRef();
        session.linkedStorages.add(ref);
        session.linkedNames.put(ref, "Barrel");
        session.linkedModes.put(ref, (byte) 1); // MODE_EXTRACT_ONLY
        assertEquals("Barrel [Extract]", RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummarySingleWithDefaultNameWhenMissing() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef ref = createRef();
        session.linkedStorages.add(ref);
        // No linkedNames entry for this ref
        assertEquals("Linked Storage", RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummaryMultipleBidirectionalReturnsCount() {
        RtsStorageSession session = createMockSession();
        session.linkedStorages.add(createRef());
        session.linkedStorages.add(createRef());
        session.linkedStorages.add(createRef());
        assertEquals("3 linked storages", RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummaryMultipleWithMixedExtractOnly() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef refA = createRef();
        LinkedStorageRef refB = createRef();
        LinkedStorageRef refC = createRef();
        session.linkedStorages.add(refA);
        session.linkedStorages.add(refB);
        session.linkedStorages.add(refC);
        session.linkedModes.put(refA, (byte) 1); // EXTRACT_ONLY
        // refB and refC are bidirectional (default)
        assertEquals("3 linked storages (1 extract-only)",
                RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    @Test
    void buildLinkedSummaryMultipleAllExtractOnly() {
        RtsStorageSession session = createMockSession();
        LinkedStorageRef refA = createRef();
        LinkedStorageRef refB = createRef();
        session.linkedStorages.add(refA);
        session.linkedStorages.add(refB);
        session.linkedModes.put(refA, (byte) 1);
        session.linkedModes.put(refB, (byte) 1);
        assertEquals("2 linked storages (2 extract-only)",
                RtsLinkedStorageResolver.buildLinkedSummary(session));
    }

    // ======================================================================
    //  hasAnyStorage / buildAnyStorageSummary
    // ======================================================================
    //
    //  These methods require a ServerPlayer. Mocking ServerPlayer in a unit
    //  test triggers Minecraft's ParticleTypes static initializer, which is
    //  not available without a bootstrapped game environment. The pure-logic
    //  paths (session null-check, buildLinkedSummary delegation) are covered
    //  by the linked-summary tests above.
    //
    //  ServerPlayer-dependent paths such as RtsBdCompat lookups would also
    //  need the Beyond Dimensions mod on the test classpath, making them
    //  integration-level instead of unit-level.
    // ======================================================================

    @Test
    void buildAnyStorageSummaryNullSessionReturnsNoStorage() {
        assertEquals("No Storage", RtsLinkedStorageResolver.buildAnyStorageSummary(null, null));
    }
}
