package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Forge 1.12.2 压缩 NBT 的有界文件适配层。
 *
 * <p>1.12.2 的 {@link CompressedStreamTools} 没有新版本的 Path 与 NBT accounter 重载，
 * 因此先限制压缩文件大小，再通过流读取。这里不负责原子替换或备份策略。
 */
final class RtsCompressedNbtIo {
    static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;

    private RtsCompressedNbtIo() {
    }

    static NBTTagCompound read(Path path) throws IOException {
        long bytes = Files.size(path);
        if (bytes <= 0L || bytes > MAX_FILE_BYTES) {
            throw new IOException("NBT 压缩文件大小越界: " + bytes + " (" + path + ")");
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    static void write(Path path, NBTTagCompound tag) throws IOException {
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {
            CompressedStreamTools.writeCompressed(tag, output);
        }
    }
}
