package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.data.RtsWorkflowStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

/**
 * 工作流持久化门面，让引擎不直接依赖 NBT 文件格式。
 */
public final class WorkflowPersistenceService {
    private static final WorkflowPersistenceService INSTANCE = new WorkflowPersistenceService();

    private WorkflowPersistenceService() {
    }

    public static WorkflowPersistenceService getInstance() {
        return INSTANCE;
    }

    public void saveAll(
            MinecraftServer server,
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots) {
        RtsWorkflowStore.saveAll(server, playerSlots);
    }

    public Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayerFromStore(
            MinecraftServer server, UUID playerId) {
        return RtsWorkflowStore.loadPlayer(server, playerId);
    }
}
