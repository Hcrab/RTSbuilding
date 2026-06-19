package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流条目在世界存档级别的持久化存储。
 *
 * <p>将所有玩家的工作流槽位管理器以单个压缩 NBT 文件的形式
 * 存储在存档目录（{@code rtsbuilding/workflow_data.dat}）中。
 * 遵循与 {@link RtsStorageSessionStore} 相同的文件 I/O 模式。
 *
 * <p>这是一个独立的存储层——调用方（通常是 {@code RtsWorkflowEngine}）
 * 在内存中管理槽位，并在生命周期边界调用
 * {@link #saveAll(MinecraftServer, Map)} / {@link #loadPlayer(MinecraftServer, UUID)}。
 */
public final class RtsWorkflowStore {
    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "workflow_data.dat";
    private static final String TEMP_FILE_NAME = "workflow_data.dat.tmp";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final int DATA_VERSION = 1;

    // 每个玩家数据的内部键名
    private static final String KEY_NEXT_ID = "next_id";
    /** NBT 键名：维度数据映射 */
    private static final String KEY_DIMENSIONS = "dimensions";
    /** NBT 键名：槽位管理器数据 */
    private static final String KEY_SLOT_MANAGER = "slots";

    private RtsWorkflowStore() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  保存 — 持久化所有玩家的工作流数据
    // ──────────────────────────────────────────────────────────────────

    /**
     * 保存所有玩家在所有维度上的工作流槽位管理器到世界存档文件。
     *
     * @param server   Minecraft 服务器实例（用于获取存档路径）
     * @param allSlots 玩家 UUID → 维度 → 槽位管理器的映射
     */
    public static synchronized void saveAll(MinecraftServer server,
                                            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> allSlots) {
        if (server == null || allSlots == null) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt(KEY_DATA_VERSION, DATA_VERSION);

        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : allSlots.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimSlots = playerEntry.getValue();
            if (dimSlots == null || dimSlots.isEmpty()) {
                continue;
            }

            CompoundTag playerTag = new CompoundTag();
            CompoundTag dimensions = new CompoundTag();
            boolean hasData = false;

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimSlots.entrySet()) {
                ResourceKey<Level> dimension = dimEntry.getKey();
                RtsWorkflowSlotManager slots = dimEntry.getValue();
                if (slots == null || slots.occupiedCount() == 0) {
                    continue;
                }
                CompoundTag slotsTag = slots.saveToNbt();
                if (slotsTag != null) {
                    dimensions.put(dimension.location().toString(), slotsTag);
                    hasData = true;
                }
            }

            if (hasData) {
                playerTag.put(KEY_DIMENSIONS, dimensions);
                players.put(playerId.toString(), playerTag);
            }
        }

        root.put(KEY_PLAYERS, players);
        writeAll(server, root);
    }

    // ──────────────────────────────────────────────────────────────────
    //  加载 — 恢复单个玩家的工作流数据
    // ──────────────────────────────────────────────────────────────────

    /**
     * 从世界存档文件中加载指定玩家的工作流槽位管理器。
     *
     * @param server   Minecraft 服务器实例
     * @param playerId 玩家的 UUID
     * @return 维度 → 槽位管理器的映射（没有保存的数据则返回空映射）
     */
    public static synchronized Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayer(
            MinecraftServer server, UUID playerId) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        if (server == null || playerId == null) {
            return result;
        }

        CompoundTag root = loadAll(server);
        if (root.isEmpty()) {
            return result;
        }

        CompoundTag players = root.getCompound(KEY_PLAYERS);
        if (players.isEmpty()) {
            return result;
        }

        String playerKey = playerId.toString();
        if (!players.contains(playerKey)) {
            return result;
        }

        CompoundTag playerTag = players.getCompound(playerKey);
        CompoundTag dimensions = playerTag.getCompound(KEY_DIMENSIONS);
        for (String dimKey : dimensions.getAllKeys()) {
            ResourceLocation dimLocation = ResourceLocation.tryParse(dimKey);
            if (dimLocation == null) {
                continue;
            }
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
    //  文件 I/O
    // ──────────────────────────────────────────────────────────────────

    private static final String KEY_REGISTRY = "minecraft:dimension_type";

    /**
     * 从维度字符串表示解析出对应的 {@link ResourceKey}。
     */
    private static ResourceKey<Level> parseDimensionKey(String dimKey) {
        ResourceLocation location = ResourceLocation.tryParse(dimKey);
        if (location == null) {
            return Level.OVERWORLD;
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }

    /** 从世界存档文件加载所有工作流数据 */
    private static CompoundTag loadAll(MinecraftServer server) {
        Path path = storagePath(server);
        if (!Files.isRegularFile(path)) {
            return emptyRoot();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            if (root == null) {
                return emptyRoot();
            }
            if (!root.contains(KEY_PLAYERS)) {
                root.put(KEY_PLAYERS, new CompoundTag());
            }
            return root;
        } catch (IOException | RuntimeException ignored) {
            return emptyRoot();
        }
    }

    /** 创建空的根标签，包含默认数据版本和空的玩家映射 */
    private static CompoundTag emptyRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_DATA_VERSION, DATA_VERSION);
        root.put(KEY_PLAYERS, new CompoundTag());
        return root;
    }

    /**
     * 将所有工作流数据写入文件。
     * 使用先写临时文件再原子移动的方式，防止写入过程中崩溃导致数据损坏。
     */
    private static void writeAll(MinecraftServer server, CompoundTag root) {
        Path path = storagePath(server);
        Path tempPath = path.resolveSibling(TEMP_FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, tempPath);
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored1) {
            }
        }
    }

    /** 获取工作流数据文件的存储路径 */
    private static Path storagePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY).resolve(FILE_NAME);
    }
}
