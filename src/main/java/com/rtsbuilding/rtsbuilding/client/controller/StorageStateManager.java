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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

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

    public static final int QUICK_SLOT_COUNT = 27;
    public static final int GUI_BINDING_SLOT_COUNT = 8;
    private static final int DEFAULT_STORAGE_PAGE_SIZE = 90;
    private static final int MAX_STORAGE_PAGE_SIZE = 180;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";
    private static final long STORAGE_SCAN_RESULT_VISIBLE_MS = 450L;
    private static final int CRAFTABLE_BATCH_SIZE = 12;
    private static final long STORAGE_AUTO_REFRESH_INTERVAL_MS = 30_000L;

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
    private boolean storageScanRunning;
    private long storageScanStartedAtMs;
    private long storageScanVisibleUntilMs;
    private long storagePageReceivedAtMs;
    private boolean storageViewDirty;
    private long storageViewDirtySinceMs;

    // =========================================================================
    //  Craft fields
    // =========================================================================

    private String craftablesSearch = "";
    private boolean craftablesShowUnavailable;
    private final List<CraftableEntry> craftableEntries = new ArrayList<>();
    private int craftablesRevision;
    private boolean craftablesHasMore;
    private final Set<Integer> pendingCraftableOffsets = new HashSet<>();
    private String craftFeedbackItemId = "";
    private int craftFeedbackCount;
    private long craftFeedbackExpiryMs;
    private final List<CraftFeedbackIngredient> craftFeedbackIngredients = new ArrayList<>();

    // =========================================================================
    //  Funnel fields
    // =========================================================================

    private boolean funnelEnabled;
    private final List<FunnelBufferEntry> funnelBufferEntries = new ArrayList<>();

    // =========================================================================
    //  Quick-slot fields
    // =========================================================================

    private final String[] quickSlotItemIds = new String[QUICK_SLOT_COUNT];
    private final String[] quickSlotLabels = new String[QUICK_SLOT_COUNT];
    private final ItemStack[] quickSlotPreviews = new ItemStack[QUICK_SLOT_COUNT];

    // =========================================================================
    //  GUI-binding fields
    // =========================================================================

    private final String[] guiBindingLabels = new String[GUI_BINDING_SLOT_COUNT];
    private final String[] guiBindingItemIds = new String[GUI_BINDING_SLOT_COUNT];
    private final ItemStack[] guiBindingPreviews = new ItemStack[GUI_BINDING_SLOT_COUNT];

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
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            this.quickSlotItemIds[i] = "";
            this.quickSlotLabels[i] = "";
            this.quickSlotPreviews[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < GUI_BINDING_SLOT_COUNT; i++) {
            this.guiBindingLabels[i] = "";
            this.guiBindingItemIds[i] = "";
            this.guiBindingPreviews[i] = ItemStack.EMPTY;
        }
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
        if (itemId == null || itemId.isBlank()) {
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
        return this.storageScanRunning;
    }

    public boolean isStorageViewDirty() {
        return this.storageViewDirty;
    }

    public boolean shouldHighlightStorageRefresh() {
        return this.storageViewDirty;
    }

    public boolean hasStoragePageSnapshot() {
        return this.storagePageReceivedAtMs > 0L || this.storageRevision > 0;
    }

    public boolean hasAnyStorageContent() {
        return this.storageLinked
                || !this.linkedStoragePositions.isEmpty()
                || !this.storageEntries.isEmpty()
                || !this.fluidEntries.isEmpty();
    }

    public float getStorageScanProgress() {
        if (!isStorageScanPopupVisible()) {
            return 0.0F;
        }
        if (this.storageScanRunning) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - this.storageScanStartedAtMs);
            return (float) Math.min(0.92D, elapsed / 900.0D * 0.92D);
        }
        return 1.0F;
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
        return this.craftablesSearch;
    }

    public boolean isCraftablesShowUnavailable() {
        return this.craftablesShowUnavailable;
    }

    public List<CraftableEntry> getCraftableEntries() {
        return Collections.unmodifiableList(this.craftableEntries);
    }

    public int getCraftablesRevision() {
        return this.craftablesRevision;
    }

    public boolean hasMoreCraftables() {
        return this.craftablesHasMore;
    }

    public String getCraftFeedbackItemId() {
        return this.craftFeedbackItemId;
    }

    public int getCraftFeedbackCount() {
        return this.craftFeedbackCount;
    }

    public long getCraftFeedbackExpiryMs() {
        return this.craftFeedbackExpiryMs;
    }

    public List<CraftFeedbackIngredient> getCraftFeedbackIngredients() {
        return Collections.unmodifiableList(this.craftFeedbackIngredients);
    }

    // =========================================================================
    //  Public getters — quick slot / GUI binding
    // =========================================================================

    public int getQuickSlotCount() {
        return QUICK_SLOT_COUNT;
    }

    public String getQuickSlotItemId(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return "";
        }
        return this.quickSlotItemIds[index];
    }

    public String getQuickSlotLabel(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return "";
        }
        return this.quickSlotLabels[index];
    }

    public ItemStack getQuickSlotPreview(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return this.quickSlotPreviews[index];
    }

    public int getGuiBindingCount() {
        return GUI_BINDING_SLOT_COUNT;
    }

    public String getGuiBindingLabel(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return "";
        }
        ItemStack preview = this.guiBindingPreviews[index];
        if (preview != null && !preview.isEmpty()) {
            // 服务端保存的 label 可能已经按服务端语言展开。优先用客户端
            // 物品预览重新解析名称，让 AE 线缆、机器等绑定跟随玩家的当前语言。
            return preview.getHoverName().getString();
        }
        return this.guiBindingLabels[index];
    }

    public ItemStack getGuiBindingPreview(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return this.guiBindingPreviews[index];
    }

    public boolean hasGuiBinding(int index) {
        return !getGuiBindingLabel(index).isBlank();
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
        int safePageSize = Mth.clamp(pageSize, 1, MAX_STORAGE_PAGE_SIZE);
        if (this.storagePageSize == safePageSize) {
            return;
        }
        this.storagePageSize = safePageSize;
        if (hasStoragePageSnapshot() && !this.storageScanRunning) {
            requestStoragePage(this.storagePage);
        }
    }

    public void requestStoragePageIfNoSnapshot(int page) {
        if (!hasStoragePageSnapshot() && !this.storageScanRunning) {
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
        String normalized = normalizeCraftablesSearch(search);
        if (this.craftablesSearch.equals(normalized)) {
            return;
        }
        this.craftablesSearch = normalized;
        requestCraftables();
    }

    public void setCraftablesShowUnavailable(boolean showUnavailable) {
        if (this.craftablesShowUnavailable == showUnavailable) {
            return;
        }
        this.craftablesShowUnavailable = showUnavailable;
        requestCraftables();
    }

    public void toggleCraftablesShowUnavailable() {
        setCraftablesShowUnavailable(!this.craftablesShowUnavailable);
    }

    public void requestCraftables() {
        this.craftablesSearch = normalizeCraftablesSearch(this.craftablesSearch);
        clearCraftablesState();
        if (this.craftablesSearch.isBlank()) {
            return;
        }
        requestCraftablesPage(0, CRAFTABLE_BATCH_SIZE);
    }

    public void requestMoreCraftables() {
        if (this.craftablesSearch.isBlank() || !this.craftablesHasMore) {
            return;
        }
        requestCraftablesPage(this.craftableEntries.size(), CRAFTABLE_BATCH_SIZE);
    }

    public void craftRecipeToLinked(String recipeId) {
        craftRecipeToLinked(recipeId, 1);
    }

    public void craftRecipeToLinked(String recipeId, int craftCount) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendCraftRecipe(recipeId, craftCount);
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
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        if (selectedItemId == null || selectedItemId.isBlank() || selectedItemPreview == null || selectedItemPreview.isEmpty()) {
            clearQuickSlot(index);
            return;
        }
        setQuickSlotLocal(index, selectedItemId, selectedItemPreview.copy());
        RtsClientPacketGateway.sendSetQuickSlot(index, selectedItemId, selectedItemPreview);
    }

    public void assignQuickSlotFromToolItem(int index, ItemStack stack) {
        if (index < 0 || index >= QUICK_SLOT_COUNT || stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String itemId = id.toString();
        setQuickSlotLocal(index, itemId, stack.copy());
        RtsClientPacketGateway.sendSetQuickSlot(index, itemId, stack);
    }

    public void clearQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        setQuickSlotLocal(index, "", ItemStack.EMPTY);
        RtsClientPacketGateway.sendSetQuickSlot(index, "", ItemStack.EMPTY);
    }

    public void setGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT || pos == null) {
            return;
        }
        RtsClientPacketGateway.sendSetGuiBinding(index, pos, face, itemIdHint);
    }

    public void clearGuiBinding(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return;
        }
        this.guiBindingLabels[index] = "";
        RtsClientPacketGateway.sendClearGuiBinding(index);
    }

    public void openGuiBinding(int index) {
        // NOTE: beginRemoteMenuOpenGrace() is called by the controller before
        // delegating to this method.
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT || !hasGuiBinding(index)) {
            return;
        }
        RtsClientPacketGateway.sendOpenGuiBinding(index);
    }

    // =========================================================================
    //  Payload handlers (public, called from controller)
    // =========================================================================

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        if (payload == null || !payload.dirty()) {
            clearStorageViewDirty();
            return;
        }
        if (!this.storageViewDirty) {
            this.storageViewDirtySinceMs = System.currentTimeMillis();
        }
        this.storageViewDirty = true;
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
        this.storageLinked = payload.linked();
        this.linkedStorageName = payload.linkedName();
        this.autoStoreMinedDrops = payload.autoStoreMinedDrops();
        this.bdNetworkEnabled = payload.useBdNetwork();
        this.linkedStoragePositions.clear();
        this.linkedStorageEntries.clear();
        for (int i = 0; i < payload.linkedPositions().size(); i++) {
            Long packed = payload.linkedPositions().get(i);
            if (packed == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(packed.longValue());
            this.linkedStoragePositions.add(pos);
            this.linkedStorageEntries.add(decodeLinkedStorageEntry(payload, i, pos));
        }
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
        this.fluidEntries.clear();
        this.recentEntries.clear();

        int size = Math.min(payload.itemStacks().size(), payload.counts().size());
        for (int i = 0; i < size; i++) {
            ItemStack stack = payload.itemStacks().get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack preview = stack.copy();
            preview.setCount(1);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(preview.getItem());
            if (id == null) {
                continue;
            }
            this.storageEntries.add(new StorageEntry(preview, id.toString(), payload.counts().get(i), id.getNamespace(), id.getPath()));
        }

        if (payload.totalCountsSnapshot()) {
            this.storageTotalCounts.clear();
            int totalItemSize = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
            for (int i = 0; i < totalItemSize; i++) {
                String itemId = payload.totalItemIds().get(i);
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                this.storageTotalCounts.put(itemId, Math.max(0L, payload.totalItemCounts().get(i)));
            }
        }

        int fluidSize = Math.min(payload.fluidIds().size(),
                Math.min(payload.fluidAmounts().size(), payload.fluidCapacities().size()));
        for (int i = 0; i < fluidSize; i++) {
            String fluidId = payload.fluidIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(fluidId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
            ItemStack preview = FluidUtil.getFilledBucket(fluidStack);
            String label = fluid.getFluidType().getDescription(fluidStack).getString();
            this.fluidEntries.add(new FluidEntry(
                    fluidId, label,
                    payload.fluidAmounts().get(i),
                    payload.fluidCapacities().get(i),
                    id.getNamespace(), id.getPath(), preview));
        }

        int recentSize = Math.min(
                payload.recentIds().size(),
                Math.min(
                        payload.recentAmounts().size(),
                        Math.min(payload.recentCapacities().size(), payload.recentKinds().size())));
        for (int i = 0; i < recentSize; i++) {
            RecentEntry entry = decodeRecentEntry(
                    payload.recentIds().get(i),
                    payload.recentAmounts().get(i),
                    payload.recentCapacities().get(i),
                    payload.recentKinds().get(i));
            if (entry != null) {
                this.recentEntries.add(entry);
            }
        }

        applyQuickSlotPayload(payload.quickSlotItemIds(), payload.quickSlotPreviews());
        applyGuiBindingPayload(payload.guiBindingLabels(), payload.guiBindingItemIds());

        this.funnelEnabled = payload.funnelEnabled();
        this.funnelBufferEntries.clear();
        int funnelBufferSize = Math.min(payload.funnelBufferItemIds().size(), payload.funnelBufferCounts().size());
        for (int i = 0; i < funnelBufferSize; i++) {
            String itemId = payload.funnelBufferItemIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            long count = Math.max(0L, payload.funnelBufferCounts().get(i));
            if (count <= 0L) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            this.funnelBufferEntries.add(new FunnelBufferEntry(stack, itemId, count));
        }
        this.storageRevision++;
        if (!this.storageLinked && this.linkedStoragePositions.isEmpty()) {
            clearCraftablesState();
        }

        if (afterPageApplied != null) {
            afterPageApplied.run();
        }
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        String payloadSearch = normalizeCraftablesSearch(payload.search());
        if (!this.craftablesSearch.equals(payloadSearch)
                || this.craftablesShowUnavailable != payload.showUnavailable()) {
            return;
        }

        int offset = Math.max(0, payload.offset());
        this.pendingCraftableOffsets.remove(offset);
        if (!payload.append() || offset == 0) {
            this.craftableEntries.clear();
        } else if (offset != this.craftableEntries.size()) {
            return;
        }

        int size = Math.min(
                payload.recipeIds().size(),
                Math.min(
                        payload.resultItemIds().size(),
                        Math.min(
                                payload.resultCounts().size(),
                                Math.min(payload.craftable().size(), payload.missingSummaries().size()))));
        int optionFlatIndex = 0;
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.tryParse(payload.resultItemIds().get(i));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                optionFlatIndex += i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            int resultCount = Math.max(1, payload.resultCounts().get(i));
            stack.setCount(Math.min(resultCount, stack.getMaxStackSize()));
            int optionCount = i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
            List<CraftRecipeOption> recipeOptions = new ArrayList<>(optionCount);
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                if (optionFlatIndex >= payload.optionRecipeIds().size()
                        || optionFlatIndex >= payload.optionResultCounts().size()
                        || optionFlatIndex >= payload.optionCraftable().size()
                        || optionFlatIndex >= payload.optionSummaries().size()
                        || optionFlatIndex >= payload.optionMissingSummaries().size()) {
                    break;
                }
                recipeOptions.add(new CraftRecipeOption(
                        payload.optionRecipeIds().get(optionFlatIndex),
                        Math.max(1, payload.optionResultCounts().get(optionFlatIndex)),
                        payload.optionCraftable().get(optionFlatIndex),
                        payload.optionSummaries().get(optionFlatIndex),
                        payload.optionMissingSummaries().get(optionFlatIndex)));
                optionFlatIndex++;
            }
            if (recipeOptions.isEmpty()) {
                recipeOptions.add(new CraftRecipeOption(
                        payload.recipeIds().get(i),
                        resultCount,
                        payload.craftable().get(i),
                        stack.getHoverName().getString(),
                        payload.missingSummaries().get(i)));
            }
            this.craftableEntries.add(new CraftableEntry(
                    stack,
                    payload.recipeIds().get(i),
                    payload.resultItemIds().get(i),
                    resultCount,
                    payload.craftable().get(i),
                    payload.missingSummaries().get(i),
                    id.getNamespace(),
                    id.getPath(),
                    List.copyOf(recipeOptions)));
        }
        this.craftablesSearch = payloadSearch;
        this.craftablesShowUnavailable = payload.showUnavailable();
        this.craftablesHasMore = payload.hasMore();
        this.craftablesRevision++;
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        String itemId = payload.itemId() == null ? "" : payload.itemId();
        int craftedCount = Math.max(0, payload.craftedCount());
        if (itemId.isBlank() || craftedCount <= 0) {
            return;
        }
        List<CraftFeedbackIngredient> decodedIngredients = new ArrayList<>();
        int ingredientSize = Math.min(payload.consumedItemIds().size(), payload.consumedCounts().size());
        for (int i = 0; i < ingredientSize; i++) {
            String consumedItemId = payload.consumedItemIds().get(i);
            ResourceLocation consumedKey = ResourceLocation.tryParse(consumedItemId);
            if (consumedKey == null || !BuiltInRegistries.ITEM.containsKey(consumedKey)) {
                continue;
            }
            ItemStack preview = new ItemStack(BuiltInRegistries.ITEM.get(consumedKey));
            decodedIngredients.add(new CraftFeedbackIngredient(
                    consumedItemId,
                    preview.getHoverName().getString(),
                    preview,
                    Math.max(0, payload.consumedCounts().get(i))));
        }
        long now = System.currentTimeMillis();
        boolean mergeWithActive = itemId.equals(this.craftFeedbackItemId) && now <= this.craftFeedbackExpiryMs;
        if (mergeWithActive) {
            this.craftFeedbackCount += craftedCount;
        } else {
            this.craftFeedbackItemId = itemId;
            this.craftFeedbackCount = craftedCount;
        }
        if (mergeWithActive) {
            mergeCraftFeedbackIngredients(decodedIngredients);
        } else {
            this.craftFeedbackIngredients.clear();
            this.craftFeedbackIngredients.addAll(decodedIngredients);
        }
        this.craftFeedbackExpiryMs = now + 2200L;
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
        this.storagePageReceivedAtMs = 0L;
        this.bdNetworkEnabled = true;
        this.autoStoreMinedDrops = true;
        this.funnelBufferEntries.clear();
        clearCraftablesState();
        clearQuickSlotsLocal();
        clearGuiBindingsLocal();
    }

    void clearStorageStateOnDisable() {
        clearStorageScanState();
        clearStorageViewDirty();
        this.storagePageReceivedAtMs = 0L;
        this.funnelEnabled = false;
        this.funnelBufferEntries.clear();
        clearCraftablesState();
        clearQuickSlotsLocal();
        clearGuiBindingsLocal();
    }

    void tickStorageAutoRefresh(boolean viewDirtyOverride) {
        if (!viewDirtyOverride
                || this.storageScanRunning
                || !hasStoragePageSnapshot()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.storageViewDirtySinceMs <= 0L) {
            this.storageViewDirtySinceMs = now;
            return;
        }
        if (now - this.storageViewDirtySinceMs < STORAGE_AUTO_REFRESH_INTERVAL_MS) {
            return;
        }
        requestStoragePage(this.storagePage);
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
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            this.quickSlotItemIds[i] = "";
            this.quickSlotLabels[i] = "";
            this.quickSlotPreviews[i] = ItemStack.EMPTY;
        }
    }

    void clearGuiBindingsLocal() {
        for (int i = 0; i < GUI_BINDING_SLOT_COUNT; i++) {
            this.guiBindingLabels[i] = "";
            this.guiBindingItemIds[i] = "";
            this.guiBindingPreviews[i] = ItemStack.EMPTY;
        }
    }

    /** Returns true if the storage page can be auto-refreshed and a refresh should be scheduled. */
    boolean isStorageScanPopupVisible() {
        if (this.storageScanRunning) {
            return true;
        }
        if (this.storageScanVisibleUntilMs <= 0L) {
            return false;
        }
        return System.currentTimeMillis() < this.storageScanVisibleUntilMs;
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
        if (!RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            clearStorageScanState();
            return;
        }
        this.storageScanRunning = true;
        this.storageScanStartedAtMs = System.currentTimeMillis();
        this.storageScanVisibleUntilMs = 0L;
    }

    private void markStorageScanFinished() {
        if (!this.storageScanRunning && this.storageScanStartedAtMs <= 0L) {
            return;
        }
        this.storageScanRunning = false;
        long now = System.currentTimeMillis();
        this.storagePageReceivedAtMs = now;
        this.storageScanVisibleUntilMs = now + STORAGE_SCAN_RESULT_VISIBLE_MS;
    }

    void clearStorageScanState() {
        this.storageScanRunning = false;
        this.storageScanStartedAtMs = 0L;
        this.storageScanVisibleUntilMs = 0L;
    }

    void clearStorageViewDirty() {
        this.storageViewDirty = false;
        this.storageViewDirtySinceMs = 0L;
    }

    private void requestCraftablesPage(int offset, int limit) {
        int normalizedOffset = Math.max(0, offset);
        int normalizedLimit = Math.max(1, limit);
        if (!this.pendingCraftableOffsets.add(normalizedOffset)) {
            return;
        }
        RtsClientPacketGateway.sendRequestCraftables(
                this.craftablesSearch,
                this.craftablesShowUnavailable,
                normalizedOffset,
                normalizedLimit);
    }

    private void clearCraftablesState() {
        boolean changed = !this.craftableEntries.isEmpty()
                || this.craftablesHasMore
                || !this.pendingCraftableOffsets.isEmpty();
        this.craftableEntries.clear();
        this.craftablesHasMore = false;
        this.pendingCraftableOffsets.clear();
        if (changed) {
            this.craftablesRevision++;
        }
    }

    private void applyQuickSlotPayload(List<String> payloadQuickSlots, List<ItemStack> payloadQuickSlotPreviews) {
        clearQuickSlotsLocal();
        int size = Math.min(QUICK_SLOT_COUNT, payloadQuickSlots == null ? 0 : payloadQuickSlots.size());
        for (int i = 0; i < size; i++) {
            String itemId = payloadQuickSlots.get(i);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            ItemStack preview = payloadQuickSlotPreviews != null && i < payloadQuickSlotPreviews.size()
                    ? payloadQuickSlotPreviews.get(i)
                    : ItemStack.EMPTY;
            if (preview == null || preview.isEmpty() || !preview.is(BuiltInRegistries.ITEM.get(key))) {
                preview = resolveQuickSlotFallbackPreview(itemId, key);
            } else {
                preview = preview.copyWithCount(1);
            }
            setQuickSlotLocal(i, itemId, preview);
        }
    }

    private ItemStack resolveQuickSlotFallbackPreview(String itemId, ResourceLocation key) {
        for (StorageEntry entry : this.storageEntries) {
            if (entry != null && itemId.equals(entry.itemId()) && entry.stack() != null && !entry.stack().isEmpty()) {
                return entry.stack().copyWithCount(1);
            }
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private void applyGuiBindingPayload(List<String> payloadGuiBindings, List<String> payloadGuiBindingItemIds) {
        clearGuiBindingsLocal();
        int size = Math.min(
                GUI_BINDING_SLOT_COUNT,
                Math.min(
                        payloadGuiBindings == null ? 0 : payloadGuiBindings.size(),
                        payloadGuiBindingItemIds == null ? 0 : payloadGuiBindingItemIds.size()));
        for (int i = 0; i < size; i++) {
            String label = payloadGuiBindings.get(i);
            this.guiBindingLabels[i] = label == null ? "" : label;
            String itemId = payloadGuiBindingItemIds.get(i);
            this.guiBindingItemIds[i] = itemId == null ? "" : itemId;
            ResourceLocation key = ResourceLocation.tryParse(this.guiBindingItemIds[i]);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                this.guiBindingItemIds[i] = "";
                this.guiBindingPreviews[i] = ItemStack.EMPTY;
                continue;
            }
            this.guiBindingPreviews[i] = new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
    }

    private void setQuickSlotLocal(int index, String itemId, ItemStack preview) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        String normalizedItemId = itemId == null ? "" : itemId;
        ItemStack normalizedPreview = preview == null ? ItemStack.EMPTY : preview.copy();
        if (!normalizedPreview.isEmpty()) {
            normalizedPreview.setCount(1);
        }
        this.quickSlotItemIds[index] = normalizedItemId;
        if (normalizedItemId.isBlank() || normalizedPreview.isEmpty()) {
            this.quickSlotLabels[index] = "";
            this.quickSlotPreviews[index] = ItemStack.EMPTY;
            return;
        }
        this.quickSlotLabels[index] = normalizedPreview.getHoverName().getString();
        this.quickSlotPreviews[index] = normalizedPreview;
    }

    private LinkedStorageEntry decodeLinkedStorageEntry(S2CRtsStoragePagePayload payload, int index, BlockPos pos) {
        String label = index >= 0 && index < payload.linkedNames().size()
                ? payload.linkedNames().get(index)
                : this.linkedStorageName;
        if (label == null || label.isBlank()) {
            label = "Linked Storage";
        }
        byte mode = index >= 0 && index < payload.linkedModes().size()
                ? payload.linkedModes().get(index)
                : C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
        int priority = index >= 0 && index < payload.linkedPriorities().size()
                ? payload.linkedPriorities().get(index)
                : 0;
        boolean worldAvailable = index >= 0 && index < payload.linkedWorldAvailable().size()
                && Boolean.TRUE.equals(payload.linkedWorldAvailable().get(index));
        ItemStack preview = ItemStack.EMPTY;
        String iconItemId = index >= 0 && index < payload.linkedIconItemIds().size()
                ? payload.linkedIconItemIds().get(index)
                : "";
        ResourceLocation iconKey = ResourceLocation.tryParse(iconItemId);
        if (iconKey != null && BuiltInRegistries.ITEM.containsKey(iconKey)) {
            preview = new ItemStack(BuiltInRegistries.ITEM.get(iconKey));
        }
        return new LinkedStorageEntry(pos, label, mode, priority, preview, worldAvailable);
    }

    private long getStorageFluidAmount(String fluidId) {
        if (fluidId == null || fluidId.isBlank()) {
            return 0L;
        }
        for (FluidEntry entry : this.fluidEntries) {
            if (fluidId.equals(entry.fluidId())) {
                return Math.max(0L, entry.amount());
            }
        }
        return 0L;
    }

    private void mergeCraftFeedbackIngredients(List<CraftFeedbackIngredient> added) {
        if (added == null || added.isEmpty()) {
            return;
        }
        Map<String, CraftFeedbackIngredient> merged = new LinkedHashMap<>();
        for (CraftFeedbackIngredient ingredient : this.craftFeedbackIngredients) {
            if (ingredient == null || ingredient.itemId() == null || ingredient.itemId().isBlank()) {
                continue;
            }
            merged.put(ingredient.itemId(), ingredient);
        }
        for (CraftFeedbackIngredient ingredient : added) {
            if (ingredient == null || ingredient.itemId() == null || ingredient.itemId().isBlank()) {
                continue;
            }
            CraftFeedbackIngredient existing = merged.get(ingredient.itemId());
            if (existing == null) {
                merged.put(ingredient.itemId(), ingredient);
                continue;
            }
            merged.put(
                    ingredient.itemId(),
                    new CraftFeedbackIngredient(
                            ingredient.itemId(),
                            ingredient.label(),
                            ingredient.preview().copy(),
                            existing.count() + ingredient.count()));
        }
        this.craftFeedbackIngredients.clear();
        this.craftFeedbackIngredients.addAll(merged.values());
    }

    // =========================================================================
    //  Static helpers
    // =========================================================================

    private static double clampLayoutNormalized(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Mth.clamp(value, 0.0D, 1.0D);
    }

    private static String normalizeCraftablesSearch(String search) {
        return search == null ? "" : search.trim();
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

    private static RecentEntry decodeRecentEntry(String idText, long amount, long capacity, byte kind) {
        if (idText == null || idText.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            return null;
        }
        boolean fluidKind = kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
        if (fluidKind) {
            if (!BuiltInRegistries.FLUID.containsKey(id)) {
                return null;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
            ItemStack preview = FluidUtil.getFilledBucket(fluidStack);
            String label = fluid.getFluidType().getDescription(fluidStack).getString();
            return new RecentEntry(true, idText, label, Math.max(0L, amount), Math.max(0L, capacity), kind, preview);
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        ItemStack preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return new RecentEntry(false, idText, preview.getHoverName().getString(), Math.max(0L, amount), 0L, kind, preview);
    }
}
