package com.rtsbuilding.rtsbuilding.server.data;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class RtsStorageSessionStore {
    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "storage_sessions.dat";
    private static final String TEMP_FILE_NAME = "storage_sessions.dat.tmp";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final int DATA_VERSION = 1;

    private RtsStorageSessionStore() {
    }

    public static synchronized CompoundTag loadSession(ServerPlayer player) {
        if (player == null) {
            return new CompoundTag();
        }
        CompoundTag all = loadAll(player.getServer());
        CompoundTag players = all.getCompound(KEY_PLAYERS);
        CompoundTag session = players.getCompound(player.getUUID().toString());
        return session.isEmpty() ? new CompoundTag() : session.copy();
    }

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

    private static CompoundTag loadAll(MinecraftServer server) {
        if (server == null) {
            return emptyRoot();
        }
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
