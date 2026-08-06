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

    /**
     * 注册或替换用户命名空间中的可编辑主题。内建 rtsbuilding 命名空间永远不能经此入口覆盖。
     */
    public void registerOrReplaceUser(UiThemeDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (definition.id().startsWith("rtsbuilding:") || !definition.editable()
                || definition.renderMode() != UiThemeRenderMode.PALETTE) {
            throw new IllegalArgumentException("only editable user Palette themes may be replaced");
        }
        definitions.put(definition.id(), definition);
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
