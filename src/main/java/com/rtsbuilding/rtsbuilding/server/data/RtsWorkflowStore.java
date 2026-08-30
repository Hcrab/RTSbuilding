package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流条目的持久化存储。
 *
 * <p><b>新版</b>：通过 {@link DataCluster} + {@link WorkflowComponents#FULL_WORKFLOW}
 * 按玩家拆分文件存储（{@code rtsbuilding/players/{uuid}/workflow.dat}）。
 * 使用脏标记延迟刷盘，仅在玩家有活跃工作流时才写对应文件。
 * DataCluster 生命周期统一由 {@link SaveScheduler} 管理，本类只负责编解码。
 *
 * <p><b>旧版</b>：将所有玩家数据写入单个 {@code rtsbuilding/workflow_data.dat}。
 * 旧版 API 保留用于加载遗留存档数据时的回退。
 */
public final class RtsWorkflowStore {

    // ──────────────────────────────────────────────────────────────────
    //  旧版常量（保留用于遗留数据回退）
    // ──────────────────────────────────────────────────────────────────

    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "workflow_data.dat";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final int DATA_VERSION = 1;

    private static final String KEY_DIMENSIONS = "dimensions";

    private RtsWorkflowStore() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  新版 API（逐玩家 DataCluster，通过 SaveScheduler 管理生命周期）
    // ──────────────────────────────────────────────────────────────────

    /**
     * 保存所有玩家在所有维度上的工作流槽位管理器。
     *
     * <p>每个玩家的数据写入 {@code rtsbuilding/players/{uuid}/workflow.dat}，
     * 通过 {@link DataCluster#set(DataComponent, Object)} 标记脏。
     * 写入后通过 {@link SaveScheduler#flushAll()} 立即刷盘以确保数据落盘。
     * DataCluster 生命周期由 {@link SaveScheduler} 统一管理。
     *
     * @param server   Minecraft 服务器实例
     * @param allSlots 玩家 UUID → 维度 → 槽位管理器的映射
     */
    public static void saveAll(MinecraftServer server,
                               Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> allSlots) {
        if (server == null || allSlots == null) return;

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : allSlots.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimSlots = playerEntry.getValue();
            if (dimSlots == null || dimSlots.isEmpty()) continue;

            CompoundTag dimensions = new CompoundTag();
            boolean hasData = false;

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimSlots.entrySet()) {
                ResourceKey<Level> dimension = dimEntry.getKey();
                RtsWorkflowSlotManager slots = dimEntry.getValue();
                if (slots == null || slots.occupiedCount() == 0) continue;

                CompoundTag slotsTag = slots.saveToNbt();
                if (slotsTag != null && !slotsTag.isEmpty()) {
                    dimensions.put(dimension.location().toString(), slotsTag);
                    hasData = true;
                }
            }

            if (hasData) {
                CompoundTag playerData = new CompoundTag();
                playerData.put(KEY_DIMENSIONS, dimensions);
                cluster(server, playerId).set(WorkflowComponents.FULL_WORKFLOW, playerData);
            }
        }

        // 关键事件后立即刷盘，确保数据落盘
        SaveScheduler.INSTANCE.flushAll();
    }

    /**
     * 从存储中加载指定玩家的工作流槽位管理器。
     *
     * <p>优先从新版的逐玩家 {@link DataCluster} 加载，
     * 如果不存在（首次迁移后遗留数据），回退到旧版全量文件。
     *
     * @param server   Minecraft 服务器实例
     * @param playerId 玩家的 UUID
     * @return 维度 → 槽位管理器的映射
     */
    public static Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayer(
            MinecraftServer server, UUID playerId) {
        // 尝试新版逐玩家文件
        DataCluster dc = cluster(server, playerId);
        CompoundTag root = dc.get(WorkflowComponents.FULL_WORKFLOW);
        if (!root.isEmpty() && root.contains(KEY_DIMENSIONS)) {
            return deserializeDimensions(root.getCompound(KEY_DIMENSIONS));
        }

        // 回退：尝试旧版全量文件
        return loadPlayerLegacy(server, playerId);
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    private static DataCluster cluster(MinecraftServer server, UUID playerId) {
        // 通过 SaveScheduler 统一管理生命周期，避免独立缓存导致的分裂
        return SaveScheduler.INSTANCE.dataCluster(server, playerId, "workflow");
    }

    private static Map<ResourceKey<Level>, RtsWorkflowSlotManager> deserializeDimensions(CompoundTag dimensions) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        for (String dimKey : dimensions.getAllKeys()) {
            ResourceLocation dimLocation = ResourceLocation.tryParse(dimKey);
            if (dimLocation == null) continue;

            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimLocation);
            CompoundTag slotsTag = dimensions.getCompound(dimKey);
            if (slotsTag != null && !slotsTag.isEmpty()) {
                RtsWorkflowSlotManager slots = RtsWorkflowSlotManager.loadFromNbt(slotsTag);
                if (slots.occupiedCount() > 0) {
                    result.put(dimension, slots);
                }
            }
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    //  旧版全量文件回退
    // ──────────────────────────────────────────────────────────────────

    private static Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayerLegacy(
            MinecraftServer server, UUID playerId) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        if (server == null || playerId == null) return result;

        var legacyStore = new RtsAtomicNbtStore(server, DIRECTORY, FILE_NAME);
        RtsNbtStore.ReadResult readResult = legacyStore.readResult();
        if (readResult instanceof RtsNbtStore.ReadResult.Failed failed) {
            RtsbuildingMod.LOGGER.error("[Workflow] 旧版存档读取失败，已保留原文件且跳过迁移: {}",
                    failed.cause().getMessage());
            return result;
        }
        if (readResult instanceof RtsNbtStore.ReadResult.Missing) return result;
        CompoundTag root = ((RtsNbtStore.ReadResult.Found) readResult).root();
        if (root.isEmpty()) return result;

        CompoundTag players = root.getCompound(KEY_PLAYERS);
        if (players.isEmpty()) return result;

        String playerKey = playerId.toString();
        if (!players.contains(playerKey)) return result;

        CompoundTag playerTag = players.getCompound(playerKey);
        CompoundTag dimensions = playerTag.getCompound(KEY_DIMENSIONS);
        result.putAll(deserializeDimensions(dimensions));

        // 迁移必须先确认新版玩家文件落盘，再清理旧文件；反过来会在第二次写盘失败时丢失唯一副本。
        if (!result.isEmpty()) {
            CompoundTag playerData = new CompoundTag();
            CompoundTag dims = new CompoundTag();
            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> entry : result.entrySet()) {
                CompoundTag slotsTag = entry.getValue().saveToNbt();
                if (slotsTag != null) {
                    dims.put(entry.getKey().location().toString(), slotsTag);
                }
            }
            playerData.put(KEY_DIMENSIONS, dims);
            DataCluster playerCluster = cluster(server, playerId);
            playerCluster.set(WorkflowComponents.FULL_WORKFLOW, playerData);
            if (!playerCluster.flush()) {
                RtsbuildingMod.LOGGER.error(
                        "[Workflow] 玩家 {} 的新版工作流文件写入失败，旧版数据保持不变，等待下次重试",
                        playerId);
                return result;
            }

            players.remove(playerKey);
            CompoundTag migratedLegacyRoot = root.copy();
            migratedLegacyRoot.put(KEY_PLAYERS, players.isEmpty() ? new CompoundTag() : players);
            migratedLegacyRoot.putInt(KEY_DATA_VERSION, DATA_VERSION);
            if (!legacyStore.write(migratedLegacyRoot)) {
                RtsbuildingMod.LOGGER.warn(
                        "[Workflow] 玩家 {} 的新版数据已落盘，但旧版索引清理失败；已保留双副本，需稍后清理旧文件",
                        playerId);
                return result;
            }

            RtsbuildingMod.LOGGER.info("[Workflow] 已迁移玩家 {} 的工作流数据到新版格式", playerId);
        }

        return result;
    }
}
