package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * NBT 文件的原子读写工具——临时文件 + {@link StandardCopyOption#ATOMIC_MOVE} 保证写入安全。
 *
 * <p>封装了两个 Store 类中反复出现的「读压缩 NBT → 写临时文件 → 原子移动」模式，
 * 提供线程安全的文件级 I/O 操作，不含任何业务逻辑。
 *
 * <p>使用示例：
 * <pre>{@code
 * var store = new RtsAtomicNbtStore(server, "rtsbuilding", "session.dat");
 * CompoundTag data = store.read();       // 文件不存在返回空标签，损坏则抛出异常
 * store.write(data);                     // 原子写入
 * }</pre>
 */
public final class RtsAtomicNbtStore implements RtsNbtStore {

    /** 单个 NBT 文件最大允许大小（128 MB） */
    private static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;

    private final Path filePath;
    private final Path tempPath;
    private final String label;

    /**
     * @param server   Minecraft 服务器实例（用于获取存档根路径）
     * @param subDir   存档中的子目录名，如 {@code "rtsbuilding"}
     * @param fileName 文件名，如 {@code "storage_sessions.dat"}
     */
    public RtsAtomicNbtStore(MinecraftServer server, String subDir, String fileName) {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve(subDir);
        this.filePath = dir.resolve(fileName);
        this.tempPath = dir.resolve(fileName + ".tmp");
        this.label = subDir + "/" + fileName;
    }

    /** 同包测试使用的文件级构造器，生产代码仍应通过 MinecraftServer 解析存档路径。 */
    RtsAtomicNbtStore(Path filePath, String label) {
        this.filePath = filePath;
        this.tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        this.label = label;
    }

    /** 从文件读取压缩 NBT，并保留“不存在”和“损坏”的语义差异。 */
    @Override
    public ReadResult readResult() {
        if (!Files.exists(filePath)) {
            return ReadResult.missing();
        }
        if (!Files.isRegularFile(filePath)) {
            IOException cause = new IOException("NBT 路径不是普通文件");
            RtsbuildingMod.LOGGER.error("读取 NBT 文件 {} 失败: {}", filePath, cause.getMessage());
            return ReadResult.failed(cause);
        }
        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.create(MAX_FILE_BYTES));
            if (root == null) {
                IOException cause = new IOException("NBT 根标签为空");
                RtsbuildingMod.LOGGER.error("读取 NBT 文件 {} 失败: {}", filePath, cause.getMessage());
                return ReadResult.failed(cause);
            }
            return ReadResult.found(root);
        } catch (IOException | RuntimeException e) {
            RtsbuildingMod.LOGGER.error("读取 NBT 文件 {} 失败: {}", filePath, e.getMessage());
            return ReadResult.failed(e);
        }
    }

    /**
     * 兼容旧调用的便捷读取入口。
     *
     * <p>文件不存在仍返回空标签；文件存在但损坏时必须抛出异常，绝不能把损坏误报成空存档，
     * 否则调用方随后保存默认值会覆盖仍可人工恢复的原文件。
     */
    public CompoundTag read() {
        return switch (readResult()) {
            case ReadResult.Found found -> found.root();
            case ReadResult.Missing ignored -> new CompoundTag();
            case ReadResult.Failed failed -> throw new IllegalStateException(
                    "读取 NBT 文件失败，拒绝以空数据继续: " + label, failed.cause());
        };
    }

    /**
     * 将 NBT 数据原子写入文件。
     * <p>先写入临时文件，成功后通过 {@link StandardCopyOption#ATOMIC_MOVE} 移动到目标路径，
     * 避免写入过程中崩溃导致文件损坏。如果文件系统不支持原子移动，回退到普通替换。
     *
     * @param tag 要写入的 NBT 数据
     * @return 写入是否成功
     */
    @Override
    public boolean write(CompoundTag tag) {
        try {
            Files.createDirectories(filePath.getParent());
            NbtIo.writeCompressed(tag, tempPath);
            try {
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // 文件系统可能不支持原子移动（如某些网络文件系统），回退到普通移动
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            RtsbuildingMod.LOGGER.error("写入 NBT 文件 {} 失败: {}", filePath, e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    /** 返回目标文件的完整路径（用于日志和诊断）。 */
    public Path path() {
        return filePath;
    }

    /** 返回文件的人类可读标签（用于日志）。 */
    @Override
    public String label() {
        return label;
    }

}
