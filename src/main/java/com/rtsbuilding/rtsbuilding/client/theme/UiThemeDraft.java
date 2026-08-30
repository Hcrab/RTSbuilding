package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;

import java.util.EnumMap;
import java.util.Map;

/**
 * 主题编辑窗口持有的可变草稿。
 *
 * <p>它只在内存中修改核心语义色；每次修改会同步同名组件令牌，确保“全局颜色”不会留下某个
 * 工作流或终端仍使用旧色。只有 {@link #snapshot()} 产生的不可变定义可以进入注册表和磁盘。</p>
 */
public final class UiThemeDraft {
    private final UiThemeDefinition source;
    private final String userId;
    private final EnumMap<UiThemeToken, UiColor> tokens = new EnumMap<>(UiThemeToken.class);
    private final EnumMap<UiThemeCoverageCatalog.ComponentFamily,
            Map<UiThemeToken, UiColor>> components =
            new EnumMap<>(UiThemeCoverageCatalog.ComponentFamily.class);

    public UiThemeDraft(UiThemeDefinition source) {
        if (source == null || source.renderMode() != UiThemeRenderMode.PALETTE) {
            throw new IllegalArgumentException("only Palette themes are editable");
        }
        this.source = source;
        this.userId = source.id().startsWith("rtsbuilding:")
                ? "user:" + source.id().substring(source.id().indexOf(':') + 1) + "_custom"
                : source.id();
        this.tokens.putAll(source.tokens());
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            EnumMap<UiThemeToken, UiColor> copy = new EnumMap<>(UiThemeToken.class);
            copy.putAll(source.components().get(family));
            this.components.put(family, copy);
        }
    }

    public UiColor color(UiThemeToken token) {
        return tokens.get(token);
    }

    public void setColor(UiThemeToken token, UiColor color) {
        if (token == null || color == null) throw new IllegalArgumentException("token/color");
        if (!token.translucentAllowed() && color.alpha() == 0) {
            throw new IllegalArgumentException("critical color cannot be transparent");
        }
        tokens.put(token, color);
        for (Map<UiThemeToken, UiColor> family : components.values()) {
            if (family.containsKey(token)) family.put(token, color);
        }
    }

    public String userId() {
        return userId;
    }

    public UiThemeDefinition snapshot() {
        return new UiThemeDefinition(userId,
                source.id().startsWith("rtsbuilding:")
                        ? source.id().substring(source.id().indexOf(':') + 1) + " Custom"
                        : source.nameKey(),
                source.author(), source.descriptionKey(), UiThemeRenderMode.PALETTE,
                source.textureSet(), true, tokens, components);
    }
}
