package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 保持注册顺序的主题集合；活动主题只能引用已注册的稳定 ID。 */
public final class UiThemePalette {
    private final Map<String, UiThemeTokens> themes = new LinkedHashMap<String, UiThemeTokens>();
    private String activeId;

    public void register(UiThemeTokens theme) {
        if (theme == null) {
            throw new IllegalArgumentException("theme must not be null");
        }
        if (themes.put(theme.getId(), theme) != null) {
            throw new IllegalArgumentException("duplicate theme id: " + theme.getId());
        }
        if (activeId == null) {
            activeId = theme.getId();
        }
    }

    public void activate(String id) {
        if (!themes.containsKey(id)) {
            throw new IllegalArgumentException("unknown theme id: " + id);
        }
        activeId = id;
    }

    public UiThemeTokens active() {
        if (activeId == null) {
            throw new IllegalStateException("no themes registered");
        }
        return themes.get(activeId);
    }

    public List<UiThemeTokens> snapshot() {
        return Collections.unmodifiableList(new ArrayList<UiThemeTokens>(themes.values()));
    }
}
