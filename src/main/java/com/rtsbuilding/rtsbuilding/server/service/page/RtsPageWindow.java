package com.rtsbuilding.rtsbuilding.server.service.page;

/**
 * 服务端唯一的逻辑页窗口计算结果。
 *
 * <p>页大小来自服务端实际批准值，globalIndex 是本页第一条条目的全局偏移。
 * 所有乘法和加法先在 long 中完成，避免极大的条目数或页大小在归一化前溢出。</p>
 */
public record RtsPageWindow(
        int requestedPage,
        int requestedPageSize,
        int effectivePageSize,
        int safePage,
        int totalPages,
        long globalIndex,
        int fromIndex,
        int toIndex) {

    public static RtsPageWindow calculate(int requestedPage, int requestedPageSize, int totalEntries) {
        int safeTotalEntries = Math.max(0, totalEntries);
        int effectivePageSize = RtsPageSharedHelpers.sanitizePageSize(requestedPageSize);
        long totalPagesLong = Math.max(1L,
                ((long) safeTotalEntries + (long) effectivePageSize - 1L) / effectivePageSize);
        int totalPages = (int) Math.min(Integer.MAX_VALUE, totalPagesLong);
        int safePage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        long globalIndex = Math.min((long) safeTotalEntries,
                Math.max(0L, (long) safePage * (long) effectivePageSize));
        long toLong = Math.min((long) safeTotalEntries,
                globalIndex + (long) effectivePageSize);
        int fromIndex = (int) globalIndex;
        int toIndex = (int) Math.max(globalIndex, toLong);
        return new RtsPageWindow(
                requestedPage,
                requestedPageSize,
                effectivePageSize,
                safePage,
                totalPages,
                globalIndex,
                fromIndex,
                toIndex);
    }

    public int itemCount() {
        return Math.max(0, this.toIndex - this.fromIndex);
    }
}
