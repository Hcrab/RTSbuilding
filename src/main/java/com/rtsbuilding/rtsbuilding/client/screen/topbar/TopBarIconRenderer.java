package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiContribution;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiStateBlendAnimationSet;
import com.rtsbuilding.rtsbuilding.client.theme.LegacyStateTextureResolver;
import com.rtsbuilding.rtsbuilding.client.theme.LegacyTextureSet;
import com.rtsbuilding.rtsbuilding.client.theme.PaletteTextureCatalog;
import com.rtsbuilding.rtsbuilding.client.theme.ThemedStateTextureResolver;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 顶栏正式图标的资源目录与状态解析器。
 *
 * <p>本类只负责从按钮语义和视觉状态选择纹理并绘制，不再在运行时拼接像素图形。
 * 新增正式按钮时必须同时提供 inactive、hover、active、pressed 四态资源。</p>
 */
public final class TopBarIconRenderer {
    private static final double MIN_VISIBLE_WEIGHT = 0.001D;

    private static final List<UiTextureState> VISUAL_STATES =
            Collections.unmodifiableList(Arrays.asList(UiTextureState.values()));

    /**
     * 将当前业务视觉状态提交给有界动画集，再按权重叠加正式状态纹理。
     *
     * <p>状态和命中已在调用前立即生效；这里只做纹理交叉淡入。稳定态只提交一张纹理，
     * 过渡期间最多提交四张，不生成中间帧资源，也不保留额外矩形轮廓。</p>
     */
    public static void renderBlended(
            RtsGuiContext graphics,
            TopBarTypes.TopBarButtonId id,
            int x, int y, int size,
            UiTextureState target,
            UiStateBlendAnimationSet<TopBarTypes.TopBarButtonId, UiTextureState> transitions,
            boolean animationsEnabled) {
        transitions.update(id, target, animationsEnabled);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (UiTextureState state : VISUAL_STATES) {
                double weight = transitions.weight(id, state);
                if (weight <= MIN_VISIBLE_WEIGHT) {
                    continue;
                }
                ResourceLocation texture = texture(id, state);
                if (texture == null) {
                    texture = TOPBAR_GUIDE_INACTIVE;
                }
                graphics.setColor(1.0F, 1.0F, 1.0F,
                        (float) Math.max(0.0D, Math.min(1.0D, weight)));
                graphics.blit(texture, x, y, 0, 0, size, size, size, size);
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    public static List<UiTextureState> visualStates() {
        return VISUAL_STATES;
    }

    public static String tooltipKey(TopBarTypes.TopBarButtonId id) {
        try {
            TopBarUiContribution contribution = TopBarUiCatalog.contribution(
                    TopBarUiButtonId.valueOf(id.name()));
            if (contribution != null) return contribution.getTooltipKey();
        } catch (IllegalArgumentException ignored) {
            // 旧平台专属按钮尚未进入正式目录时仍保持稳定回退键。
        }
        return "screen.rtsbuilding.topbar.tooltip."
                + id.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ResourceLocation texture(TopBarTypes.TopBarButtonId id, UiTextureState state) {
        LegacyTextureSet legacy = LegacyStateTextureResolver.topBarSet(id);
        ResourceLocation palette = PaletteTextureCatalog.topBar(id);
        if (legacy == null || palette == null) return null;
        UiIndexedTextureSpec spec = id == TopBarTypes.TopBarButtonId.QUEST_DETECT
                ? UiIndexedTextureSpec.PR133_QUEST
                : UiIndexedTextureSpec.PR133_THREE_TONE;
        return ThemedStateTextureResolver.resolve(legacy, palette, state, spec);
    }

    public static UiTextureState visualState(boolean active, boolean hovered, boolean pressed) {
        if (pressed) return UiTextureState.PRESSED;
        if (active) return UiTextureState.ACTIVE;
        return hovered ? UiTextureState.HOVER : UiTextureState.INACTIVE;
    }

    private TopBarIconRenderer() {
    }
}
