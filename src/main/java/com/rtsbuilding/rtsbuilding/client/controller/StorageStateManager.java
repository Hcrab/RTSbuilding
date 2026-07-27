package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.*;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.*;

/**
 * Manages RTS storage, crafting, funnel, quick-slot, and GUI-binding state on the client side.
 * Extracted from {@link ClientRtsController} to reduce its size.
 *
 * <p>Holds all storage-related fields and provides methods for querying and updating
 * storage pages, craftables, feedback, funnel, quick slots, and GUI bindings.
 */
public final class StorageStateManager {

    // =========================================================================
    //  Constants
    // =========================================================================

    public static final int QUICK_SLOT_COUNT = StorageBindingState.QUICK_SLOT_COUNT;
    public static final int GUI_BINDING_SLOT_COUNT = StorageBindingState.GUI_BINDING_SLOT_COUNT;
    private static final int DEFAULT_STORAGE_PAGE_SIZE = 90;
    private static final int MAX_STORAGE_PAGE_SIZE = 180;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    // =========================================================================
    //  Storage page fields
    // =========================================================================

    private boolean storageCollapsed;
    private boolean storageLinked;
    private boolean bdNetworkEnabled = true;
    private String linkedStorageName = "No Storage";
    private final List<BlockPos> linkedStoragePositions = new ArrayList<>();
    private final List<LinkedStorageEntry> linkedStorageEntries = new ArrayList<>();
    private int storagePage;
    private int storagePageSize = DEFAULT_STORAGE_PAGE_SIZE;
    private int storageTotalPages = 1;
    private int storageTotalEntries;
    private int storageRevision;
    private String storageSearch = "";
    private String storageCategory = CATEGORY_ALL;
    private RtsStorageSort storageSort = RtsStorageSort.QUANTITY;
    private boolean storageSortAscending;
    private final List<String> storageCategories = new ArrayList<>();
    private final List<StorageEntry> storageEntries = new ArrayList<>();
    private final Map<String, Long> storageTotalCounts = new HashMap<>();
    private final List<FluidEntry> fluidEntries = new ArrayList<>();
    private final List<RecentEntry> recentEntries = new ArrayList<>();
    private final StorageRefreshState refreshState = new StorageRefreshState();

    private final StorageCraftState craftState = new StorageCraftState();

    // =========================================================================
    //  Funnel fields
    // =========================================================================

    private boolean funnelEnabled;
    private final List<FunnelBufferEntry> funnelBufferEntries = new ArrayList<>();

    private final StorageBindingState bindingState = new StorageBindingState();

    // =========================================================================
    //  Other storage-related fields
    // =========================================================================

    private boolean autoStoreMinedDrops = true;
    private double storagePanelXNormalized;
    private double storagePanelYNormalized;
    private double storagePanelWidthNormalized;
    private double storagePanelHeightNormalized;

    // =========================================================================
    //  Initialization
    // =========================================================================

    /** Package-private constructor; called by {@link ClientRtsController}. */
    StorageStateManager() {
        this.storagePanelXNormalized = 0.5D;
        this.storagePanelYNormalized = 1.0D;
        this.storagePanelWidthNormalized = 0.92D;
        this.storagePanelHeightNormalized = 0.24D;
        this.storageCategories.add(CATEGORY_ALL);
    }

    // =========================================================================
    //  Public getters — storage page
    // =========================================================================

    public boolean isStorageCollapsed() {
        return this.storageCollapsed;
    }

    public void toggleStorageCollapsed() {
        this.storageCollapsed = !this.storageCollapsed;
    }

    public double getStoragePanelXNormalized() {
        return this.storagePanelXNormalized;
    }

    public double getStoragePanelYNormalized() {
        return this.storagePanelYNormalized;
    }

    public double getStoragePanelWidthNormalized() {
        return this.storagePanelWidthNormalized;
    }

    public double getStoragePanelHeightNormalized() {
        return this.storagePanelHeightNormalized;
    }

    public void updateStoragePanelLayout(double xNormalized, double yNormalized, double widthNormalized, double heightNormalized) {
        this.storagePanelXNormalized = clampLayoutNormalized(xNormalized);
        this.storagePanelYNormalized = clampLayoutNormalized(yNormalized);
        this.storagePanelWidthNormalized = clampLayoutNormalized(widthNormalized);
        this.storagePanelHeightNormalized = clampLayoutNormalized(heightNormalized);
    }

    public boolean isStorageLinked() {
        return this.storageLinked;
    }

    public String getLinkedStorageName() {
        return this.linkedStorageName;
    }

    public List<BlockPos> getLinkedStoragePositions() {
        return Collections.unmodifiableList(this.linkedStoragePositions);
    }

    public List<LinkedStorageEntry> getLinkedStorageEntries() {
        return Collections.unmodifiableList(this.linkedStorageEntries);
    }

    public int getStoragePage() {
        return this.storagePage;
    }

    public int getStoragePageSize() {
        return this.storagePageSize;
    }

    public int getStorageTotalPages() {
        return this.storageTotalPages;
    }

    public int getStorageTotalEntries() {
        return this.storageTotalEntries;
    }

    public int getStorageRevision() {
        return this.storageRevision;
    }

    public String getStorageSearch() {
        return this.storageSearch;
    }

    public String getStorageCategory() {
        return this.storageCategory;
    }

    public RtsStorageSort getStorageSort() {
        return this.storageSort;
    }

    public boolean isStorageSortAscending() {
        return this.storageSortAscending;
    }

    public List<String> getStorageCategories() {
        return Collections.unmodifiableList(this.storageCategories);
    }

    public List<StorageEntry> getStorageEntries() {
        return Collections.unmodifiableList(this.storageEntries);
    }

    public long getStorageTotalCount(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return 0L;
        }
        return Math.max(0L, this.storageTotalCounts.getOrDefault(itemId, 0L));
    }

    public List<FluidEntry> getFluidEntries() {
        return Collections.unmodifiableList(this.fluidEntries);
    }

    public List<RecentEntry> getRecentEntries() {
        return Collections.unmodifiableList(this.recentEntries);
    }

    public long getRecentDisplayAmount(RecentEntry entry) {
        if (entry == null) {
            return 0L;
        }
        if (entry.fluid()) {
            return getStorageFluidAmount(entry.id());
        }
        return getStorageTotalCount(entry.id());
    }

    // =========================================================================
    //  Public getters — scan / dirty
    // =========================================================================

    public boolean isStorageScanRunning() {
        return this.refreshState.scanRunning();
    }

    public boolean isStorageViewDirty() {
        return this.refreshState.viewDirty();
    }

    public boolean shouldHighlightStorageRefresh() {
        return this.refreshState.viewDirty();
    }

    public boolean hasStoragePageSnapshot() {
        return this.refreshState.hasPageSnapshot(this.storageRevision);
    }

    public boolean hasAnyStorageContent() {
        return this.storageLinked
                || !this.linkedStoragePositions.isEmpty()
                || !this.storageEntries.isEmpty()
                || !this.fluidEntries.isEmpty();
    }

    public float getStorageScanProgress() {
        return this.refreshState.scanProgress();
    }

    // =========================================================================
    //  Public getters — BD network
    // =========================================================================

    public boolean isBdNetworkEnabled() {
        return this.bdNetworkEnabled;
    }

    public void setBdNetworkEnabled(boolean enabled) {
        this.bdNetworkEnabled = enabled;
        RtsClientPacketGateway.sendSetBdNetwork(enabled);
    }

    public void toggleBdNetworkEnabled() {
        setBdNetworkEnabled(!this.bdNetworkEnabled);
    }

    // =========================================================================
    //  Public getters — auto-store / funnel
    // =========================================================================

    public boolean isAutoStoreMinedDrops() {
        return this.autoStoreMinedDrops;
    }

    public void setAutoStoreMinedDrops(boolean enabled) {
        this.autoStoreMinedDrops = enabled;
        RtsClientPacketGateway.sendSetAutoStoreMinedDrops(enabled);
    }

    public void toggleAutoStoreMinedDrops() {
        setAutoStoreMinedDrops(!this.autoStoreMinedDrops);
    }

    public boolean isFunnelEnabled() {
        return this.funnelEnabled;
    }

    public List<FunnelBufferEntry> getFunnelBufferEntries() {
        return Collections.unmodifiableList(this.funnelBufferEntries);
    }

    // =========================================================================
    //  Public getters — craft
    // =========================================================================

    public String getCraftablesSearch() {
        return this.craftState.search();
    }

    public boolean isCraftablesShowUnavailable() {
        return this.craftState.showUnavailable();
    }

    public List<CraftableEntry> getCraftableEntries() {
        return this.craftState.entries();
    }

    public int getCraftablesRevision() {
        return this.craftState.revision();
    }

    public boolean hasMoreCraftables() {
        return this.craftState.hasMore();
    }

    public String getCraftFeedbackItemId() {
        return this.craftState.feedbackItemId();
    }

    public int getCraftFeedbackCount() {
        return this.craftState.feedbackCount();
    }

    public long getCraftFeedbackExpiryMs() {
        return this.craftState.feedbackExpiryMs();
    }

    public List<CraftFeedbackIngredient> getCraftFeedbackIngredients() {
        return this.craftState.feedbackIngredients();
    }

    // =========================================================================
    //  Public getters — quick slot / GUI binding
    // =========================================================================

    public int getQuickSlotCount() {
        return QUICK_SLOT_COUNT;
    }

    public String getQuickSlotItemId(int index) {
        return this.bindingState.quickItemId(index);
    }

    public String getQuickSlotLabel(int index) {
        return this.bindingState.quickLabel(index);
    }

    public ItemStack getQuickSlotPreview(int index) {
        return this.bindingState.quickPreview(index);
    }

    public int getGuiBindingCount() {
        return GUI_BINDING_SLOT_COUNT;
    }

    public String getGuiBindingLabel(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return "";
        }
        ItemStack preview = this.bindingState.bindingPreview(index);
        if (preview != null && !preview.isEmpty()) {
            // 服务端保存的 label 可能已经按服务端语言展开。优先用客户端
            // 物品预览重新解析名称，让 AE 线缆、机器等绑定跟随玩家的当前语言。
            return preview.getDisplayName();
        }
        return this.bindingState.bindingLabel(index);
    }

    public ItemStack getGuiBindingPreview(int index) {
        return this.bindingState.bindingPreview(index);
    }

    public boolean hasGuiBinding(int index) {
        return this.bindingState.hasBinding(index);
    }

    // =========================================================================
    //  Public actions — storage page
    // =========================================================================

    public void requestStoragePage(int page) {
        markStorageScanStarted();
        RtsClientPacketGateway.sendRequestStoragePage(
                page,
                this.storageSearch,
                this.storageCategory,
                this.storageSort,
                this.storageSortAscending,
                this.storagePageSize);
    }

    public void updateStoragePageSize(int pageSize) {
        int safePageSize = MathHelper.clamp(pageSize, 1, MAX_STORAGE_PAGE_SIZE);
        if (this.storagePageSize == safePageSize) {
            return;
        }
        this.storagePageSize = safePageSize;
        if (hasStoragePageSnapshot() && !this.refreshState.scanRunning()) {
            requestStoragePage(this.storagePage);
        }
    }

    public void requestStoragePageIfNoSnapshot(int page) {
        if (!hasStoragePageSnapshot() && !this.refreshState.scanRunning()) {
            requestStoragePage(page);
        }
    }

    public void refreshStoragePage() {
        requestStoragePage(this.storagePage);
    }

    public void setStorageSearch(String search) {
        this.storageSearch = search == null ? "" : search;
        requestStoragePage(0);
    }

    public void setStorageCategory(String category) {
        String normalized = normalizeCategory(category);
        if (this.storageCategory.equals(normalized)) {
            return;
        }
        this.storageCategory = normalized;
        requestStoragePage(0);
    }

    public void cycleSort() {
        int next = (this.storageSort.ordinal() + 1) % RtsStorageSort.values().length;
        this.storageSort = RtsStorageSort.byId(next);
        requestStoragePage(0);
    }

    public void toggleSortDirection() {
        this.storageSortAscending = !this.storageSortAscending;
        requestStoragePage(0);
    }

    public void prevPage() {
        requestStoragePage(Math.max(0, this.storagePage - 1));
    }

    public void nextPage() {
        requestStoragePage(Math.min(this.storageTotalPages - 1, this.storagePage + 1));
    }

    // =========================================================================
    //  Public actions — craft
    // =========================================================================

    public void setCraftablesSearch(String search) {
        this.craftState.setSearch(search);
    }

    public void setCraftablesShowUnavailable(boolean showUnavailable) {
        this.craftState.setShowUnavailable(showUnavailable);
    }

    public void toggleCraftablesShowUnavailable() {
        setCraftablesShowUnavailable(!this.craftState.showUnavailable());
    }

    public void requestCraftables() {
        this.craftState.requestFirstPage();
    }

    public void requestMoreCraftables() {
        this.craftState.requestMore();
    }

    public void craftRecipeToLinked(String recipeId) {
        craftRecipeToLinked(recipeId, 1);
    }

    public void craftRecipeToLinked(String recipeId, int craftCount) {
        this.craftState.craft(recipeId, craftCount);
    }

    // =========================================================================
    //  Public actions — funnel / link / slot
    // =========================================================================

    public void linkStorage(BlockPos pos) {
        linkStorage(pos, true);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        if (pos == null) {
            return;
        }
        RtsClientPacketGateway.sendLinkStorage(pos, allowStore);
    }

    public void unlinkLinkedStorage(BlockPos pos) {
        RtsClientPacketGateway.sendUnlinkStorage(pos);
    }

    public void updateLinkedStorageSettings(BlockPos pos, boolean extractOnly, int priority) {
        RtsClientPacketGateway.sendUpdateLinkedStorage(pos, extractOnly, priority);
    }

    public void storeHotbarSlotToLinked(int slot) {
        RtsClientPacketGateway.sendStoreHotbarSlot(slot);
    }

    public void fillInventoryFromLinked() {
        RtsClientPacketGateway.sendFillInventory();
    }

    public void setFunnelEnabled(boolean enabled) {
        if (this.funnelEnabled == enabled) {
            return;
        }
        this.funnelEnabled = enabled;
        RtsClientPacketGateway.sendSetFunnelEnabled(enabled);
    }

    public void toggleFunnelEnabled() {
        setFunnelEnabled(!this.funnelEnabled);
    }

    // =========================================================================
    //  Public actions — quick slot / GUI binding (delegated from controller)
    // =========================================================================

    /**
     * Assigns a quick slot. Called from the controller which provides the selected item data.
     */
    public void assignQuickSlotFromSelected(int index, String selectedItemId, ItemStack selectedItemPreview) {
        this.bindingState.assignSelected(index, selectedItemId, selectedItemPreview);
    }

    public void assignQuickSlotFromToolItem(int index, ItemStack stack) {
        this.bindingState.assignTool(index, stack);
    }

    public void clearQuickSlot(int index) {
        this.bindingState.clearQuick(index);
    }

    public void setGuiBinding(int index, BlockPos pos, EnumFacing face, String itemIdHint) {
        this.bindingState.setBinding(index, pos, face, itemIdHint);
    }

    public void clearGuiBinding(int index) {
        this.bindingState.clearBinding(index);
    }

    public void openGuiBinding(int index) {
        // NOTE: beginRemoteMenuOpenGrace() is called by the controller before
        // delegating to this method.
        this.bindingState.openBinding(index);
    }

    // =========================================================================
    //  Payload handlers (public, called from controller)
    // =========================================================================

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        this.refreshState.applyDirty(payload != null && payload.dirty());
    }

    /**
     * Applies a storage page payload received from the server.
     *
     * @param payload          the storage page payload
     * @param afterPageApplied called after all state is updated but before
     *                         {@code refreshSelectedItemPreviewFromStorage}
     *                         (used by the controller for cross-cutting concerns)
     */
    public void applyStoragePage(S2CRtsStoragePagePayload payload, Runnable afterPageApplied) {
        markStorageScanFinished();
        clearStorageViewDirty();
        StoragePagePayloadDecoder.DecodedPage decoded = StoragePagePayloadDecoder.decode(payload, this.linkedStorageName);
        this.storageLinked = payload.linked();
        this.linkedStorageName = payload.linkedName();
        this.autoStoreMinedDrops = payload.autoStoreMinedDrops();
        this.bdNetworkEnabled = payload.useBdNetwork();
        this.linkedStoragePositions.clear();
        this.linkedStoragePositions.addAll(decoded.positions());
        this.linkedStorageEntries.clear();
        this.linkedStorageEntries.addAll(decoded.linked());
        this.storagePage = payload.page();
        this.storageTotalPages = Math.max(1, payload.totalPages());
        this.storageTotalEntries = payload.totalEntries();
        this.storageSearch = payload.search();
        this.storageCategory = normalizeCategory(payload.category());
        this.storageSort = RtsStorageSort.byId(payload.sort());
        this.storageSortAscending = payload.ascending();
        this.storageCategories.clear();
        this.storageCategories.add(CATEGORY_ALL);
        for (String category : payload.categories()) {
            String normalized = normalizeCategory(category);
            if (!this.storageCategories.contains(normalized)) {
                this.storageCategories.add(normalized);
            }
        }
        if (!this.storageCategories.contains(this.storageCategory)) {
            this.storageCategory = CATEGORY_ALL;
        }
        this.storageEntries.clear();
        this.storageEntries.addAll(decoded.items());
        this.fluidEntries.clear();
        this.fluidEntries.addAll(decoded.fluids());
        this.recentEntries.clear();
        this.recentEntries.addAll(decoded.recent());

        if (payload.totalCountsSnapshot()) {
            this.storageTotalCounts.clear();
            this.storageTotalCounts.putAll(decoded.totals());
        }

        this.bindingState.applyQuickSlots(payload.quickSlotItemIds(), payload.quickSlotPreviews(), this.storageEntries);
        this.bindingState.applyBindings(payload.guiBindingLabels(), payload.guiBindingItemIds());

        this.funnelEnabled = payload.funnelEnabled();
        this.funnelBufferEntries.clear();
        this.funnelBufferEntries.addAll(decoded.funnel());
        this.storageRevision++;
        if (!this.storageLinked && this.linkedStoragePositions.isEmpty()) {
            this.craftState.clear();
        }

        if (afterPageApplied != null) {
            afterPageApplied.run();
        }
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        this.craftState.apply(payload);
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        this.craftState.applyFeedback(payload);
    }

    // =========================================================================
    //  Package-private helpers (called from controller for reset/manage)
    // =========================================================================

    void clearStorageState() {
        this.storageEntries.clear();
        this.fluidEntries.clear();
        this.recentEntries.clear();
        this.storageLinked = false;
        this.linkedStorageName = "No Storage";
        this.linkedStoragePositions.clear();
        this.linkedStorageEntries.clear();
        this.storagePage = 0;
        this.storageTotalPages = 1;
        this.storageTotalEntries = 0;
        this.storageSearch = "";
        this.storageCategory = CATEGORY_ALL;
        this.storageSort = RtsStorageSort.QUANTITY;
        this.storageSortAscending = false;
        this.storageCategories.clear();
        this.storageCategories.add(CATEGORY_ALL);
        this.storageCollapsed = false;
        clearStorageScanState();
        clearStorageViewDirty();
        this.refreshState.forgetSnapshot();
        this.bdNetworkEnabled = true;
        this.autoStoreMinedDrops = true;
        this.funnelBufferEntries.clear();
        this.craftState.clear();
        clearQuickSlotsLocal();
        clearGuiBindingsLocal();
    }

    void clearStorageStateOnDisable() {
        clearStorageScanState();
        clearStorageViewDirty();
        this.refreshState.forgetSnapshot();
        this.funnelEnabled = false;
        this.funnelBufferEntries.clear();
        this.craftState.clear();
        clearQuickSlotsLocal();
        clearGuiBindingsLocal();
    }

    void tickStorageAutoRefresh(boolean storageViewVisible) {
        if (this.refreshState.shouldRequestRefresh(storageViewVisible, hasStoragePageSnapshot())) {
            requestStoragePage(this.storagePage);
        }
    }

    void setBdNetworkWithoutPacket(boolean enabled) {
        this.bdNetworkEnabled = enabled;
    }

    void setAutoStoreMinedDropsWithoutPacket(boolean enabled) {
        this.autoStoreMinedDrops = enabled;
    }

    void setFunnelWithoutPacket(boolean enabled) {
        this.funnelEnabled = enabled;
    }

    void setStorageLinked(boolean linked) {
        this.storageLinked = linked;
    }

    void setLinkedStorageName(String name) {
        this.linkedStorageName = name == null ? "No Storage" : name;
    }

    void clearFunnelTarget(Runnable clearCooldown) {
        if (clearCooldown != null) {
            clearCooldown.run();
        }
    }

    void clearQuickSlotsLocal() {
        this.bindingState.clearQuickSlots();
    }

    void clearGuiBindingsLocal() {
        this.bindingState.clearGuiBindings();
    }

    /** Returns true if the storage page can be auto-refreshed and a refresh should be scheduled. */
    boolean isStorageScanPopupVisible() {
        return this.refreshState.popupVisible();
    }

    /** Returns the stored storage entries for internal use by controller (e.g., selected item preview). */
    List<StorageEntry> getInternalStorageEntries() {
        return this.storageEntries;
    }

    Map<String, Long> getInternalStorageTotalCounts() {
        return this.storageTotalCounts;
    }

    // =========================================================================
    //  Private helpers
    // =========================================================================

    private void markStorageScanStarted() {
        this.refreshState.markScanStarted();
    }

    private void markStorageScanFinished() {
        this.refreshState.markScanFinished();
    }

    void clearStorageScanState() {
        this.refreshState.clearScan();
    }

    void clearStorageViewDirty() {
        this.refreshState.clearDirty();
    }

    private long getStorageFluidAmount(String fluidId) {
        if (fluidId == null || fluidId.trim().isEmpty()) {
            return 0L;
        }
        for (FluidEntry entry : this.fluidEntries) {
            if (fluidId.equals(entry.fluidId())) {
                return Math.max(0L, entry.amount());
            }
        }
        return 0L;
    }

    // =========================================================================
    //  Static helpers
    // =========================================================================

    private static double clampLayoutNormalized(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return MathHelper.clamp(value, 0.0D, 1.0D);
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        return CATEGORY_MOD_PREFIX + value;
    }

}
