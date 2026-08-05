package com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage;

import com.rtsbuilding.rtsbuilding.client.domain.state.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StorageState {

    
    
    

    private boolean storageCollapsed;
    private boolean storageLinked;
    private boolean bdNetworkEnabled = true;
    private String linkedStorageName = "No Storage";
    private final List<BlockPos> linkedPositions = new ArrayList<>();
    
    private final List<LinkedStorageEntry> linkedStorageEntries = new ArrayList<>();
    
    private final List<String> linkedDisplayNames = new ArrayList<>();
    
    private final List<String> linkedIconItemIds = new ArrayList<>();
    
    private final List<Integer> linkedPriorities = new ArrayList<>();
    private int storagePage, storagePageSize = 100000, storageTotalPages = 1, storageTotalEntries;
    private int storageRevision;
    
    private int pageRequestCount;
    private String storageSearch = "", storageCategory = "all";
    private int storageSort;
    private boolean storageSortAscending;
    private final List<String> storageCategories = new ArrayList<>();
    private final List<Object> storageEntries = new ArrayList<>();
    private final Map<String, Long> totalCounts = new HashMap<>();
    private final List<Object> fluidEntries = new ArrayList<>();
    private final List<Object> recentEntries = new ArrayList<>();

    
    
    
    
    private final Set<String> locallyRemovedRecentIds = new LinkedHashSet<>();
    private boolean scanRunning;
    private long scanStartedMs, scanVisibleUntilMs;
    private boolean viewDirty;
    private long viewDirtySinceMs;

    
    
    

    private String craftablesSearch = "";
    private boolean craftablesShowUnavailable;
    private final List<Object> craftableEntries = new ArrayList<>();
    private int craftablesRevision;
    private boolean craftablesHasMore;

    
    
    

    private static final long AUTO_REFRESH_MS = 30_000L;
    
    private static final long SCAN_TIMEOUT_MS = 10_000L;
    private boolean autoRefreshEnabled;

    StorageState() {
        storageCategories.add("all");
    }

    
    
    

    void requestStoragePage(int page) {
        this.scanRunning = true;
        this.scanStartedMs = System.currentTimeMillis();
        this.pageRequestCount++;
        RtsClientPacketGateway.sendRequestStoragePage(page, storageSearch, storageCategory,
                com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort.byId(storageSort), storageSortAscending, storagePageSize);
    }

    void applyStoragePage(S2CRtsStoragePagePayload payload) {
        this.scanRunning = false;
        this.scanVisibleUntilMs = System.currentTimeMillis() + 450L;
        this.viewDirty = false;
        this.storageLinked = payload.linked();
        this.linkedStorageName = payload.linkedName();
        this.storagePage = payload.page();
        this.storageTotalPages = Math.max(1, payload.totalPages());
        this.storageTotalEntries = payload.totalEntries();
        this.storageSearch = payload.search() == null ? "" : payload.search();
        this.storageSort = payload.sort();
        this.storageSortAscending = payload.ascending();
        this.storageRevision++;
        applyPayloadEntries(payload);
    }

    private void applyPayloadEntries(S2CRtsStoragePagePayload payload) {
        
        this.storageEntries.clear();
        this.totalCounts.clear();
        this.fluidEntries.clear();
        this.recentEntries.clear();

        
        this.linkedPositions.clear();
        this.linkedStorageEntries.clear();
        this.linkedDisplayNames.clear();
        this.linkedIconItemIds.clear();
        this.linkedPriorities.clear();
        int linkedSize = Math.min(payload.linkedPositions().size(), payload.linkedWorldAvailable().size());
        for (int i = 0; i < linkedSize; i++) {
            Long packed = payload.linkedPositions().get(i);
            if (packed == null) continue;
            BlockPos pos = BlockPos.of(packed);
            this.linkedPositions.add(pos);
            byte mode = i < payload.linkedModes().size() ? payload.linkedModes().get(i) : 0;
            boolean available = i < payload.linkedWorldAvailable().size()
                    ? Boolean.TRUE.equals(payload.linkedWorldAvailable().get(i)) : false;
            this.linkedStorageEntries.add(new LinkedStorageEntry(pos, mode, available));
            String name = i < payload.linkedNames().size() ? payload.linkedNames().get(i) : "";
            this.linkedDisplayNames.add(name.isBlank() ? pos.toShortString() : name);
            this.linkedIconItemIds.add(i < payload.linkedIconItemIds().size()
                    ? (payload.linkedIconItemIds().get(i) == null ? "" : payload.linkedIconItemIds().get(i)) : "");
            this.linkedPriorities.add(i < payload.linkedPriorities().size()
                    ? (payload.linkedPriorities().get(i) == null ? 0 : payload.linkedPriorities().get(i)) : 0);
        }

        int size = Math.min(payload.itemStacks().size(), payload.counts().size());
        for (int i = 0; i < size; i++) {
            ItemStack stack = payload.itemStacks().get(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack preview = stack.copy();
            preview.setCount(1);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(preview.getItem());
            if (id == null) continue;
            byte mode = i < payload.itemModes().size() ? payload.itemModes().get(i) : 0;
            this.storageEntries.add(new com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry(
                    preview, id.toString(), payload.counts().get(i), id.getNamespace(), id.getPath(),
                    mode));
        }

        
        int fluidSize = Math.min(payload.fluidIds().size(),
                Math.min(payload.fluidAmounts().size(),
                        Math.min(payload.fluidCapacities().size(), payload.fluidModes().size())));
        for (int i = 0; i < fluidSize; i++) {
            String fluidId = payload.fluidIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(fluidId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) continue;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
            ItemStack preview = FluidUtil.getFilledBucket(fluidStack);
            String label = fluid.getFluidType().getDescription(fluidStack).getString();
            byte mode = i < payload.fluidModes().size() ? payload.fluidModes().get(i) : 0;
            this.fluidEntries.add(new FluidEntry(
                    fluidId, label,
                    payload.fluidAmounts().get(i),
                    payload.fluidCapacities().get(i),
                    id.getNamespace(), id.getPath(), preview, mode));
        }

        
        int recentSize = Math.min(payload.recentIds().size(),
                Math.min(payload.recentAmounts().size(), payload.recentKinds().size()));
        recentSize = Math.min(recentSize, 16);
        for (int i = 0; i < recentSize; i++) {
            String recentId = payload.recentIds().get(i);
            if (recentId == null || recentId.isBlank()) continue;
            
            if (locallyRemovedRecentIds.contains(recentId)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(recentId);
            if (rl == null) continue;
            ItemStack preview = ItemStack.EMPTY;
            if (BuiltInRegistries.ITEM.containsKey(rl)) {
                preview = new ItemStack(BuiltInRegistries.ITEM.get(rl), 1);
            } else if (BuiltInRegistries.FLUID.containsKey(rl)) {
                var fluid = BuiltInRegistries.FLUID.get(rl);
                var fluidStack = new FluidStack(fluid, net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME);
                preview = net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(fluidStack);
            }
            this.recentEntries.add(new RecentEntry(
                    recentId,
                    payload.recentAmounts().get(i),
                    i < payload.recentCapacities().size() ? payload.recentCapacities().get(i) : 0,
                    payload.recentKinds().get(i),
                    preview));
        }
    }

    
    
    void removeRecentEntry(String id) {
        if (id == null || id.isBlank()) return;
        locallyRemovedRecentIds.add(id);
        recentEntries.removeIf(e -> e instanceof RecentEntry re && id.equals(re.id()));
        
        if (locallyRemovedRecentIds.size() > 64) {
            java.util.Iterator<String> it = locallyRemovedRecentIds.iterator();
            int drop = locallyRemovedRecentIds.size() - 32;
            while (it.hasNext() && drop-- > 0) {
                it.next();
                it.remove();
            }
        }
    }

    
    void restoreRecentEntry(String id) {
        if (id == null) return;
        locallyRemovedRecentIds.remove(id);
    }

    
    
    

    void requestCraftables() {
        this.craftablesSearch = this.craftablesSearch.trim();
        this.craftableEntries.clear();
        if (this.craftablesSearch.isBlank()) return;
        RtsClientPacketGateway.sendRequestCraftables(this.craftablesSearch, this.craftablesShowUnavailable, 0, 12);
    }

    void applyCraftables(S2CRtsCraftablesPayload payload) {
        String search = payload.search() == null ? "" : payload.search().trim();
        if (!this.craftablesSearch.equals(search)) return;
        if (payload.offset() == 0) this.craftableEntries.clear();
        this.craftablesHasMore = payload.hasMore();
        this.craftablesRevision++;
    }

    void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        
    }

    void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        if (payload == null || !payload.dirty()) {
            this.viewDirty = false;
            return;
        }
        if (!this.viewDirty) this.viewDirtySinceMs = System.currentTimeMillis();
        this.viewDirty = true;
    }

    
    
    

    void tickAutoRefresh(long now) {
        
        if (this.scanRunning && now - this.scanStartedMs > SCAN_TIMEOUT_MS) {
            this.scanRunning = false;
            this.viewDirty = true;
            this.viewDirtySinceMs = now;
        }
        if (!this.viewDirty || this.scanRunning) return;
        if (this.viewDirtySinceMs <= 0L) {
            this.viewDirtySinceMs = now;
            return;
        }
        if (now - this.viewDirtySinceMs < AUTO_REFRESH_MS) return;
        requestStoragePage(this.storagePage);
    }

    
    
    

    void clearSessionState() {
        this.storageEntries.clear();
        this.fluidEntries.clear();
        this.recentEntries.clear();
        this.locallyRemovedRecentIds.clear();
        this.linkedPositions.clear();
        this.linkedStorageEntries.clear();
        this.linkedDisplayNames.clear();
        this.linkedIconItemIds.clear();
        this.linkedPriorities.clear();
        this.storageLinked = false;
        this.storagePage = 0;
        this.storageTotalPages = 1;
        this.storageSearch = "";
        this.craftableEntries.clear();
        this.scanRunning = false;
        this.viewDirty = false;
    }

    
    
    

    public boolean isStorageLinked() { return storageLinked; }
    public boolean isStorageCollapsed() { return storageCollapsed; }
    public void toggleCollapsed() { this.storageCollapsed = !this.storageCollapsed; }
    public boolean hasAnyStorageContent() {
        return storageLinked || !linkedPositions.isEmpty() || !storageEntries.isEmpty() || !fluidEntries.isEmpty();
    }
    public int getRevision() { return storageRevision; }
    
    public int getPageRequestCount() { return pageRequestCount; }
    public List<Object> getStorageEntries() { return storageEntries; }
    public List<Object> getFluidEntries() { return fluidEntries; }
    public List<Object> getRecentEntries() { return recentEntries; }
    public List<RecentEntry> getRecentEntriesTyped() {
        List<RecentEntry> result = new ArrayList<>();
        for (Object obj : recentEntries) {
            if (obj instanceof RecentEntry re) result.add(re);
        }
        return result;
    }
    public List<Object> getCraftableEntries() { return craftableEntries; }
    public List<String> getStorageCategories() { return storageCategories; }
    
    public List<LinkedStorageEntry> getLinkedStorageEntries() { return linkedStorageEntries; }
    
    public List<String> getLinkedDisplayNames() { return linkedDisplayNames; }
    
    public List<String> getLinkedIconItemIds() { return linkedIconItemIds; }
    
    public List<Integer> getLinkedPriorities() { return linkedPriorities; }
    public int getPage() { return storagePage; }
    public int getTotalPages() { return storageTotalPages; }
    public String getSearch() { return storageSearch; }
    public String getCategory() { return storageCategory; }
    public boolean isSortAscending() { return storageSortAscending; }

    
    
    

    public void setStorageSearch(String search) {
        this.storageSearch = search == null ? "" : search;
        requestStoragePage(0);
    }

    public void setCraftablesSearch(String search) {
        this.craftablesSearch = search == null ? "" : search.trim();
        requestCraftables();
    }
}
