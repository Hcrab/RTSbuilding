package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
 * 工作流条目的世界存档级持久化层。
 *
 * <p>本类只负责把内存中的工作流槽位写成压缩 NBT，以及在玩家登录时读回。
 * 它不恢复放置、挖掘或蓝图执行队列；执行级恢复仍由对应服务决定。这样可以
 * 先追平 main 的数据寿命边界，又避免服务端重启后误改玩家世界。</p>
 */
public final class RtsWorkflowStore {
    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "workflow_data.dat";
    private static final String TEMP_FILE_NAME = "workflow_data.dat.tmp";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_DIMENSIONS = "dimensions";
    private static final int DATA_VERSION = 1;

    private RtsWorkflowStore() {
    }

    public static synchronized void saveAll(
            MinecraftServer server,
            Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> allSlots) {
        if (server == null || allSlots == null) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt(KEY_DATA_VERSION, DATA_VERSION);
        CompoundTag players = new CompoundTag();

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : allSlots.entrySet()) {
            CompoundTag playerTag = savePlayerDimensions(playerEntry.getValue());
            if (!playerTag.isEmpty()) {
                players.put(playerEntry.getKey().toString(), playerTag);
            }
        }

        root.put(KEY_PLAYERS, players);
        writeAll(server, root);
    }

    public static synchronized Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayer(
            MinecraftServer server, UUID playerId) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        if (server == null || playerId == null) {
            return result;
        }

        CompoundTag root = loadAll(server);
        CompoundTag players = root.getCompound(KEY_PLAYERS);
        CompoundTag playerTag = players.getCompound(playerId.toString());
        if (playerTag.isEmpty()) {
            return result;
        }

        CompoundTag dimensions = playerTag.getCompound(KEY_DIMENSIONS);
        for (String dimKey : dimensions.getAllKeys()) {
            ResourceLocation dimLocation = ResourceLocation.tryParse(dimKey);
            if (dimLocation == null) {
                continue;
            }
            CompoundTag slotsTag = dimensions.getCompound(dimKey);
            if (slotsTag.isEmpty()) {
                continue;
            }
            RtsWorkflowSlotManager slots = RtsWorkflowSlotManager.loadFromNbt(slotsTag);
            if (slots.occupiedCount() > 0) {
                result.put(ResourceKey.create(Registries.DIMENSION, dimLocation), slots);
            }
        }
        return result;
    }

    private static CompoundTag savePlayerDimensions(Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimSlots) {
        CompoundTag playerTag = new CompoundTag();
        if (dimSlots == null || dimSlots.isEmpty()) {
            return playerTag;
        }

        CompoundTag dimensions = new CompoundTag();
        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimSlots.entrySet()) {
            RtsWorkflowSlotManager slots = dimEntry.getValue();
            if (slots == null || slots.occupiedCount() == 0) {
                continue;
            }
            dimensions.put(dimEntry.getKey().location().toString(), slots.saveToNbt());
        }
        if (!dimensions.isEmpty()) {
            playerTag.put(KEY_DIMENSIONS, dimensions);
        }
        return playerTag;
    }

    private static CompoundTag loadAll(MinecraftServer server) {
        Path path = storagePath(server);
        if (!Files.isRegularFile(path)) {
            return emptyRoot();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(path.toFile());
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

    private static CompoundTag emptyRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_DATA_VERSION, DATA_VERSION);
        root.put(KEY_PLAYERS, new CompoundTag());
        return root;
    }

    private static void writeAll(MinecraftServer server, CompoundTag root) {
        Path path = storagePath(server);
        Path tempPath = path.resolveSibling(TEMP_FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, tempPath.toFile());
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException deleteIgnored) {
            }
        }
    }

    private static Path storagePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY).resolve(FILE_NAME);
    }
}
