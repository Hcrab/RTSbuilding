package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.data.RtsWorkflowStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

/**
 * Workflow persistence service — responsible for reading/writing workflow entries between memory and persistent storage.
 *
 * <p>Persistence logic separated from {@link com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine}
 * in Phase 4; the engine no longer concerns itself with serialization details.
 */
public final class WorkflowPersistenceService {

    private static final WorkflowPersistenceService INSTANCE = new WorkflowPersistenceService();

    private WorkflowPersistenceService() {
    }

    public static WorkflowPersistenceService getInstance() {
        return INSTANCE;
    }

    /**
     * Persist all players' workflow entries to the world save file.
     *
     * @param server      Minecraft server instance
     * @param playerSlots Slot manager map currently held by the engine
     */
    public void saveAll(MinecraftServer server,
                        Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots) {
        if (server == null) return;
        RtsWorkflowStore.saveAll(server, playerSlots);
    }

    /**
     * Load the specified player's workflow entries from the world save file.
     *
     * @param server   Minecraft server instance
     * @param playerId Player UUID
     * @return Slot manager map grouped by dimension, may be empty
     */
    public Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayerFromStore(
            MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return Map.of();
        return RtsWorkflowStore.loadPlayer(server, playerId);
    }
}
