package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 存储会话数据的持久化存储，以世界存档级别的压缩 NBT 文件形式保存。
 *
 * <p>将所有玩家的存储会话序列化为一个压缩 NBT 文件，
 * 存储在存档目录的 {@code rtsbuilding/storage_sessions.dat} 中。
 * 使用临时文件 + 原子移动的方式保证写入安全。
 */
public final class RtsStorageSessionStore {
    /** 存储子目录名称 */
    private static final String DIRECTORY = "rtsbuilding";
    /** 存档文件名 */
    private static final String FILE_NAME = "storage_sessions.dat";
    /** 临时文件名，用于原子写入 */
    private static final String TEMP_FILE_NAME = "storage_sessions.dat.tmp";
    /** NBT 键名：数据版本号 */
    private static final String KEY_DATA_VERSION = "data_version";
    /** NBT 键名：玩家数据映射 */
    private static final String KEY_PLAYERS = "players";
    /** 当前数据版本 */
    private static final int DATA_VERSION = 1;

    /** 工具类，私有构造防止实例化 */
    private RtsStorageSessionStore() {
    }

    /**
     * 加载指定玩家的存储会话数据。
     *
     * @param player 目标玩家
     * @return 该玩家的会话 NBT 数据，如果不存在则返回空标签
     */
    public static synchronized CompoundTag loadSession(ServerPlayer player) {
        if (player == null) {
            return new CompoundTag();
        }
        CompoundTag all = loadAll(player.getServer());
        CompoundTag players = all.getCompound(KEY_PLAYERS);
        CompoundTag session = players.getCompound(player.getUUID().toString());
        return session.isEmpty() ? new CompoundTag() : session.copy();
    }

    /**
     * 保存指定玩家的存储会话数据。
     *
     * @param player  目标玩家
     * @param session 要保存的会话 NBT 数据
     */
    public static synchronized void saveSession(ServerPlayer player, CompoundTag session) {
        if (player == null || session == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CompoundTag all = loadAll(server);
        all.putInt(KEY_DATA_VERSION, DATA_VERSION);
        CompoundTag players = all.getCompound(KEY_PLAYERS);
        players.put(player.getUUID().toString(), session.copy());
        all.put(KEY_PLAYERS, players);
        writeAll(server, all);
    }

    /** 从文件加载所有玩家的会话数据 */
    private static CompoundTag loadAll(MinecraftServer server) {
        if (server == null) {
            return emptyRoot();
        }
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

    /** 创建一个空的根标签，包含默认数据版本和空的玩家映射 */
    private static CompoundTag emptyRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_DATA_VERSION, DATA_VERSION);
        root.put(KEY_PLAYERS, new CompoundTag());
        return root;
    }

    /**
     * 将所有玩家的会话数据写入文件。
     * 使用先写临时文件再原子移动的方式，防止写入过程中崩溃导致数据损坏。
     */
    private static void writeAll(MinecraftServer server, CompoundTag root) {
        Path path = storagePath(server);
        Path tempPath = path.resolveSibling(TEMP_FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, tempPath);
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

    /** 获取存储文件的路径 */
    private static Path storagePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY).resolve(FILE_NAME);
    }
}
