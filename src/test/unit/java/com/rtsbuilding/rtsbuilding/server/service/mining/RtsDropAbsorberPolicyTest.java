package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsDropAbsorberPolicyTest {
    @Test
    void anyExtractOnlyEndpointMustDisableAggregateInsertFastPath() {
        RtsStorageSession session = new RtsStorageSession();
        LinkedStorageRef writable = new LinkedStorageRef(0, new BlockPos(1, 64, 1));
        LinkedStorageRef extractOnly = new LinkedStorageRef(0, new BlockPos(2, 64, 2));

        session.linkedStorageInfo.add(
                writable, C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL, 0);
        assertFalse(RtsDropAbsorber.hasExtractOnlyLinkedStorage(session));

        session.linkedStorageInfo.add(
                extractOnly, C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY, 0);
        assertTrue(RtsDropAbsorber.hasExtractOnlyLinkedStorage(session),
                "混合链接中只要有一个 Extract Only，掉落入库就必须绕开聚合写入快路");
    }

    @Test
    void finalStorePermissionMustFailClosedForMissingExtractOnlyAndUnlinkedRefs() {
        RtsStorageSession session = new RtsStorageSession();
        LinkedStorageRef ref = new LinkedStorageRef(0, new BlockPos(3, 64, 3));

        assertFalse(RtsLinkedStorageResolver.isStoreAllowed(session, ref), "Unlinked refs must reject writes");
        session.linkedStorageInfo.add(ref, C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL, 0);
        assertTrue(RtsLinkedStorageResolver.isStoreAllowed(session, ref));

        session.linkedStorageInfo.setMode(ref, C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY);
        assertFalse(RtsLinkedStorageResolver.isStoreAllowed(session, ref), "Extract Only must reject writes immediately");

        session.linkedStorageInfo.remove(ref);
        assertFalse(RtsLinkedStorageResolver.isStoreAllowed(session, ref), "Stale views must reject writes after unlink");
    }
}
