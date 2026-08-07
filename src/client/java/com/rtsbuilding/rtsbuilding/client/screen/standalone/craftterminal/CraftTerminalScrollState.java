package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * 合成终端的跨服务端页面连续滚动状态。
 *
 * <p>服务端仍按有界页面返回数据，本类把 180 条页面解释为每页 20 行，并保存一个 全局行号。一个六行视窗可能同时跨越两页，因此这里最多缓存三个服务端已确认的
 * 只读页面；跨界时请求缺少的相邻页，新页到达前保留上一个完整视窗。它不生成物品、 不修改物品，也不发送除页面请求之外的任何业务动作。
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

  public void reset(ClientRtsController controller) {
    this.pageCache.clear();
    resetPosition();
    this.lastRevision = controller.getStorageRevision();
    this.viewKey = viewKey(controller);
    this.pendingPage = 0;
    controller.requestStoragePage(0);
  }

  public void resetPosition() {
    this.desiredGlobalRow = 0;
    this.displayedGlobalRow = 0;
  }

  /** 搜索或排序动作已经自行请求了新页；这里只丢弃旧视图缓存并登记预期响应， 避免为了清缓存再重复发送一次相同请求。 */
  public void expectFreshPage(ClientRtsController controller, int page) {
    this.pageCache.clear();
    resetPosition();
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
    if (revision != this.lastRevision) {
      this.lastRevision = revision;
      String currentViewKey = viewKey(controller);
      boolean expectedAdjacentPage =
          this.pendingPage == controller.getStoragePage() && currentViewKey.equals(this.viewKey);
      if (!expectedAdjacentPage) {
        this.pageCache.clear();
      }
      this.viewKey = currentViewKey;
      this.pendingPage = -1;
      this.pageCache.put(controller.getStoragePage(), List.copyOf(controller.getStorageEntries()));
    }

    int missingPage =
        firstMissingPage(this.desiredGlobalRow, visibleRows, controller.getStorageTotalEntries());
    if (missingPage < 0) {
      this.displayedGlobalRow = this.desiredGlobalRow;
    } else {
      requestPageIfNeeded(controller, missingPage);
    }
  }

  /** 返回当前完整视窗中某个格子的服务端快照；尚未收到所需页时返回 {@code null}。 */
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

  public double fraction(int totalEntries, int visibleRows) {
    int maximum = maximumGlobalRow(totalEntries, visibleRows);
    return maximum <= 0 ? 0.0D : (double) this.desiredGlobalRow / (double) maximum;
  }

  public double thumbFraction(int totalEntries, int visibleRows) {
    int totalRows =
        Math.max(1, (totalEntries + CraftTerminalLayout.COLUMNS - 1) / CraftTerminalLayout.COLUMNS);
    return Math.min(1.0D, (double) visibleRows / (double) totalRows);
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

  /** 纯计算接口，同时作为跨页窗口的回归测试落点。 */
  static int[] requiredPagesForWindow(int globalRow, int visibleRows, int totalEntries) {
    if (totalEntries <= 0) {
      return new int[0];
    }
    int firstIndex = Math.max(0, globalRow) * CraftTerminalLayout.COLUMNS;
    int lastIndex =
        Math.min(
            totalEntries - 1,
            (Math.max(0, globalRow) + Math.max(1, visibleRows)) * CraftTerminalLayout.COLUMNS - 1);
    int firstPage = firstIndex / PAGE_SIZE;
    int lastPage = Math.max(firstPage, lastIndex / PAGE_SIZE);
    List<Integer> pages = new ArrayList<>(lastPage - firstPage + 1);
    for (int page = firstPage; page <= lastPage; page++) {
      pages.add(page);
    }
    return pages.stream().mapToInt(Integer::intValue).toArray();
  }

  private static String viewKey(ClientRtsController controller) {
    return controller.getStorageSearch()
        + '\u0000'
        + controller.getStorageCategory()
        + '\u0000'
        + controller.getStorageSort().name()
        + '\u0000'
        + controller.isStorageSortAscending()
        + '\u0000'
        + controller.getStoragePageSize();
  }

  private static int maximumGlobalRow(int totalEntries, int visibleRows) {
    int totalRows =
        (Math.max(0, totalEntries) + CraftTerminalLayout.COLUMNS - 1) / CraftTerminalLayout.COLUMNS;
    return Math.max(0, totalRows - Math.max(1, visibleRows));
  }
}
