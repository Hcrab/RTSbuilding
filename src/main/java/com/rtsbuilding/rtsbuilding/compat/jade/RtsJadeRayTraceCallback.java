package com.rtsbuilding.rtsbuilding.compat.jade;

/**
 * 保留 mainline 类名作为迁移边界，但 1.12.2 不注册射线回调。
 *
 * <p>官方 Jade 1.12/HWYLA API 没有等价的“替换当前命中目标”回调。后续若决定为 HWYLA
 * 编写经过实机验证的 Mixin 桥，应在独立可选模块中实现，而不是让基础模组在缺少依赖时
 * 冒充完整兼容。
 */
public final class RtsJadeRayTraceCallback {
    private RtsJadeRayTraceCallback() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static String getUnavailableReason() {
        return "Jade 1.12 is a HWYLA addon and exposes no modern ray-trace callback";
    }
}
