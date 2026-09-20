package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合成终端的跨服务端页面连续滚动状态。
 *
 * <p>页面大小由服务端响应决定，而不是由终端的默认请求值决定。一个视窗可能跨越任意
 * 数量的服务端页；缓存因此保留当前完整视窗、目标视窗和有限邻页，而不是固定三个页的
 * LRU。收到目标页前继续显示上一个完整视窗，避免滚动拖动过程中出现半页错位。</p>
 */
public final class CraftTerminalScrollState {
    /** 仅作为首次请求的默认值，不代表服务端实际页窗口。 */
    public static final int DEFAULT_PAGE_SIZE = 180;
    private static final int MAX_NEIGHBOR_PAGES = 2;

    private int desiredGlobalRow;
    private int displayedGlobalRow;
    private int lastRevision = -1;
    private int pendingPage = -1;
    private int effectivePageSize = DEFAULT_PAGE_SIZE;
    private long lastDataRevision = -1L;
    private String viewKey = "";
    private final Map<Integer, List<StorageEntry>> pageCache =
            new LinkedHashMap<>(8, 0.75F, true);
    private final Map<Integer, Long> pageGlobalIndices = new LinkedHashMap<>();

    public void reset(ClientRtsController controller) {
        this.pageCache.clear();
        this.pageGlobalIndices.clear();
        resetPosition();
        this.effectivePageSize = safePageSize(controller.getStorageEffectivePageSize());
        this.lastDataRevision = controller.getStorageServerDataRevision();
        this.lastRevision = controller.getStorageRevision();
        this.viewKey = viewKey(controller);
        this.pendingPage = 0;
        controller.requestStoragePage(0);
    }

    public void resetPosition() {
        this.desiredGlobalRow = 0;
        this.displayedGlobalRow = 0;
    }

    /**
     * 搜索或排序动作已经自行请求了新页；这里只丢弃旧视图缓存并登记预期响应，
     * 避免为了清缓存再重复发送一次相同请求。
     */
    public void expectFreshPage(ClientRtsController controller, int page) {
        this.pageCache.clear();
        this.pageGlobalIndices.clear();
        resetPosition();
        this.effectivePageSize = safePageSize(controller.getStorageEffectivePageSize());
        this.lastDataRevision = controller.getStorageServerDataRevision();
        this.lastRevision = controller.getStorageRevision();
        this.viewKey = viewKey(controller);
        this.pendingPage = Math.max(0, page);
    }

    public boolean scrollRows(ClientRtsController controller, int deltaRows, int visibleRows) {
        int maximum = maximumGlobalRow(controller.getStorageTotalEntries(), visibleRows);
        int next = Mth.clamp(this.desiredGlobalRow + deltaRows, 0, maximum);
        if (next == this.desiredGlobalRow) {
            return false;
        }
        this.desiredGlobalRow = next;
        int missingPage = firstMissingPage(next, visibleRows, controller.getStorageTotalEntries());
        if (missingPage < 0) {
            this.displayedGlobalRow = next;
        } else {
            requestPageIfNeeded(controller, missingPage);
        }
        return true;
    }

    public void setFromFraction(ClientRtsController controller, double fraction, int visibleRows) {
        int maximum = maximumGlobalRow(controller.getStorageTotalEntries(), visibleRows);
        int target = Mth.clamp((int) Math.round(Mth.clamp(fraction, 0.0D, 1.0D) * maximum), 0, maximum);
        int delta = target - this.desiredGlobalRow;
        if (delta != 0) {
            scrollRows(controller, delta, visibleRows);
        }
    }

    public void update(ClientRtsController controller, int visibleRows) {
        int maximum = maximumGlobalRow(controller.getStorageTotalEntries(), visibleRows);
        this.desiredGlobalRow = Mth.clamp(this.desiredGlobalRow, 0, maximum);
        int revision = controller.getStorageRevision();
        String currentViewKey = viewKey(controller);
        boolean viewChanged = !currentViewKey.equals(this.viewKey);
        int observedPageSize = safePageSize(controller.getStorageEffectivePageSize());
        boolean pageSizeChanged = observedPageSize != this.effectivePageSize;
        boolean dataChanged = controller.getStorageServerDataRevision() != this.lastDataRevision;
        if (viewChanged || pageSizeChanged || dataChanged) {
            // 查询代际或服务端窗口变化时，旧页的全局索引不再可信。
            this.pageCache.clear();
            this.pageGlobalIndices.clear();
            if (viewChanged) {
                resetPosition();
            }
            this.pendingPage = -1;
            this.viewKey = currentViewKey;
            this.effectivePageSize = observedPageSize;
            this.lastDataRevision = controller.getStorageServerDataRevision();
        }

        if (revision != this.lastRevision) {
            // 同一查询内的相邻页响应可能乱序；StorageStateManager 已经按页请求号和
            // 查询代际拒绝旧响应，这里应保留仍属于当前视窗的页，不能因一张迟到邻页
            // 把刚收齐的目标尾页全部驱逐。
            this.pageCache.put(controller.getStoragePage(),
                    List.copyOf(controller.getStorageEntries()));
            this.pageGlobalIndices.put(controller.getStoragePage(),
                    Math.max(0L, controller.getStorageGlobalIndex()));
            this.lastRevision = revision;
            this.pendingPage = -1;
        }

        pruneCache(controller, visibleRows);
        int missingPage = firstMissingPage(
                this.desiredGlobalRow, visibleRows, controller.getStorageTotalEntries());
        if (missingPage < 0) {
            this.displayedGlobalRow = this.desiredGlobalRow;
        } else {
            requestPageIfNeeded(controller, missingPage);
        }
    }

    /** 返回当前完整视窗中某个格子的服务端快照；尚未收到所需页时返回 {@code null}。 */
    public StorageEntry entryAtVisibleCell(int visibleCell) {
        if (visibleCell < 0) {
            return null;
        }
        long globalIndex = (long) this.displayedGlobalRow * CraftTerminalLayout.COLUMNS + visibleCell;
        int pageSize = safePageSize(this.effectivePageSize);
        int page = (int) (globalIndex / pageSize);
        List<StorageEntry> entries = this.pageCache.get(page);
        if (entries == null) {
            return null;
        }
        long pageStart = this.pageGlobalIndices.getOrDefault(page, (long) page * pageSize);
        long localIndex = globalIndex - pageStart;
        return localIndex >= 0L && localIndex < entries.size()
                ? entries.get((int) localIndex) : null;
    }

    public double fraction(int totalEntries, int visibleRows) {
        int maximum = maximumGlobalRow(totalEntries, visibleRows);
        return maximum <= 0 ? 0.0D : (double) this.desiredGlobalRow / (double) maximum;
    }

    public double thumbFraction(int totalEntries, int visibleRows) {
        long totalRows = Math.max(1L, ((long) Math.max(0, totalEntries)
                + CraftTerminalLayout.COLUMNS - 1L) / CraftTerminalLayout.COLUMNS);
        return Math.min(1.0D, (double) Math.max(1, visibleRows) / (double) totalRows);
    }

    private void requestPageIfNeeded(ClientRtsController controller, int page) {
        if (page < 0 || page == this.pendingPage) {
            return;
        }
        this.pendingPage = page;
        controller.requestStoragePage(page);
    }

    private int firstMissingPage(int globalRow, int visibleRows, int totalEntries) {
        for (int page : requiredPagesForWindow(globalRow, visibleRows, totalEntries, this.effectivePageSize)) {
            if (!this.pageCache.containsKey(page)) {
                return page;
            }
        }
        return -1;
    }

    /**
     * 保留当前显示视窗和目标视窗的所有页，并只对两侧做有限预取。
     * 小页时目标窗口可以天然超过三个页，不能用固定数量的 LRU 驱逐必需页。
     */
    private void pruneCache(ClientRtsController controller, int visibleRows) {
        Set<Integer> keep = new HashSet<>();
        addWindowAndNeighbors(keep, this.displayedGlobalRow, visibleRows,
                controller.getStorageTotalEntries());
        addWindowAndNeighbors(keep, this.desiredGlobalRow, visibleRows,
                controller.getStorageTotalEntries());
        this.pageCache.keySet().removeIf(page -> {
            if (keep.contains(page)) {
                return false;
            }
            this.pageGlobalIndices.remove(page);
            return true;
        });
    }

    private void addWindowAndNeighbors(Set<Integer> keep, int row, int visibleRows, int totalEntries) {
        int[] required = requiredPagesForWindow(row, visibleRows, totalEntries, this.effectivePageSize);
        for (int page : required) {
            for (int neighbor = page - MAX_NEIGHBOR_PAGES; neighbor <= page + MAX_NEIGHBOR_PAGES; neighbor++) {
                if (neighbor >= 0) {
                    keep.add(neighbor);
                }
            }
        }
    }

    /** 兼容旧的纯计算调用，默认值只用于测试/初始化，不参与实际客户端索引。 */
    static int[] requiredPagesForWindow(int globalRow, int visibleRows, int totalEntries) {
        return requiredPagesForWindow(globalRow, visibleRows, totalEntries, DEFAULT_PAGE_SIZE);
    }

    /** 纯计算接口，同时作为动态服务端页大小的跨页回归测试落点。 */
    static int[] requiredPagesForWindow(int globalRow, int visibleRows, int totalEntries, int pageSize) {
        if (totalEntries <= 0) {
            return new int[0];
        }
        int safePageSize = safePageSize(pageSize);
        long firstIndex = Math.max(0L, (long) globalRow) * CraftTerminalLayout.COLUMNS;
        long lastIndex = Math.min((long) totalEntries - 1L,
                ((long) Math.max(0, globalRow) + Math.max(1, visibleRows))
                        * CraftTerminalLayout.COLUMNS - 1L);
        int firstPage = (int) (firstIndex / safePageSize);
        int lastPage = Math.max(firstPage, (int) (lastIndex / safePageSize));
        List<Integer> pages = new ArrayList<>(lastPage - firstPage + 1);
        for (int page = firstPage; page <= lastPage; page++) {
            pages.add(page);
        }
        return pages.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String viewKey(ClientRtsController controller) {
        return controller.getStorageSessionId() + "\u0000"
                + controller.getStorageQueryId() + "\u0000"
                + controller.getStorageSearch() + '\u0000'
                + controller.getStorageCategory() + '\u0000'
                + controller.getStorageSort().name() + '\u0000'
                + controller.isStorageSortAscending() + '\u0000'
                + controller.getStoragePageSize();
    }

    private static int maximumGlobalRow(int totalEntries, int visibleRows) {
        long totalRows = ((long) Math.max(0, totalEntries) + CraftTerminalLayout.COLUMNS - 1L)
                / CraftTerminalLayout.COLUMNS;
        return (int) Math.max(0L, totalRows - Math.max(1, visibleRows));
    }

    private static int safePageSize(int pageSize) {
        return Math.max(1, pageSize);
    }
}
