package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Craft Terminal 的跨页浏览状态。
 *
 * <p>服务端仍是储存页面和排序的唯一权威；本类只缓存已经收到的页面并在视窗跨页时请求
 * 缺失页。它不会创建、删除或移动物品，因此终端、底栏与容器 overlay 可以继续消费同一条
 * {@link ClientRtsController} 储存状态链。</p>
 */
public final class CraftTerminalScrollState {
    public static final int PAGE_SIZE = 180;
    private static final int MAX_CACHED_PAGES = 3;

    private int desiredGlobalRow;
    private int displayedGlobalRow;
    private int lastRevision = -1;
    private int pendingPage = -1;
    private String viewKey = "";
    private final Map<Integer, List<StorageEntry>> pageCache =
            new LinkedHashMap<>(4, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<StorageEntry>> eldest) {
                    return size() > MAX_CACHED_PAGES;
                }
            };

    /** 切换到终端视图时从当前共享筛选、排序状态重新请求第一页。 */
    public void reset(ClientRtsController controller) {
        this.pageCache.clear();
        resetPosition();
        this.lastRevision = controller.getStorageRevision();
        this.viewKey = viewKey(controller);
        this.pendingPage = 0;
        controller.requestStoragePage(0);
    }

    /** 排序或搜索已自行发出刷新请求时，丢弃旧页面并等待该请求返回。 */
    public void expectFreshPage(ClientRtsController controller, int page) {
        this.pageCache.clear();
        resetPosition();
        this.lastRevision = controller.getStorageRevision();
        this.viewKey = viewKey(controller);
        this.pendingPage = Math.max(0, page);
    }

    /** 按行滚动；跨越服务端页面边界时仅请求缺失页。 */
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

    /** 将滚动条比例转换为共享储存页面中的目标行。 */
    public void setFromFraction(ClientRtsController controller, double fraction, int visibleRows) {
        int maximum = maximumGlobalRow(controller.getStorageTotalEntries(), visibleRows);
        int target = Mth.clamp((int) Math.round(Mth.clamp(fraction, 0.0D, 1.0D) * maximum), 0, maximum);
        int delta = target - this.desiredGlobalRow;
        if (delta != 0) {
            scrollRows(controller, delta, visibleRows);
        }
    }

    /** 接收服务端页面后更新缓存，并确保当前可视窗口完整。 */
    public void update(ClientRtsController controller, int visibleRows) {
        int maximum = maximumGlobalRow(controller.getStorageTotalEntries(), visibleRows);
        this.desiredGlobalRow = Mth.clamp(this.desiredGlobalRow, 0, maximum);
        int revision = controller.getStorageRevision();
        if (revision != this.lastRevision) {
            this.lastRevision = revision;
            String currentViewKey = viewKey(controller);
            boolean expectedAdjacentPage = this.pendingPage == controller.getStoragePage()
                    && currentViewKey.equals(this.viewKey);
            if (!expectedAdjacentPage) {
                this.pageCache.clear();
            }
            this.viewKey = currentViewKey;
            this.pendingPage = -1;
            this.pageCache.put(controller.getStoragePage(), List.copyOf(controller.getStorageEntries()));
        }

        int missingPage = firstMissingPage(
                this.desiredGlobalRow, visibleRows, controller.getStorageTotalEntries());
        if (missingPage < 0) {
            this.displayedGlobalRow = this.desiredGlobalRow;
        } else {
            requestPageIfNeeded(controller, missingPage);
        }
    }

    /** 返回当前完整可视窗口中某一格对应的服务端页面快照。 */
    public StorageEntry entryAtVisibleCell(int visibleCell) {
        int globalIndex = this.displayedGlobalRow * CraftTerminalLayout.COLUMNS + visibleCell;
        if (globalIndex < 0) {
            return null;
        }
        int page = globalIndex / PAGE_SIZE;
        int localIndex = globalIndex % PAGE_SIZE;
        List<StorageEntry> entries = this.pageCache.get(page);
        return entries != null && localIndex < entries.size() ? entries.get(localIndex) : null;
    }

    /** 供终端皮肤的滚动块表示当前位置。 */
    public double fraction(int totalEntries, int visibleRows) {
        int maximum = maximumGlobalRow(totalEntries, visibleRows);
        return maximum <= 0 ? 0.0D : (double) this.desiredGlobalRow / (double) maximum;
    }

    private void resetPosition() {
        this.desiredGlobalRow = 0;
        this.displayedGlobalRow = 0;
    }

    private void requestPageIfNeeded(ClientRtsController controller, int page) {
        if (page < 0 || page == this.pendingPage) {
            return;
        }
        this.pendingPage = page;
        controller.requestStoragePage(page);
    }

    private int firstMissingPage(int globalRow, int visibleRows, int totalEntries) {
        for (int page : requiredPagesForWindow(globalRow, visibleRows, totalEntries)) {
            if (!this.pageCache.containsKey(page)) {
                return page;
            }
        }
        return -1;
    }

    private static int[] requiredPagesForWindow(int globalRow, int visibleRows, int totalEntries) {
        if (totalEntries <= 0) {
            return new int[0];
        }
        int firstIndex = Math.max(0, globalRow) * CraftTerminalLayout.COLUMNS;
        int lastIndex = Math.min(totalEntries - 1,
                (Math.max(0, globalRow) + Math.max(1, visibleRows))
                        * CraftTerminalLayout.COLUMNS - 1);
        int firstPage = firstIndex / PAGE_SIZE;
        int lastPage = Math.max(firstPage, lastIndex / PAGE_SIZE);
        List<Integer> pages = new ArrayList<>(lastPage - firstPage + 1);
        for (int page = firstPage; page <= lastPage; page++) {
            pages.add(page);
        }
        return pages.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String viewKey(ClientRtsController controller) {
        return controller.getStorageSearch() + '\u0000'
                + controller.getStorageCategory() + '\u0000'
                + controller.getStorageSort().name() + '\u0000'
                + controller.isStorageSortAscending() + '\u0000'
                + controller.getStoragePageSize();
    }

    private static int maximumGlobalRow(int totalEntries, int visibleRows) {
        int totalRows = (Math.max(0, totalEntries) + CraftTerminalLayout.COLUMNS - 1)
                / CraftTerminalLayout.COLUMNS;
        return Math.max(0, totalRows - Math.max(1, visibleRows));
    }
}
