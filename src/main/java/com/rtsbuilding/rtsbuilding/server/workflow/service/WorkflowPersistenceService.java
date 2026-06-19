package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.data.RtsWorkflowStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

/**
 * 工作流持久化服务——负责工作流条目在内存与持久化存储之间的读写。
 *
 * <p>Phase 4 从 {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine}
 * 独立出来的持久化逻辑，引擎不再关心序列化细节。
 */
public final class WorkflowPersistenceService {

    private static final WorkflowPersistenceService INSTANCE = new WorkflowPersistenceService();

    private WorkflowPersistenceService() {
    }

    public static WorkflowPersistenceService getInstance() {
        return INSTANCE;
    }

    /**
     * 将所有玩家的工作流条目持久化到世界存档文件。
     *
     * @param server      Minecraft 服务器实例
     * @param playerSlots 引擎当前持有的 slot 管理器映射
     */
    public void saveAll(MinecraftServer server,
                        Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots) {
        if (server == null) return;
        RtsWorkflowStore.saveAll(server, playerSlots);
    }

    /**
     * 从世界存档文件加载指定玩家的工作流条目。
     *
     * @param server   Minecraft 服务器实例
     * @param playerId 玩家 UUID
     * @return 按维度分组的 slot 管理器映射，可能为空
     */
    public Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayerFromStore(
            MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return Map.of();
        return RtsWorkflowStore.loadPlayer(server, playerId);
    }
}
