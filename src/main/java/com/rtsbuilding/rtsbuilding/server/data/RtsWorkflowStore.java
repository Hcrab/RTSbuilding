package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 玩家工作流的逐玩家持久化与旧全量文件迁移。1.12.2 使用整数维度 ID。 */
public final class RtsWorkflowStore {
    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "workflow_data.dat";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_DIMENSIONS = "dimensions";
    private static final int DATA_VERSION = 1;

    private RtsWorkflowStore() {
    }

    public static void saveAll(MinecraftServer server,
            Map<UUID, Map<Integer, RtsWorkflowSlotManager>> allSlots) {
        if (server == null || allSlots == null) return;
        for (Map.Entry<UUID, Map<Integer, RtsWorkflowSlotManager>> playerEntry : allSlots.entrySet()) {
            Map<Integer, RtsWorkflowSlotManager> dimSlots = playerEntry.getValue();
            if (dimSlots == null || dimSlots.isEmpty()) continue;

            NBTTagCompound dimensions = new NBTTagCompound();
            boolean hasData = false;
            for (Map.Entry<Integer, RtsWorkflowSlotManager> dimEntry : dimSlots.entrySet()) {
                RtsWorkflowSlotManager slots = dimEntry.getValue();
                if (slots == null || slots.occupiedCount() == 0) continue;
                NBTTagCompound slotsTag = slots.saveToNbt();
                if (slotsTag != null && !com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(slotsTag)) {
                    dimensions.setTag(dimensionName(dimEntry.getKey()), slotsTag);
                    hasData = true;
                }
            }
            if (hasData) {
                NBTTagCompound playerData = new NBTTagCompound();
                playerData.setTag(KEY_DIMENSIONS, dimensions);
                cluster(server, playerEntry.getKey()).set(WorkflowComponents.FULL_WORKFLOW, playerData);
            }
        }
        SaveScheduler.INSTANCE.flushAll();
    }

    public static Map<Integer, RtsWorkflowSlotManager> loadPlayer(
            MinecraftServer server, UUID playerId) {
        DataCluster cluster = cluster(server, playerId);
        NBTTagCompound root = cluster.get(WorkflowComponents.FULL_WORKFLOW);
        if (!com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(root) && root.hasKey(KEY_DIMENSIONS)) {
            return deserializeDimensions(root.getCompoundTag(KEY_DIMENSIONS));
        }
        return loadPlayerLegacy(server, playerId);
    }

    private static DataCluster cluster(MinecraftServer server, UUID playerId) {
        return SaveScheduler.INSTANCE.dataCluster(server, playerId, "workflow");
    }

    private static Map<Integer, RtsWorkflowSlotManager> deserializeDimensions(NBTTagCompound dimensions) {
        Map<Integer, RtsWorkflowSlotManager> result = new HashMap<Integer, RtsWorkflowSlotManager>();
        for (String dimKey : dimensions.func_150296_c()) {
            Integer dimension = parseDimension(dimKey);
            if (dimension == null) continue;
            NBTTagCompound slotsTag = dimensions.getCompoundTag(dimKey);
            if (!com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(slotsTag)) {
                RtsWorkflowSlotManager slots = RtsWorkflowSlotManager.loadFromNbt(slotsTag);
                if (slots.occupiedCount() > 0) result.put(dimension, slots);
            }
        }
        return result;
    }

    private static Map<Integer, RtsWorkflowSlotManager> loadPlayerLegacy(
            MinecraftServer server, UUID playerId) {
        Map<Integer, RtsWorkflowSlotManager> result = new HashMap<Integer, RtsWorkflowSlotManager>();
        if (server == null || playerId == null) return result;

        RtsAtomicNbtStore legacyStore = new RtsAtomicNbtStore(server, DIRECTORY, FILE_NAME);
        RtsNbtStore.ReadResult readResult = legacyStore.readResult();
        if (readResult instanceof RtsNbtStore.ReadResult.Failed) {
            Throwable cause = ((RtsNbtStore.ReadResult.Failed) readResult).cause();
            RtsbuildingMod.LOGGER.error("[Workflow] 旧版存档读取失败，保留原文件并跳过迁移: {}",
                    cause.getMessage());
            return result;
        }
        if (readResult instanceof RtsNbtStore.ReadResult.Missing) return result;
        NBTTagCompound root = ((RtsNbtStore.ReadResult.Found) readResult).root();
        NBTTagCompound players = root.getCompoundTag(KEY_PLAYERS);
        String playerKey = playerId.toString();
        if (com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(root) || com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.isEmpty(players) || !players.hasKey(playerKey)) return result;

        result.putAll(deserializeDimensions(players.getCompoundTag(playerKey)
                .getCompoundTag(KEY_DIMENSIONS)));
        if (result.isEmpty()) return result;

        NBTTagCompound playerData = new NBTTagCompound();
        NBTTagCompound dims = new NBTTagCompound();
        for (Map.Entry<Integer, RtsWorkflowSlotManager> entry : result.entrySet()) {
            NBTTagCompound slotsTag = entry.getValue().saveToNbt();
            if (slotsTag != null) dims.setTag(dimensionName(entry.getKey()), slotsTag);
        }
        playerData.setTag(KEY_DIMENSIONS, dims);
        DataCluster playerCluster = cluster(server, playerId);
        playerCluster.set(WorkflowComponents.FULL_WORKFLOW, playerData);
        if (!playerCluster.flush()) {
            RtsbuildingMod.LOGGER.error("[Workflow] 玩家 {} 的新工作流文件写入失败，保留旧数据等待重试",
                    playerId);
            return result;
        }

        players.removeTag(playerKey);
        NBTTagCompound migratedRoot = com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(root);
        migratedRoot.setTag(KEY_PLAYERS, players);
        migratedRoot.setInteger(KEY_DATA_VERSION, DATA_VERSION);
        if (!legacyStore.write(migratedRoot)) {
            RtsbuildingMod.LOGGER.warn("[Workflow] 玩家 {} 的新数据已落盘，但旧索引清理失败；保留双副本",
                    playerId);
        } else {
            RtsbuildingMod.LOGGER.info("[Workflow] 已迁移玩家 {} 的工作流数据", playerId);
        }
        return result;
    }

    private static Integer parseDimension(String value) {
        if ("minecraft:overworld".equals(value)) return 0;
        if ("minecraft:the_nether".equals(value)) return -1;
        if ("minecraft:the_end".equals(value)) return 1;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String dimensionName(int dimension) {
        if (dimension == 0) return "minecraft:overworld";
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return Integer.toString(dimension);
    }
}
