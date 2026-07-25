package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * durable task 根文件专用的严格原子存储。
 *
 * <p>它明确不提供普通移动降级：临时文件和目标文件都必须 force，随后只允许同目录
 * {@code ATOMIC_MOVE}。Windows 无法打开目录句柄时仅忽略明确的“不支持/拒绝访问”，
 * 其他 I/O 错误一律使本次提交失败。</p>
 */
public final class RtsStrictAtomicNbtStore implements RtsNbtStore {
    private static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;

    private final Path filePath;
    private final String label;
    private final AtomicMover mover;
    private final FileForcer fileForcer;
    private final DirectoryForcer directoryForcer;

    public RtsStrictAtomicNbtStore(MinecraftServer server, String subDir, String fileName) {
        this(Objects.requireNonNull(server, "server").getWorldPath(LevelResource.ROOT)
                        .resolve(subDir).resolve(fileName),
                subDir + "/" + fileName,
                RtsStrictAtomicNbtStore::atomicMove,
                RtsStrictAtomicNbtStore::forceFileChannel,
                RtsStrictAtomicNbtStore::forceDirectoryChannel);
    }

    RtsStrictAtomicNbtStore(Path filePath, String label) {
        this(filePath, label, RtsStrictAtomicNbtStore::atomicMove,
                RtsStrictAtomicNbtStore::forceFileChannel,
                RtsStrictAtomicNbtStore::forceDirectoryChannel);
    }

    RtsStrictAtomicNbtStore(Path filePath, String label, AtomicMover mover,
            FileForcer fileForcer, DirectoryForcer directoryForcer) {
        this.filePath = Objects.requireNonNull(filePath, "filePath").toAbsolutePath().normalize();
        this.label = Objects.requireNonNull(label, "label");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.fileForcer = Objects.requireNonNull(fileForcer, "fileForcer");
        this.directoryForcer = Objects.requireNonNull(directoryForcer, "directoryForcer");
    }

    @Override
    public ReadResult readResult() {
        if (!Files.exists(filePath)) return ReadResult.missing();
        if (!Files.isRegularFile(filePath)) {
            return ReadResult.failed(new IOException("NBT 路径不是普通文件: " + filePath));
        }
        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.create(MAX_FILE_BYTES));
            return root == null
                    ? ReadResult.failed(new IOException("NBT 根标签为空: " + filePath))
                    : ReadResult.found(root);
        } catch (IOException | RuntimeException failure) {
            return ReadResult.failed(failure);
        }
    }

    @Override
    public boolean write(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        Path parent = filePath.getParent();
        Path temporary = filePath.resolveSibling(
                filePath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(parent);
            writeAndForce(temporary, tag);
            long compressedBytes = Files.size(temporary);
            if (compressedBytes <= 0L || compressedBytes > MAX_FILE_BYTES) {
                throw new IOException("NBT 压缩文件大小越界: " + compressedBytes);
            }
            // 发布前按与启动相同的 128 MiB accounter 再读一次，拒绝写出无法重载的根。
            if (NbtIo.readCompressed(temporary, NbtAccounter.create(MAX_FILE_BYTES)) == null) {
                throw new IOException("临时 NBT 根标签为空");
            }
            mover.move(temporary, filePath);
            fileForcer.force(filePath);
            forceDirectoryBestEffort(parent, directoryForcer);
            return true;
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.error("严格写入 NBT 文件 {} 失败: {}", filePath, failure.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            return false;
        }
    }

    private static void writeAndForce(Path temporary, CompoundTag tag) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var output = new FilterOutputStream(Channels.newOutputStream(channel)) {
                @Override
                public void close() throws IOException {
                    flush();
                }
            };
            NbtIo.writeCompressed(tag, output);
            output.flush();
            channel.force(true);
        }
    }

    static void forceDirectoryBestEffort(Path directory, DirectoryForcer forcer) throws IOException {
        try {
            forcer.force(directory);
        } catch (AccessDeniedException | UnsupportedOperationException unsupported) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                if (unsupported instanceof IOException io) throw io;
                throw unsupported;
            }
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void forceFileChannel(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectoryChannel(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    @Override
    public Path path() {
        return filePath;
    }

    @Override
    public String label() {
        return label;
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface FileForcer {
        void force(Path file) throws IOException;
    }

    @FunctionalInterface
    interface DirectoryForcer {
        void force(Path directory) throws IOException;
    }
}
