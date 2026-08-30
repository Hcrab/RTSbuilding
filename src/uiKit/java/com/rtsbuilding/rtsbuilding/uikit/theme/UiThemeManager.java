package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理当前主题与监听器的纯 Java 状态机。
 *
 * <p>文件导入必须先在外部完整解析和验证，再调用 {@link #activate(String)}；本类不会用
 * Legacy 补齐损坏主题，也不会把主题选择传播到服务器。</p>
 */
public final class UiThemeManager {
    public interface Listener {
        void onThemeChanged(UiThemeDefinition previous, UiThemeDefinition current);
    }

    private final UiThemeRegistry registry;
    private final String fallbackId;
    private final List<Listener> listeners = new ArrayList<Listener>();
    private UiThemeDefinition active;

    public UiThemeManager(UiThemeRegistry registry, String fallbackId) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        this.registry = registry;
        this.fallbackId = fallbackId;
        this.active = registry.require(fallbackId);
    }

    public UiThemeDefinition active() {
        return active;
    }

    public void activate(String id) {
        UiThemeDefinition next = registry.require(id);
        if (next == active) return;
        UiThemeDefinition previous = active;
        active = next;
        for (Listener listener : new ArrayList<Listener>(listeners)) {
            listener.onThemeChanged(previous, next);
        }
    }

    public void fallBackToLegacy() {
        activate(fallbackId);
    }

    public void addListener(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
