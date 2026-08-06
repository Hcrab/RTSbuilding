package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.theme.LegacyTextureSet;
import com.rtsbuilding.rtsbuilding.client.theme.ThemedStateTextureResolver;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalSortField;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.resources.ResourceLocation;

/**
 * 合成终端排序按钮的双轨纹理目录。
 *
 * <p>四个语义各自对应一张已经离线合成完毕的 24×24 PNG。生产端只选择完整纹理并
 * 以 1:1 绘制；不会在运行时拼字符、叠图标或缩放素材。Palette 只替换五种精确
 * 索引色，不改变 v2 图标的像素排布。</p>
 */
final class CraftTerminalSortButtonTextures {
    private static final Entry NAME = entry("terminal_sort_name.png");
    private static final Entry QUANTITY = entry("terminal_sort_quantity.png");
    private static final Entry ASCENDING = entry("terminal_sort_ascending.png");
    private static final Entry DESCENDING = entry("terminal_sort_descending.png");

    private CraftTerminalSortButtonTextures() {
    }

    static ResourceLocation resolveField(CraftTerminalSortField field, UiTextureState state) {
        if (field == null) {
            throw new IllegalArgumentException("sort field must not be null");
        }
        return resolve(field == CraftTerminalSortField.NAME ? NAME : QUANTITY, state);
    }

    static ResourceLocation resolveDirection(boolean ascending, UiTextureState state) {
        return resolve(ascending ? ASCENDING : DESCENDING, state);
    }

    private static ResourceLocation resolve(Entry entry, UiTextureState state) {
        return ThemedStateTextureResolver.resolve(
                entry.legacy,
                entry.source,
                state,
                UiIndexedTextureSpec.V2_TERMINAL_SORT_BUTTON);
    }

    private static Entry entry(String name) {
        ResourceLocation source = new ResourceLocation(
                "rtsbuilding", "textures/gui/ui/" + name);
        return new Entry(source, new LegacyTextureSet(source, source, source, source));
    }

    private static final class Entry {
        private final ResourceLocation source;
        private final LegacyTextureSet legacy;

        private Entry(ResourceLocation source, LegacyTextureSet legacy) {
            this.source = source;
            this.legacy = legacy;
        }
    }
}
