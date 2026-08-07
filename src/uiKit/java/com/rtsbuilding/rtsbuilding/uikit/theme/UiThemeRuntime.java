package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 客户端和无头预览共用的活动主题入口。
 *
 * <p>这里只持有纯 Java 状态；Minecraft 资源重载、动态纹理和配置持久化由 client 适配层监听。</p>
 */
public final class UiThemeRuntime {
    private static final UiThemeRegistry REGISTRY = UiThemeBuiltins.createRegistry();
    private static final UiThemeManager MANAGER =
            new UiThemeManager(REGISTRY, UiThemeBuiltins.LEGACY_ID);

    public static UiThemeRegistry registry() {
        return REGISTRY;
    }

    public static UiThemeManager manager() {
        return MANAGER;
    }

    public static UiColor color(UiThemeToken token) {
        return MANAGER.active().color(token);
    }

    private UiThemeRuntime() {
    }
}
