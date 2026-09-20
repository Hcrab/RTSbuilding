package com.rtsbuilding.rtsbuilding.server.service.page;

/** 服务端统一计算实际页窗口和 globalIndex，避免 int 页偏移溢出。 */
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
        return new RtsPageWindow(requestedPage, requestedPageSize, effectivePageSize,
                safePage, totalPages, globalIndex, fromIndex, toIndex);
    }

    public int itemCount() {
        return Math.max(0, this.toIndex - this.fromIndex);
    }
}
