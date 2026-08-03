package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册顺序稳定的已验证主题仓库；不读取磁盘，也不决定渲染轨道。 */
public final class UiThemeRegistry {
    private final Map<String, UiThemeDefinition> definitions =
            new LinkedHashMap<String, UiThemeDefinition>();

    public void register(UiThemeDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (definitions.put(definition.id(), definition) != null) {
            throw new IllegalArgumentException("duplicate theme id: " + definition.id());
        }
    }

    public UiThemeDefinition require(String id) {
        UiThemeDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("unknown theme id: " + id);
        return definition;
    }

    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    public List<UiThemeDefinition> snapshot() {
        return Collections.unmodifiableList(new ArrayList<UiThemeDefinition>(definitions.values()));
    }
}
