package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CraftTerminalScrollState 的真实缓存/请求行为，不启动客户端或注册表。 */
final class CraftTerminalScrollStateBehaviorTest {
    private static final int VISIBLE_ROWS = 6;

    @Test
    void everySupportedEffectivePageSizeCompletesAWindowAndExercisesSmallPageFanout() {
        for (int pageSize : new int[]{1, 17, 90, 180, 256}) {
            FakeStorageController source = new FakeStorageController(pageSize);
            CraftTerminalScrollState state = new CraftTerminalScrollState();
            driveWindow(state, source);

            for (int cell = 0; cell < VISIBLE_ROWS * CraftTerminalLayout.COLUMNS; cell++) {
                assertNotNull(state.entryAtVisibleCell(cell),
                        "pageSize=" + pageSize + " cell=" + cell);
            }
            if (pageSize <= 17) {
                assertTrue(source.deliveredPages.size() > 3,
                        "small pages must exercise a multi-page window");
            } else {
                assertTrue(source.deliveredPages.size() >= 1);
            }
            int requestsAtStable = source.requestedPages.size();
            state.update(source.controller, VISIBLE_ROWS);
            state.update(source.controller, VISIBLE_ROWS);
            assertEquals(requestsAtStable, source.requestedPages.size(),
                    "stable window must not request pages repeatedly");
        }
    }

    @Test
    void fastDragToTailKeepsTheTailAfterAnOlderInFlightPageArrivesLate() {
        FakeStorageController source = new FakeStorageController(17);
        CraftTerminalScrollState state = new CraftTerminalScrollState();
        state.reset(source.controller);
        int first = source.pollRequestedPage();
        source.deliver(first);
        state.update(source.controller, VISIBLE_ROWS);
        int oldInFlight = source.pollRequestedPage();

        state.setFromFraction(source.controller, 1.0D, VISIBLE_ROWS);
        int tailPage = source.requestedPages.get(source.requestedPages.size() - 1);
        source.deliver(tailPage);
        state.update(source.controller, VISIBLE_ROWS);
        source.deliver(oldInFlight);
        state.update(source.controller, VISIBLE_ROWS);

        driveWindow(state, source);
        int lastCell = VISIBLE_ROWS * CraftTerminalLayout.COLUMNS - 1;
        StorageEntry tail = state.entryAtVisibleCell(lastCell);
        assertNotNull(tail);
        assertTrue(tail.itemId().startsWith("item-" + (source.totalEntries - 1)));
        int requestsAtStable = source.requestedPages.size();
        state.update(source.controller, VISIBLE_ROWS);
        assertEquals(requestsAtStable, source.requestedPages.size());
    }

    @Test
    void queryPageSizeRevisionAndReconnectClearOnlyTheInvalidContext() {
        FakeStorageController source = new FakeStorageController(90);
        CraftTerminalScrollState state = new CraftTerminalScrollState();
        state.reset(source.controller);
        source.deliver(source.pollRequestedPage());
        state.update(source.controller, VISIBLE_ROWS);
        assertEquals("item-0", state.entryAtVisibleCell(0).itemId());

        source.queryId = 2L;
        source.serverDataRevision = 2L;
        source.globalOffset = 1000;
        source.revision++;
        source.deliver(0);
        state.update(source.controller, VISIBLE_ROWS);
        assertEquals("item-1000", state.entryAtVisibleCell(0).itemId());

        source.effectivePageSize = 17;
        source.pageSize = 17;
        source.globalOffset = 2000;
        source.revision++;
        source.deliver(0);
        state.update(source.controller, VISIBLE_ROWS);
        assertEquals("item-2000", state.entryAtVisibleCell(0).itemId());

        state.reset(source.controller);
        assertEquals(0, source.requestedPages.get(source.requestedPages.size() - 1));
        assertEquals(0, state.entryAtVisibleCell(0) == null ? 0 : 1,
                "reconnect reset must not expose the previous page before its new response");
    }

    private static void driveWindow(CraftTerminalScrollState state, FakeStorageController source) {
        if (source.requestedPages.isEmpty()) {
            state.reset(source.controller);
        }
        int guard = 5000;
        while (state.entryAtVisibleCell(VISIBLE_ROWS * CraftTerminalLayout.COLUMNS - 1) == null
                && guard-- > 0) {
            if (source.pendingRequests.isEmpty()) {
                state.update(source.controller, VISIBLE_ROWS);
                continue;
            }
            source.deliver(source.pollRequestedPage());
            state.update(source.controller, VISIBLE_ROWS);
        }
        assertTrue(guard > 0, "the required page window did not converge");
    }

    private static final class FakeStorageController {
        final ClientRtsController controller = mock(ClientRtsController.class);
        final Deque<Integer> pendingRequests = new ArrayDeque<>();
        final List<Integer> requestedPages = new ArrayList<>();
        final Set<Integer> deliveredPages = new HashSet<>();
        int pageSize;
        int effectivePageSize;
        final int totalEntries;
        int page;
        int revision;
        long sessionId = 1L;
        long queryId = 1L;
        long serverDataRevision = 1L;
        long globalIndex;
        int globalOffset;
        List<StorageEntry> entries = Collections.emptyList();

        FakeStorageController(int pageSize) {
            this.pageSize = pageSize;
            this.effectivePageSize = pageSize;
            this.totalEntries = Math.max(270, pageSize * 5 + 3);
            when(controller.getStorageEffectivePageSize()).thenAnswer(i -> effectivePageSize);
            when(controller.getStoragePageSize()).thenAnswer(i -> pageSize);
            when(controller.getStorageTotalEntries()).thenAnswer(i -> totalEntries);
            when(controller.getStoragePage()).thenAnswer(i -> page);
            when(controller.getStorageRevision()).thenAnswer(i -> revision);
            when(controller.getStorageSessionId()).thenAnswer(i -> sessionId);
            when(controller.getStorageQueryId()).thenAnswer(i -> queryId);
            when(controller.getStorageServerDataRevision()).thenAnswer(i -> serverDataRevision);
            when(controller.getStorageGlobalIndex()).thenAnswer(i -> globalIndex);
            when(controller.getStorageEntries()).thenAnswer(i -> entries);
            when(controller.getStorageSearch()).thenReturn("");
            when(controller.getStorageCategory()).thenReturn("all");
            when(controller.getStorageSort()).thenReturn(RtsStorageSort.QUANTITY);
            when(controller.isStorageSortAscending()).thenReturn(false);
            doAnswer(invocation -> {
                int requested = invocation.getArgument(0);
                requestedPages.add(requested);
                pendingRequests.addLast(requested);
                return null;
            }).when(controller).requestStoragePage(anyInt());
        }

        int pollRequestedPage() {
            Integer requested = pendingRequests.pollFirst();
            assertNotNull(requested);
            return requested;
        }

        void deliver(int requestedPage) {
            this.page = Math.max(0, requestedPage);
            this.globalIndex = (long) this.page * this.effectivePageSize;
            int first = (int) Math.min(Integer.MAX_VALUE, this.globalIndex);
            int size = Math.min(this.effectivePageSize, this.totalEntries - first);
            List<StorageEntry> next = new ArrayList<>();
            for (int index = 0; index < Math.max(0, size); index++) {
                int global = globalOffset + first + index;
                next.add(new StorageEntry(null, "item-" + global, 1L, "test", "Item " + global));
            }
            this.entries = next;
            this.revision++;
            this.deliveredPages.add(this.page);
        }
    }
}
