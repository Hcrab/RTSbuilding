package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 交叉淡入淡出渲染器——在两种渲染状态之间以指定进度 t 进行过渡。
 *
 * <p>将交叉淡入淡出逻辑从 {@code RtsClientUiUtil} 中提取至此，
 * 配合 {@link BlendScope} 确保 blend 状态正确配对。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * CrossFadeRenderer.render(0.5f,
 *     () -> drawNormalState(),
 *     () -> drawHoveredState());
 * }</pre>
 */
public final class CrossFadeRenderer {

    /** 过渡完成阈值——超过此值视为完全到达目标状态 */
    private static final float ALMOST_ONE = 0.999f;
    /** 过渡起始阈值——低于此值视为完全处于起始状态 */
    private static final float ALMOST_ZERO = 0.001f;

    private CrossFadeRenderer() {}

    /**
     * 以交叉淡入淡出方式渲染两个状态。
     * <p>根据进度 t 自动选择：</p>
     * <ul>
     *   <li>t &le; {@value #ALMOST_ZERO} → 仅渲染 normal</li>
     *   <li>t &ge; {@value #ALMOST_ONE} → 仅渲染 hovered</li>
     *   <li>{@value #ALMOST_ZERO} &lt; t &lt; {@value #ALMOST_ONE} → 交叉淡入淡出：先渲染 normal，再以 t 透明度叠加上 hovered</li>
     * </ul>
     *
     * @param t       交叉淡入淡出进度 [0, 1]，0=全部 normal，1=全部 hovered
     * @param normal  普通态渲染器
     * @param hovered 目标态渲染器
     */
    public static void render(float t, Runnable normal, Runnable hovered) {
        if (t > ALMOST_ZERO && t < ALMOST_ONE) {
            try (BlendScope blend = BlendScope.crossFade()) {
                normal.run();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, t);
                try {
                    hovered.run();
                } finally {
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        } else if (t >= ALMOST_ONE) {
            hovered.run();
        } else {
            normal.run();
        }
    }
}
