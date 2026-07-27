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

    static String serverKey(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }
        return "server:" + address.trim().toLowerCase(Locale.ROOT);
    }
}
