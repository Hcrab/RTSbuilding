package com.rtsbuilding.rtsbuilding.client.compat;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 为入门提醒生成稳定的“存档或服务器”作用域键。
 *
 * <p>这里只负责身份归一化，不读取 UI 状态，也不执行磁盘写入。单人存档使用世界目录，
 * 避免同名存档互相影响；多人服务器使用连接地址。维度不属于独立作用域，因此不会生成
 * 维度级回退键。</p>
 */
final class RtsIntroReminderScope {
    private RtsIntroReminderScope() {
    }

    static String singleplayerKey(Path worldRoot) {
        if (worldRoot == null) {
            return "";
        }
        String normalized = worldRoot.toAbsolutePath().normalize().toString().trim();
        return normalized.isEmpty() ? "" : "singleplayer:" + normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 从客户端游戏目录和集成服的存档文件夹名恢复真实世界路径。
     *
     * <p>客户端 {@code WorldClient} 使用的 SaveHandlerMP 没有本地世界目录，不能从
     * {@code world.getSaveHandler().getWorldDirectory()} 取路径；权威文件夹名属于集成服。</p>
     */
    static String singleplayerKey(Path gameDirectory, String folderName) {
        if (gameDirectory == null || folderName == null || folderName.trim().isEmpty()) {
            return "";
        }
        Path savesRoot = gameDirectory.toAbsolutePath().normalize().resolve("saves").normalize();
        Path worldRoot = savesRoot.resolve(folderName).normalize();
        if (!worldRoot.startsWith(savesRoot)) {
            return "";
        }
        return singleplayerKey(worldRoot);
    }

    static String serverKey(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }
        return "server:" + address.trim().toLowerCase(Locale.ROOT);
    }
}
