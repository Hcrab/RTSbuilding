package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiButtonId;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.topbar.TopBarUiContribution;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiStateBlendAnimationSet;
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

    public enum VisualState {
        INACTIVE,
        HOVER,
        ACTIVE,
        PRESSED
    }

    private static final List<VisualState> VISUAL_STATES =
            Collections.unmodifiableList(Arrays.asList(VisualState.values()));

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
            VisualState target,
            UiStateBlendAnimationSet<TopBarTypes.TopBarButtonId, VisualState> transitions,
            boolean animationsEnabled) {
        transitions.update(id, target, animationsEnabled);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            for (VisualState state : VISUAL_STATES) {
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

    public static List<VisualState> visualStates() {
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

    public static ResourceLocation texture(TopBarTypes.TopBarButtonId id, VisualState state) {
        return switch (id) {
            case INTERACT -> state(state, TOPBAR_INTERACT_INACTIVE, TOPBAR_INTERACT_HOVER,
                    TOPBAR_INTERACT_ACTIVE, TOPBAR_INTERACT_PRESSED);
            case LINK -> state(state, TOPBAR_LINK_INACTIVE, TOPBAR_LINK_HOVER,
                    TOPBAR_LINK_ACTIVE, TOPBAR_LINK_PRESSED);
            case FUNNEL -> state(state, TOPBAR_FUNNEL_INACTIVE, TOPBAR_FUNNEL_HOVER,
                    TOPBAR_FUNNEL_ACTIVE, TOPBAR_FUNNEL_PRESSED);
            case ROTATE -> state(state, TOPBAR_ROTATE_INACTIVE, TOPBAR_ROTATE_HOVER,
                    TOPBAR_ROTATE_ACTIVE, TOPBAR_ROTATE_PRESSED);
            case QUICK_BUILD -> state(state, TOPBAR_QUICK_BUILD_INACTIVE, TOPBAR_QUICK_BUILD_HOVER,
                    TOPBAR_QUICK_BUILD_ACTIVE, TOPBAR_QUICK_BUILD_PRESSED);
            case QUEST_DETECT -> state(state, TOPBAR_QUEST_DETECT_INACTIVE, TOPBAR_QUEST_DETECT_HOVER,
                    TOPBAR_QUEST_DETECT_ACTIVE, TOPBAR_QUEST_DETECT_PRESSED);
            case CHUNK_VIEW -> state(state, TOPBAR_CHUNK_VIEW_INACTIVE, TOPBAR_CHUNK_VIEW_HOVER,
                    TOPBAR_CHUNK_VIEW_ACTIVE, TOPBAR_CHUNK_VIEW_PRESSED);
            case RANGE_CULLING -> state(state, TOPBAR_RANGE_CULLING_INACTIVE, TOPBAR_RANGE_CULLING_HOVER,
                    TOPBAR_RANGE_CULLING_ACTIVE, TOPBAR_RANGE_CULLING_PRESSED);
            case GUIDE -> state(state, TOPBAR_GUIDE_INACTIVE, TOPBAR_GUIDE_HOVER,
                    TOPBAR_GUIDE_ACTIVE, TOPBAR_GUIDE_PRESSED);
            case DEVELOPER -> state(state, TOPBAR_DEVELOPER_INACTIVE, TOPBAR_DEVELOPER_HOVER,
                    TOPBAR_DEVELOPER_ACTIVE, TOPBAR_DEVELOPER_PRESSED);
            case GEAR -> state(state, TOPBAR_GEAR_INACTIVE, TOPBAR_GEAR_HOVER,
                    TOPBAR_GEAR_ACTIVE, TOPBAR_GEAR_PRESSED);
            default -> null;
        };
    }

    public static VisualState visualState(boolean active, boolean hovered, boolean pressed) {
        if (pressed) return VisualState.PRESSED;
        if (active) return VisualState.ACTIVE;
        return hovered ? VisualState.HOVER : VisualState.INACTIVE;
    }

    private static ResourceLocation state(VisualState state, ResourceLocation inactive,
                                          ResourceLocation hover, ResourceLocation active,
                                          ResourceLocation pressed) {
        return switch (state) {
            case INACTIVE -> inactive;
            case HOVER -> hover;
            case ACTIVE -> active;
            case PRESSED -> pressed;
        };
    }

    private TopBarIconRenderer() {
    }
}
