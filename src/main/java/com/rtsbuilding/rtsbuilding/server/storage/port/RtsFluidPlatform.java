package com.rtsbuilding.rtsbuilding.server.storage.port;

import java.util.Objects;

/**
 * 保存当前 Loader 的流体平台桥。
 *
 * <p>该类只拥有安装和读取边界，不包含 NeoForge 实现。Loader 入口必须在任何
 * RTS 流体操作发生前安装一次；重复安装同一实现是安全的，替换成另一实现会失败，
 * 防止测试或重载期间静默混用两个平台后端。</p>
 */
public final class RtsFluidPlatform {
    private static RtsFluidPlatformBridge bridge;

    private RtsFluidPlatform() {
    }

    public static synchronized void install(RtsFluidPlatformBridge implementation) {
        Objects.requireNonNull(implementation, "implementation");
        if (bridge != null && bridge != implementation) {
            throw new IllegalStateException("RTS fluid platform bridge is already installed");
        }
        bridge = implementation;
    }

    public static RtsFluidPlatformBridge bridge() {
        RtsFluidPlatformBridge current = bridge;
        if (current == null) {
            throw new IllegalStateException("RTS fluid platform bridge has not been installed");
        }
        return current;
    }
}
