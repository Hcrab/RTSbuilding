package com.rtsbuilding.rtsbuilding.uicore.topbar;

import com.rtsbuilding.rtsbuilding.uicore.registry.UiOrderedRegistry;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内建顶栏贡献的 internal/unstable 注册目录。
 *
 * <p>目录只公开平台无关的稳定 ID、区域、权重和语义按钮值；可见/可用/选中状态仍由
 * 当前平台快照供应。它不是已承诺二进制兼容的公开 SPI。</p>
 */
public final class TopBarUiCatalog {
    private static final List<UiRegistration<TopBarUiContribution>> REGISTRATIONS = create();

    public static List<UiRegistration<TopBarUiContribution>> registrations() {
        return REGISTRATIONS;
    }

    public static List<TopBarUiButtonId> orderedButtonIds() {
        List<TopBarUiButtonId> result = new ArrayList<TopBarUiButtonId>(REGISTRATIONS.size());
        for (UiRegistration<TopBarUiContribution> registration : REGISTRATIONS) {
            result.add(registration.getValue().getButtonId());
        }
        return Collections.unmodifiableList(result);
    }

    private static List<UiRegistration<TopBarUiContribution>> create() {
        UiOrderedRegistry<TopBarUiContribution> registry = new UiOrderedRegistry<TopBarUiContribution>();
        register(registry, TopBarUiButtonId.INTERACT, "a_mode", 0, UiControlRole.MODE, false);
        register(registry, TopBarUiButtonId.LINK, "a_mode", 10, UiControlRole.MODE, false);
        register(registry, TopBarUiButtonId.FUNNEL, "a_mode", 20, UiControlRole.MODE, false);
        register(registry, TopBarUiButtonId.ROTATE, "a_mode", 30, UiControlRole.MODE, false);
        register(registry, TopBarUiButtonId.QUICK_BUILD, "b_action", 0, UiControlRole.TOGGLE, true);
        register(registry, TopBarUiButtonId.QUEST_DETECT, "b_action", 10, UiControlRole.TOGGLE, false);
        register(registry, TopBarUiButtonId.CHUNK_VIEW, "b_action", 20, UiControlRole.TOGGLE, false);
        register(registry, TopBarUiButtonId.RANGE_CULLING, "b_action", 30, UiControlRole.TOGGLE, true);
        register(registry, TopBarUiButtonId.GUIDE, "b_action", 40, UiControlRole.TOGGLE, false);
        register(registry, TopBarUiButtonId.DEVELOPER, "b_action", 50, UiControlRole.COMMAND, false);
        register(registry, TopBarUiButtonId.GEAR, "c_right", 0, UiControlRole.TOGGLE, false);
        return registry.snapshot();
    }

    public static TopBarUiContribution contribution(TopBarUiButtonId id) {
        if (id == null) return null;
        for (UiRegistration<TopBarUiContribution> registration : REGISTRATIONS) {
            if (registration.getValue().getButtonId() == id) return registration.getValue();
        }
        return null;
    }

    private static void register(UiOrderedRegistry<TopBarUiContribution> registry,
                                 TopBarUiButtonId id, String group, int weight,
                                 UiControlRole role, boolean pluginGated) {
        registry.register(new UiRegistration<TopBarUiContribution>(
                "rtsbuilding:topbar." + id.name().toLowerCase(java.util.Locale.ROOT),
                group, weight, Collections.<String>emptyList(),
                Collections.<String>emptyList(), TopBarUiContribution.builtIn(id, role, pluginGated)));
    }

    private TopBarUiCatalog() {
    }
}
