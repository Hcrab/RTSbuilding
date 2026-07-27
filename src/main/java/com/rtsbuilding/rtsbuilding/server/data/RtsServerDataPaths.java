package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 1.12.2 服务端存档路径适配层。
 *
 * <p>这里只负责把当前存档的根目录转换成 {@link Path}；它不决定任何 RTS 子目录或文件名。
 * 将版本相关调用集中在这里，避免数据层各处依赖新版本的 {@code LevelResource} API。
 */
final class RtsServerDataPaths {
    private RtsServerDataPaths() {
    }

    static Path worldRoot(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .getEntityWorld()
                .getSaveHandler()
                .getWorldDirectory()
                .toPath();
    }
}
