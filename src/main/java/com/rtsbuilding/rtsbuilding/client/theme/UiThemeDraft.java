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
 * <p>草稿只存在于客户端内存；只有 {@link #snapshot()} 生成的不可变定义才会写入注册表或文件。
 * 修改一个核心令牌时会同步所有使用该令牌的组件色，避免预览与实际面板残留两套颜色。</p>
 */
public final class UiThemeDraft {
    private final UiThemeDefinition source;
    private final String userId;
    private final EnumMap<UiThemeToken, UiColor> tokens = new EnumMap<>(UiThemeToken.class);
    private final EnumMap<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> components =
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
        return this.tokens.get(token);
    }

    public void setColor(UiThemeToken token, UiColor color) {
        if (token == null || color == null) throw new IllegalArgumentException("token/color");
        if (!token.translucentAllowed() && color.alpha() == 0) {
            throw new IllegalArgumentException("critical color cannot be transparent");
        }
        this.tokens.put(token, color);
        for (Map<UiThemeToken, UiColor> family : this.components.values()) {
            if (family.containsKey(token)) family.put(token, color);
        }
    }

    public UiThemeDefinition snapshot() {
        return new UiThemeDefinition(this.userId,
                this.source.id().startsWith("rtsbuilding:")
                        ? this.source.id().substring(this.source.id().indexOf(':') + 1) + " Custom"
                        : this.source.nameKey(),
                this.source.author(), this.source.descriptionKey(), UiThemeRenderMode.PALETTE,
                this.source.textureSet(), true, this.tokens, this.components);
    }
}
