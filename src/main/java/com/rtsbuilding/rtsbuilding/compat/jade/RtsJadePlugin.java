package com.rtsbuilding.rtsbuilding.compat.jade;

import net.minecraftforge.fml.common.Loader;

/**
 * Minecraft 1.12.2 上的 Jade/HWYLA 能力探测器。
 *
 * <p>Snownee 的官方 Jade 1.12 分支是依附 HWYLA 1.8 的信息扩展包，只公开
 * {@code IWailaRegistrar} 提示提供者接口；它没有现代 Jade 的射线替换、渲染前回调或
 * 快捷键桥。因此本类不声明伪造的 Waila 插件，也不硬链接任一可选模组，只报告环境并让
 * RTS Jade 专用功能安全关闭。普通 Jade/HWYLA 的第一人称提示功能完全由其自身负责。
 */
public final class RtsJadePlugin {
    private static final String LEGACY_JADE_MOD_ID = "jade";
    private static final String HWYLA_MOD_ID = "waila";
    private static final String HWYLA_API_CLASS = "mcp.mobius.waila.api.IWailaPlugin";
    private static final String MODERN_RAY_TRACE_API_CLASS =
            "snownee.jade.api.callback.JadeRayTraceCallback";

    private RtsJadePlugin() {
    }

    public static CompatibilityStatus detectCompatibility() {
        boolean jadeLoaded = isModLoaded(LEGACY_JADE_MOD_ID);
        boolean hwylaLoaded = isModLoaded(HWYLA_MOD_ID) && isClassPresent(HWYLA_API_CLASS);
        boolean modernRayTraceApi = isClassPresent(MODERN_RAY_TRACE_API_CLASS);
        if (modernRayTraceApi) {
            return CompatibilityStatus.UNKNOWN_MODERN_API;
        }
        if (jadeLoaded && hwylaLoaded) {
            return CompatibilityStatus.LEGACY_JADE_ON_HWYLA;
        }
        if (jadeLoaded) {
            return CompatibilityStatus.BROKEN_LEGACY_JADE_INSTALL;
        }
        if (hwylaLoaded) {
            return CompatibilityStatus.HWYLA_ONLY;
        }
        return CompatibilityStatus.NOT_INSTALLED;
    }

    /** 1.12 官方 API 无法承载 mainline 的 RTS 射线/布局桥，始终明确降级。 */
    public static boolean supportsRtsOverlayBridge() {
        return false;
    }

    private static boolean isModLoaded(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (RuntimeException | LinkageError loaderUnavailable) {
            return false;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            ClassLoader loader = RtsJadePlugin.class.getClassLoader();
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    public enum CompatibilityStatus {
        NOT_INSTALLED,
        HWYLA_ONLY,
        LEGACY_JADE_ON_HWYLA,
        BROKEN_LEGACY_JADE_INSTALL,
        UNKNOWN_MODERN_API
    }
}
