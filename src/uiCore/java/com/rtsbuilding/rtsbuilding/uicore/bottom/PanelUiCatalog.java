package com.rtsbuilding.rtsbuilding.uicore.bottom;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiOrderedRegistry;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;

import java.util.Collections;
import java.util.List;

/**
 * 内建底部面板入口的 internal/unstable 注册目录。
 *
 * <p>新增入口必须先进入这里，再由生产布局消费；不允许在布局或输入路由里
 * 另写一份页签顺序。公开附属 API 前仍可调整包名和兼容承诺。</p>
 */
public final class PanelUiCatalog {
    private static final List<UiRegistration<PanelUiContribution>> REGISTRATIONS = create();

    private PanelUiCatalog() {
    }

    public static List<UiRegistration<PanelUiContribution>> registrations() {
        return REGISTRATIONS;
    }

    private static List<UiRegistration<PanelUiContribution>> create() {
        UiOrderedRegistry<PanelUiContribution> registry = new UiOrderedRegistry<>();
        register(registry, BottomBarUiTab.CREATIVE, "a_catalog", 0,
                "screen.rtsbuilding.bottom.creative",
                PanelUiContribution.Access.CREATIVE_PLAYER);
        register(registry, BottomBarUiTab.STORAGE, "b_storage", 0,
                "screen.rtsbuilding.bottom.storage",
                PanelUiContribution.Access.ALWAYS);
        register(registry, BottomBarUiTab.BLUEPRINTS, "c_blueprints", 0,
                "screen.rtsbuilding.bottom.blueprints",
                PanelUiContribution.Access.BLUEPRINT_PLUGIN);
        return registry.snapshot();
    }

    private static void register(UiOrderedRegistry<PanelUiContribution> registry,
                                 BottomBarUiTab tab, String group, int weight,
                                 String labelKey, PanelUiContribution.Access access) {
        registry.register(new UiRegistration<>(
                "rtsbuilding:panel." + tab.name().toLowerCase(java.util.Locale.ROOT),
                group,
                weight,
                Collections.emptyList(),
                Collections.emptyList(),
                new PanelUiContribution(tab, labelKey, UiControlRole.MODE, access)));
    }
}
