package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * RAII 风格的混合状态守卫——自动启用混合并在退出时恢复。
 * <p>使用 try-with-resources 确保 blend 状态正确配对，避免
 * {@code enableBlend / disableBlend} 不匹配导致的渲染异常。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * try (BlendScope blend = BlendScope.normal()) {
 *     // 此区域 blend 已启用且为标准混合函数
 *     renderSomething();
 * }
 * // blend 自动恢复为原先状态
 * }</pre>
 */
public record BlendScope(boolean wasEnabled) implements AutoCloseable {

    /**
     * 启用标准混合模式（SRC_ALPHA, ONE_MINUS_SRC_ALPHA）。
     * <p>适用于大多数半透明 UI 渲染场景。</p>
     */
    public static BlendScope normal() {
        boolean was = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return new BlendScope(was);
    }

    /**
     * 启用交叉淡入淡出混合模式。
     * <p>适用于 hover 动画中两张贴图的叠加过渡。</p>
     */
    public static BlendScope crossFade() {
        boolean was = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        return new BlendScope(was);
    }

    @Override
    public void close() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (!wasEnabled()) {
            RenderSystem.disableBlend();
        }
    }
}
