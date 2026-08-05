package com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage;

import com.rtsbuilding.rtsbuilding.client.domain.state.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.domain.state.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StorageModule implements FeatureModule {

    private final StorageState state = new StorageState();

    
    private final Set<BlockPos> locationDisplayPositions = new HashSet<>();

    @Override
    public String moduleId() {
        return "storage";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) {
                state.clearSessionState();
                locationDisplayPositions.clear();
            }
        } else if (event instanceof StateEvent.PlayerDied) {
            state.clearSessionState();
            locationDisplayPositions.clear();
        }
    }

    
    
    

    @Override
    public void tick(long epochMs, int tickIndex) {
        state.tickAutoRefresh(epochMs);
    }

    
    
    

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        state.applyStoragePage(payload);
        kernel().dispatch(new StateEvent.StoragePageLoaded(state.getRevision(), payload));
    }

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        state.applyStorageDirty(payload);
    }

    
    
    

    public void requestPage(int page) {
        state.requestStoragePage(page);
    }

    public void setSearch(String search) {
        state.setStorageSearch(search);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        RtsClientPacketGateway.sendLinkStorage(pos, allowStore);
    }

    
    
    

    public StorageState getState() {
        return this.state;
    }

    public boolean isLinked() { return state.isStorageLinked(); }
    public boolean isStorageCollapsed() { return state.isStorageCollapsed(); }
    public boolean hasAnyContent() { return state.hasAnyStorageContent(); }
    public int getRevision() { return state.getRevision(); }
    public int getPageRequestCount() { return state.getPageRequestCount(); }
    public List<?> getEntries() { return state.getStorageEntries(); }
    public List<?> getFluidEntries() { return state.getFluidEntries(); }
    public List<?> getRecentEntries() { return List.copyOf(state.getRecentEntries()); }
    public List<RecentEntry> getRecentEntriesTyped() { return state.getRecentEntriesTyped(); }

    
    
    
    
    public void removeRecentEntry(String id) {
        state.removeRecentEntry(id);
    }

    
    
    
    
    public void restoreRecentEntry(String id) {
        state.restoreRecentEntry(id);
    }

    
    public List<LinkedStorageEntry> getLinkedStorageEntries() { return state.getLinkedStorageEntries(); }

    
    public List<String> getLinkedDisplayNames() { return state.getLinkedDisplayNames(); }

    
    public List<String> getLinkedIconItemIds() { return state.getLinkedIconItemIds(); }

    
    public List<Integer> getLinkedPriorities() { return state.getLinkedPriorities(); }

    
    public int getLinkedPriority(BlockPos pos) {
        var entries = getLinkedStorageEntries();
        var priorities = getLinkedPriorities();
        for (int i = 0; i < entries.size() && i < priorities.size(); i++) {
            if (entries.get(i).pos().equals(pos)) {
                return priorities.get(i);
            }
        }
        return 0;
    }

    
    
    

    
    public boolean toggleLocationDisplay(BlockPos pos) {
        if (!locationDisplayPositions.remove(pos)) {
            locationDisplayPositions.add(pos);
            return true;
        }
        return false;
    }

    
    public Set<BlockPos> getLocationDisplayPositions() {
        return locationDisplayPositions;
    }

    
    public boolean isLocationDisplayActive(BlockPos pos) {
        return locationDisplayPositions.contains(pos);
    }

    
    

    public boolean handleClickModeBind(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;

        var linkedEntries = getLinkedStorageEntries();
        BlockState state = level.getBlockState(pos);
        BlockPos canonical = state.isAir() ? pos : canonicalChestPos(level, pos, state);
        var existing = linkedEntries.stream()
                .filter(e -> e.pos().equals(pos)
                        || (!state.isAir() && canonicalChestPos(level, e.pos(), level.getBlockState(e.pos())).equals(canonical)))
                .findFirst();

        if (existing.isPresent()) {
            boolean nextExtractOnly = !existing.get().isExtractOnly();
            int currentPriority = getLinkedPriority(existing.get().pos());
            RtsClientPacketGateway.sendUpdateLinkedStorage(existing.get().pos(), nextExtractOnly, currentPriority);
        } else {
            RtsClientPacketGateway.sendLinkStorage(pos, true);
        }

        RemoteMenuModule rmm = kernel().module(RemoteMenuModule.class);
        if (rmm != null) rmm.beginRemoteMenuOpenGrace();
        
        return true;
    }

    
    public boolean handleClickModeUnbind(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;

        var linkedEntries = getLinkedStorageEntries();
        BlockState state = level.getBlockState(pos);
        BlockPos canonical = state.isAir() ? pos : canonicalChestPos(level, pos, state);
        var existing = linkedEntries.stream()
                .filter(e -> e.pos().equals(pos)
                        || (!state.isAir() && canonicalChestPos(level, e.pos(), level.getBlockState(e.pos())).equals(canonical)))
                .findFirst();
        if (existing.isEmpty()) return false;

        RtsClientPacketGateway.sendUnlinkStorage(existing.get().pos());
        
        
        return true;
    }

    
    public int batchLinkContainers(Level level, BlockPos min, BlockPos max) {
        if (level == null || min == null || max == null) return 0;

        RemoteMenuModule rmm = kernel().module(RemoteMenuModule.class);
        int count = 0;

        var linkedEntries = getLinkedStorageEntries();
        java.util.Map<BlockPos, LinkedStorageEntry> linkedMap = new java.util.HashMap<>();
        for (var e : linkedEntries) {
            linkedMap.put(canonicalizeLinkedEntryPos(level, e.pos()), e);
        }

        java.util.Set<BlockPos> processedContainers = new java.util.HashSet<>();

        for (int x = min.getX(); x < max.getX(); x++) {
            for (int y = min.getY(); y < max.getY(); y++) {
                for (int z = min.getZ(); z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (!state.hasBlockEntity()) continue;

                    BlockPos canonical = canonicalChestPos(level, pos, state);
                    if (!processedContainers.add(canonical)) continue;

                    var existing = linkedMap.get(canonical);
                    if (existing != null) {
                        boolean nextExtractOnly = !existing.isExtractOnly();
                        int currentPriority = getLinkedPriority(existing.pos());
                        RtsClientPacketGateway.sendUpdateLinkedStorage(existing.pos(), nextExtractOnly, currentPriority);
                    } else {
                        RtsClientPacketGateway.sendLinkStorage(canonical, true);
                    }
                    count++;
                }
            }
        }

        if (count > 0 && rmm != null) {
            rmm.beginRemoteMenuOpenGrace();
        }
        
        return count;
    }

    
    public int batchUnbindContainers(Level level, BlockPos min, BlockPos max) {
        if (level == null || min == null || max == null) return 0;

        var linkedEntries = getLinkedStorageEntries();
        java.util.Map<BlockPos, LinkedStorageEntry> linkedMap = new java.util.HashMap<>();
        for (var e : linkedEntries) {
            linkedMap.put(canonicalizeLinkedEntryPos(level, e.pos()), e);
        }

        java.util.Set<BlockPos> processedContainers = new java.util.HashSet<>();
        int count = 0;

        for (int x = min.getX(); x < max.getX(); x++) {
            for (int y = min.getY(); y < max.getY(); y++) {
                for (int z = min.getZ(); z < max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    BlockPos canonical = canonicalChestPos(level, pos, state);
                    if (!processedContainers.add(canonical)) continue;

                    var existing = linkedMap.get(canonical);
                    if (existing == null) continue;

                    RtsClientPacketGateway.sendUnlinkStorage(existing.pos());
                    count++;
                }
            }
        }

        
        return count;
    }

    

    
    private static BlockPos canonicalizeLinkedEntryPos(Level level, BlockPos pos) {
        if (level != null && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                return canonicalChestPos(level, pos, state);
            }
        }
        return pos;
    }

    
    private static BlockPos canonicalChestPos(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                var connectedDir = ChestBlock.getConnectedDirection(state);
                BlockPos connectedPos = pos.relative(connectedDir);
                if (level.hasChunk(connectedPos.getX() >> 4, connectedPos.getZ() >> 4)) {
                    BlockState connectedState = level.getBlockState(connectedPos);
                    if (!connectedState.isAir() && connectedState.getBlock() instanceof ChestBlock) {
                        int minX = Math.min(pos.getX(), connectedPos.getX());
                        int minY = Math.min(pos.getY(), connectedPos.getY());
                        int minZ = Math.min(pos.getZ(), connectedPos.getZ());
                        return new BlockPos(minX, minY, minZ);
                    }
                }
            }
        }
        return pos;
    }

    
    
    

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
