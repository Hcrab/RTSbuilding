package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.client.theme.LegacyTextureSet;
import com.rtsbuilding.rtsbuilding.client.theme.PaletteTextureCatalog;
import com.rtsbuilding.rtsbuilding.client.theme.ThemedStateTextureResolver;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Quick Build 正式形状图标与选择指示器的唯一生产目录。
 *
 * <p>这里只描述资源位置和精灵图尺寸，不创建控件、不判断选中状态，也不执行绘制。
 * 将资源映射从控件 owner 中独立出来后，新增形状时不会再在面板、输入和 renderer
 * 各维护一份顺序数组。</p>
 */
final class QuickBuildIconCatalog {
    static final ResourceLocation SELECTION_DOT =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/general/mode_button.png");
    static final int PR133_ICON_SIZE = 24;
    static final int MODE_SHEET_W = 512;
    static final int MODE_STATE_H = 512;
    static final int MODE_SHEET_H = MODE_STATE_H * 3;

    private QuickBuildIconCatalog() {}

    static WindowButton.StateTextureProvider shapeProvider(QuickBuildUiShape shape) {
        Entry entry = entry(shapeKey(shape));
        return entry::resolve;
    }

    static ResourceLocation convenienceTexture(QuickBuildUiConvenienceTool tool,
                                               UiTextureState state) {
        String key = switch (tool) {
            case REPEAT_BOX -> "cube";
            case CHUNK_QUARRY -> "smart_break/stair";
            case TREE_FELL -> "smart_break/tree";
        };
        return entry(key).resolve(state);
    }

    static ResourceLocation smartFillTexture(UiTextureState state) {
        return entry("fill_water/cave").resolve(state);
    }

    private static String shapeKey(QuickBuildUiShape shape) {
        return switch (shape) {
            case CHAIN -> "chain";
            case BLOCK -> "single";
            case LINE -> "line";
            case SQUARE -> "surface";
            case WALL -> "wall";
            case CIRCLE -> "round";
            case CYLINDER -> "cylinder";
            case BALL -> "ball";
            case BOX -> "cube";
        };
    }

    private static Entry entry(String key) {
        String base = "rtsbuilding:textures/gui/quickbuild_pr133/" + key;
        LegacyTextureSet legacy = new LegacyTextureSet(
                ResourceLocation.tryParse(base + "_inactive.png"),
                ResourceLocation.tryParse(base + "_hover.png"),
                ResourceLocation.tryParse(base + "_active.png"),
                ResourceLocation.tryParse(base + "_pressed.png"));
        return new Entry(legacy, PaletteTextureCatalog.quickBuild(key));
    }

    private static final class Entry {
        private final LegacyTextureSet legacy;
        private final ResourceLocation palette;

        private Entry(LegacyTextureSet legacy, ResourceLocation palette) {
            this.legacy = legacy;
            this.palette = palette;
        }

        private ResourceLocation resolve(UiTextureState state) {
            return ThemedStateTextureResolver.resolve(legacy, palette, state);
        }
    }

    static String tooltipKey(QuickBuildUiShape shape) {
        return "screen.rtsbuilding.tooltip.shape_"
                + shape.name().toLowerCase(Locale.ROOT);
    }
}
