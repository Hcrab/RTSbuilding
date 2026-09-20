package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端储存分页请求上下文与响应代际的唯一 owner。
 *
 * <p>这里集中维护 session/query/request 序号、每页最新请求和服务端数据修订，
 * 并在把响应交给 {@link StorageStateManager} 前完成旧代际判定。它只保留生成查询代际
 * 所需的筛选/排序值，不拥有可见物品、链接储存或任何网络发送职责；管理器仍负责把
 * 已接受响应应用到储存 UI。
 * 这样分页协议状态只有一份可变真相，重进界面或切换服务器时也能一次清理完整上下文。</p>
 *
 * <p>不变量：sessionId 在有效上下文内为正数；queryId 在查询变化后递增；requestId
 * 在一次上下文内单调递增；同一请求页只接受不早于已登记请求的响应；非负服务端修订
 * 只能单调前进。值为零的旧协议上下文字段仍按兼容语义放行。</p>
 */
final class StoragePageRequestState {
    private static final String CATEGORY_ALL = "all";
    private final int defaultPageSize;
    private int page;
    private int requestedPageSize;
    private int totalPages = 1;
    private int totalEntries;
    private int effectivePageSize;
    private long globalIndex;
    private long serverDataRevision = -1L;
    private long sessionId = newSessionId();
    private long queryId;
    private long requestSequence;
    private String queryKey = "";
    private String search = "";
    private String category = CATEGORY_ALL;
    private RtsStorageSort sort = RtsStorageSort.QUANTITY;
    private boolean sortAscending;
    private final Map<Integer, Long> latestRequestByPage = new HashMap<>();

    StoragePageRequestState(int defaultPageSize) {
        this.defaultPageSize = Math.max(1, defaultPageSize);
        this.requestedPageSize = this.defaultPageSize;
        this.effectivePageSize = this.defaultPageSize;
    }

    int page() {
        return this.page;
    }

    int requestedPageSize() {
        return this.requestedPageSize;
    }

    int totalPages() {
        return this.totalPages;
    }

    int totalEntries() {
        return this.totalEntries;
    }

    String search() {
        return this.search;
    }

    String category() {
        return this.category;
    }

    RtsStorageSort sort() {
        return this.sort;
    }

    boolean sortAscending() {
        return this.sortAscending;
    }

    int effectivePageSize() {
        return this.effectivePageSize;
    }

    long globalIndex() {
        return this.globalIndex;
    }

    long sessionId() {
        return this.sessionId;
    }

    long queryId() {
        return this.queryId;
    }

    long serverDataRevision() {
        return this.serverDataRevision;
    }

    boolean setRequestedPageSize(int pageSize) {
        if (this.requestedPageSize == pageSize) {
            return false;
        }
        this.requestedPageSize = pageSize;
        return true;
    }

    void setSearch(String search) {
        this.search = search == null ? "" : search;
    }

    boolean setCategory(String category) {
        String normalized = StorageUiValueSanitizer.normalizeCategory(category);
        if (this.category.equals(normalized)) {
            return false;
        }
        this.category = normalized;
        return true;
    }

    void cycleSort() {
        int next = (this.sort.ordinal() + 1) % RtsStorageSort.values().length;
        this.sort = RtsStorageSort.byId(next);
    }

    boolean setSort(RtsStorageSort sort) {
        if (this.sort == sort) {
            return false;
        }
        this.sort = sort;
        return true;
    }

    void toggleSortDirection() {
        this.sortAscending = !this.sortAscending;
    }

    /** 登记一次页请求，并把筛选/排序变化转换为新的查询代际。 */
    Request beginRequest(int page) {
        String normalizedQueryKey = queryKey();
        if (!normalizedQueryKey.equals(this.queryKey)) {
            this.queryId = nextPositive(this.queryId);
            this.queryKey = normalizedQueryKey;
            this.latestRequestByPage.clear();
        }
        long requestId = nextPositive(this.requestSequence);
        this.requestSequence = requestId;
        this.latestRequestByPage.put(page, requestId);
        return new Request(this.sessionId, this.queryId, requestId);
    }

    /**
     * 只接受当前会话、当前查询和当前页最新请求的响应；成功后更新协议元数据。
     */
    boolean accept(S2CRtsStoragePagePayload payload) {
        if (payload == null) {
            return false;
        }
        if (payload.sessionId() != 0L && payload.sessionId() != this.sessionId) {
            return false;
        }
        if (payload.queryId() != 0L && payload.queryId() != this.queryId) {
            return false;
        }
        if (payload.requestId() > 0L) {
            Long latest = this.latestRequestByPage.get(payload.requestedPage());
            if (latest != null && payload.requestId() < latest) {
                return false;
            }
        }
        if (payload.serverDataRevision() >= 0L
                && this.serverDataRevision >= 0L
                && payload.serverDataRevision() < this.serverDataRevision) {
            return false;
        }
        this.effectivePageSize = Math.max(1, payload.effectivePageSize() > 0
                ? payload.effectivePageSize() : payload.requestedPageSize());
        this.globalIndex = Math.max(0L, payload.globalIndex());
        this.page = payload.page();
        this.totalPages = Math.max(1, payload.totalPages());
        this.totalEntries = payload.totalEntries();
        this.search = payload.search();
        this.category = StorageUiValueSanitizer.normalizeCategory(payload.category());
        this.sort = RtsStorageSort.byId(payload.sort());
        this.sortAscending = payload.ascending();
        boolean categoryKnown = CATEGORY_ALL.equals(this.category);
        for (String category : payload.categories()) {
            if (this.category.equals(StorageUiValueSanitizer.normalizeCategory(category))) {
                categoryKnown = true;
                break;
            }
        }
        if (!categoryKnown) {
            this.category = CATEGORY_ALL;
        }
        if (payload.serverDataRevision() >= 0L) {
            this.serverDataRevision = Math.max(this.serverDataRevision, payload.serverDataRevision());
        }
        return true;
    }

    /** 清掉跨服务器/重进界面的会话、查询、请求表和响应代际。 */
    void reset() {
        resetRequestContext();
        this.page = 0;
        this.requestedPageSize = this.defaultPageSize;
        this.totalPages = 1;
        this.totalEntries = 0;
        this.effectivePageSize = this.defaultPageSize;
        this.search = "";
        this.category = CATEGORY_ALL;
        this.sort = RtsStorageSort.QUANTITY;
        this.sortAscending = false;
    }

    /** 清掉请求上下文和数据修订，但保留禁用前显示过的页、窗口、筛选和排序。 */
    void resetRequestContext() {
        this.sessionId = newSessionId();
        this.queryId = 0L;
        this.requestSequence = 0L;
        this.queryKey = "";
        this.latestRequestByPage.clear();
        this.globalIndex = 0L;
        this.serverDataRevision = -1L;
    }

    private static long newSessionId() {
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    private static long nextPositive(long value) {
        return value == Long.MAX_VALUE ? 1L : Math.max(1L, value + 1L);
    }

    private String queryKey() {
        return this.search + '\u0000' + this.category + '\u0000'
                + this.sort.ordinal() + '\u0000' + this.sortAscending + '\u0000'
                + this.requestedPageSize + '\u0000'
                + RtsClientPacketGateway.storageSearchSourceKey(this.search);
    }

    record Request(long sessionId, long queryId, long requestId) {
    }
}
