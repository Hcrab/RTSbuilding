package com.rtsbuilding.rtsbuilding.client.module.storage;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.List;

/**
 * 存储模块——管理存储页面、合成、漏斗、快槽和 GUI 绑定。
 *
 * <p>替代原 {@code StorageStateManager} + {@code ClientRtsController} 中
 * 的存储相关逻辑。通过事件驱动更新，无需 tick 轮询。</p>
 */
public final class StorageModule implements FeatureModule {

    private final StorageState state = new StorageState();

    @Override
    public String moduleId() {
        return "storage";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) state.clearSessionState();
        } else if (event instanceof StateEvent.PlayerDied) {
            state.clearSessionState();
        }
    }

    // ======================================================================
    //  Tick (only auto-refresh)
    // ======================================================================

    @Override
    public void tick(long epochMs, int tickIndex) {
        state.tickAutoRefresh(epochMs);
    }

    // ======================================================================
    //  Network callbacks (called from network handler)
    // ======================================================================

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        state.applyStoragePage(payload);
        kernel().dispatch(new StateEvent.StoragePageLoaded(state.getRevision(), payload));
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        state.applyCraftables(payload);
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        state.applyCraftFeedback(payload);
    }

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        state.applyStorageDirty(payload);
    }

    // ======================================================================
    //  Public actions
    // ======================================================================

    public void requestPage(int page) {
        state.requestStoragePage(page);
    }

    public void requestCraftables() {
        state.requestCraftables();
    }

    public void setSearch(String search) {
        state.setStorageSearch(search);
    }

    public void setCraftablesSearch(String search) {
        state.setCraftablesSearch(search);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        RtsClientPacketGateway.sendLinkStorage(pos, allowStore);
    }

    public void craftRecipe(String recipeId, int count) {
        if (recipeId == null || recipeId.isBlank()) return;
        RtsClientPacketGateway.sendCraftRecipe(recipeId, count);
    }

    // ======================================================================
    //  State accessors (for UI)
    // ======================================================================

    public StorageState getState() {
        return this.state;
    }

    public boolean isLinked() { return state.isStorageLinked(); }
    public boolean isFunnelEnabled() { return state.isFunnelEnabled(); }
    public boolean isStorageCollapsed() { return state.isStorageCollapsed(); }
    public boolean hasAnyContent() { return state.hasAnyStorageContent(); }
    public int getRevision() { return state.getRevision(); }
    public List<?> getEntries() { return state.getStorageEntries(); }
    public List<?> getFluidEntries() { return state.getFluidEntries(); }
    public List<?> getRecentEntries() { return state.getRecentEntries(); }
    public List<?> getFunnelBufferEntries() { return state.getFunnelBufferEntries(); }
    public List<?> getCraftableEntries() { return state.getCraftableEntries(); }

    /** 获取已链接的存储方块列表（用于渲染角支架线框） */
    public List<LinkedStorageEntry> getLinkedStorageEntries() { return state.getLinkedStorageEntries(); }

    // ======================================================================
    //  容器绑定逻辑（从 BuilderScreen 迁入，解耦 UI 框架与业务）
    // ======================================================================

    /**
     * 点击模式单点绑定（含模式循环）。
     * 未绑定 → 双向链接；已绑定 → 循环切换双向/仅提取。
     *
     * @return true 表示操作已执行
     */
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
            RtsClientPacketGateway.sendUpdateLinkedStorage(existing.get().pos(), nextExtractOnly, 0);
        } else {
            RtsClientPacketGateway.sendLinkStorage(pos, true);
        }

        RemoteMenuModule rmm = kernel().module(RemoteMenuModule.class);
        if (rmm != null) rmm.beginRemoteMenuOpenGrace();
        requestPage(getState().getPage());
        return true;
    }

    /**
     * 点击模式单点解绑——仅当目标方块已在链接列表中时解除。
     *
     * @return true 表示已执行解绑
     */
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
        requestPage(getState().getPage());
        return true;
    }

    /**
     * 批量链接框选区域内的所有容器方块到存储系统。
     *
     * @return 成功处理（绑定或切换模式）的容器数量
     */
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
                        RtsClientPacketGateway.sendUpdateLinkedStorage(existing.pos(), nextExtractOnly, 0);
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
        if (count > 0) {
            requestPage(getState().getPage());
        }
        return count;
    }

    /**
     * 批量解绑框选区域内所有已链接的容器。
     *
     * @return 成功解绑的数量
     */
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

        if (count > 0) {
            requestPage(getState().getPage());
        }
        return count;
    }

    // ======================== 双箱规范位置工具 ========================

    /**
     * 将链接条目中的位置规范化——对双箱子返回其规范位置（较小角坐标），
     * 确保调用方不受半箱位置影响。
     */
    private static BlockPos canonicalizeLinkedEntryPos(Level level, BlockPos pos) {
        if (level != null && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                return canonicalChestPos(level, pos, state);
            }
        }
        return pos;
    }

    /**
     * 获取容器的规范位置——对于双箱子返回两个半箱中坐标较小的角，
     * 确保双箱子只被处理一次。单箱子或非箱子方块返回自身位置。
     */
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

    // ======================================================================
    //  Convenience
    // ======================================================================

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
