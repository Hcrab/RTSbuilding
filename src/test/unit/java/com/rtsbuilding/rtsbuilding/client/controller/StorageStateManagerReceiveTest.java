package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/** StorageStateManager 接收路径的真实 session/query/request/revision 过滤行为。 */
final class StorageStateManagerReceiveTest {
    @Test
    void samePageOutOfOrderRejectsOlderRequestButAcceptsAdjacentPage() {
        try (MockedStatic<RtsClientPacketGateway> gateway = mockStatic(RtsClientPacketGateway.class)) {
            gateway.when(() -> RtsClientPacketGateway.storageSearchSourceKey(anyString()))
                    .thenReturn("");
            StorageStateManager manager = new StorageStateManager();
            long session = manager.getStorageSessionId();

            manager.requestStoragePage(0);
            manager.requestStoragePage(0);
            long query = manager.getStorageQueryId();
            manager.applyStoragePage(page(0, 0, session, query, 2, 2, ""), null);
            assertEquals(1, manager.getStorageRevision());

            manager.applyStoragePage(page(0, 0, session, query, 1, 2, ""), null);
            assertEquals(1, manager.getStorageRevision(),
                    "迟到的同页旧请求不能覆盖较新的快照");

            manager.requestStoragePage(1);
            manager.applyStoragePage(page(1, 1, session, query, 3, 3, ""), null);
            assertEquals(2, manager.getStorageRevision(),
                    "同一查询的相邻页仍应被接收");
            assertEquals(1, manager.getStoragePage());
        }
    }

    @Test
    void oldQueryAndOldSessionAreRejectedAfterContextChanges() {
        try (MockedStatic<RtsClientPacketGateway> gateway = mockStatic(RtsClientPacketGateway.class)) {
            gateway.when(() -> RtsClientPacketGateway.storageSearchSourceKey(anyString()))
                    .thenReturn("");
            StorageStateManager manager = new StorageStateManager();
            long oldSession = manager.getStorageSessionId();
            manager.requestStoragePage(0);
            long oldQuery = manager.getStorageQueryId();

            manager.setStorageSearch("stone");
            long currentQuery = manager.getStorageQueryId();
            assertNotEquals(oldQuery, currentQuery);
            int beforeOldQuery = manager.getStorageRevision();
            manager.applyStoragePage(page(0, 0, oldSession, oldQuery, 4, 4), null);
            assertEquals(beforeOldQuery, manager.getStorageRevision());

            manager.applyStoragePage(page(0, 0, oldSession, currentQuery, 5, 5), null);
            assertTrue(manager.getStorageRevision() > beforeOldQuery);

            manager.clearStorageStateOnDisable();
            long newSession = manager.getStorageSessionId();
            assertNotEquals(oldSession, newSession);
            int beforeOldSession = manager.getStorageRevision();
            manager.applyStoragePage(page(0, 0, oldSession, currentQuery, 6, 6), null);
            assertEquals(beforeOldSession, manager.getStorageRevision());
        }
    }

    private static S2CRtsStoragePagePayload page(
            int page, int requestedPage, long sessionId, long queryId,
            long requestId, long serverDataRevision) {
        return page(page, requestedPage, sessionId, queryId, requestId, serverDataRevision, "stone");
    }

    private static S2CRtsStoragePagePayload page(
            int page, int requestedPage, long sessionId, long queryId,
            long requestId, long serverDataRevision, String search) {
        return new S2CRtsStoragePagePayload(
                false, "", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), page, 8, 500, false,
                search, "all", (byte) 0, false, true, true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), false,
                Collections.emptyList(), Collections.emptyList(), requestedPage, 90, 90,
                (long) requestedPage * 90L, sessionId, queryId, requestId, serverDataRevision);
    }
}
