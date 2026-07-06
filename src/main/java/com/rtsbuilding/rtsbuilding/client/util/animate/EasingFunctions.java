package com.rtsbuilding.rtsbuilding.client.util.animate;

/**
 * 缓动函数枚举——提供 12 种平滑动画插值曲线。
 *
 * <p><b>设计原则：</b></p>
 * <ul>
 *   <li>所有函数接收 t ∈ [0, 1]，返回变换后的值 ∈ [0, 1]</li>
 *   <li>输入在 [0, 1] 范围外时自动钳位，避免 NaN 或越界</li>
 *   <li>通过 {@link #apply(float)} 统一入口调用</li>
 * </ul>
 *
 * <p><b>缓动曲线一览：</b></p>
 * <table>
 *   <tr><th>曲线</th><th>特点</th><th>适用场景</th></tr>
 *   <tr><td>LINEAR</td><td>匀速直线</td><td>进度条、颜色过渡</td></tr>
 *   <tr><td>SMOOTHSTEP</td><td>柔和缓入缓出</td><td>悬浮高亮、透明度</td></tr>
 *   <tr><td>EASE_OUT_QUAD</td><td>缓出二次</td><td>一般入场动画</td></tr>
 *   <tr><td>EASE_OUT_CUBIC</td><td>缓出三次，更平滑</td><td>面板滑入</td></tr>
 *   <tr><td>EASE_OUT_QUART</td><td>缓出四次，尾段更柔</td><td>展开/收起动画</td></tr>
 *   <tr><td>EASE_OUT_BACK</td><td>略过目标再弹回</td><td>滑块吸附、弹窗弹出</td></tr>
 *   <tr><td>EASE_OUT_ELASTIC</td><td>弹性振荡衰减</td><td>浮窗弹出、通知出现</td></tr>
 *   <tr><td>EASE_OUT_BOUNCE</td><td>落地弹跳</td><td>滚动条回弹、数量变化</td></tr>
 *   <tr><td>EASE_IN_OUT_CIRC</td><td>缓入缓出圆形</td><td>镜头缩放</td></tr>
 *   <tr><td>EASE_IN_OUT_BACK</td><td>缓入缓出回弹</td><td>开关切换动画</td></tr>
 *   <tr><td>EASE_IN_ELASTIC</td><td>缓入弹性</td><td>离场强调效果</td></tr>
 *   <tr><td>EASE_IN_BOUNCE</td><td>缓入弹跳</td><td>离场弹跳效果</td></tr>
 * </table>
 */
public enum EasingFunctions {

    /** 匀速直线——t 原样返回 */
    LINEAR(t -> t),

    /** 平滑缓入缓出——t²(3-2t)，柔和的 S 形曲线 */
    SMOOTHSTEP(t -> t * t * (3.0f - 2.0f * t)),

    /** 缓出二次——1-(1-t)²，起步快收尾渐慢 */
    EASE_OUT_QUAD(t -> 1.0f - (1.0f - t) * (1.0f - t)),

    /** 缓出三次——1-(1-t)³，比二次收尾更平滑 */
    EASE_OUT_CUBIC(t -> 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)),

    /** 缓出四次——1-(1-t)⁴，尾段极柔和 */
    EASE_OUT_QUART(t -> {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    }),

    /** 缓出回弹——略过目标一点再弹回，c1=1.70158 为标准 overshoot 系数 */
    EASE_OUT_BACK(t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }),

    /** 缓出弹性——指数衰减正弦振荡，周期 2π/3 */
    EASE_OUT_ELASTIC(t -> {
        if (t == 0.0f || t == 1.0f) return t;
        float c4 = (float) (2.0f * Math.PI / 3.0f);
        return (float) (Math.pow(2.0f, -10.0f * t) * Math.sin((t * 10.0f - 0.75f) * c4) + 1.0f);
    }),

    /** 缓出弹跳——四分段多项式模拟球落地弹跳 */
    EASE_OUT_BOUNCE(t -> bounceOut(t)),

    /** 缓入缓出圆形——前半凹入后半凸出，如圆角过渡 */
    EASE_IN_OUT_CIRC(t -> t < 0.5f
            ? (1.0f - (float) Math.sqrt(1.0f - 4.0f * t * t)) / 2.0f
            : ((float) Math.sqrt(1.0f - (2.0f * t - 2.0f) * (2.0f * t - 2.0f)) + 1.0f) / 2.0f),

    /** 缓入缓出回弹——进出两端都有回弹效果 */
    EASE_IN_OUT_BACK(t -> {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        return t < 0.5f
                ? (float) (Math.pow(2.0f * t, 2) * ((c2 + 1.0f) * 2.0f * t - c2)) / 2.0f
                : (float) (Math.pow(2.0f * t - 2.0f, 2) * ((c2 + 1.0f) * (t * 2.0f - 2.0f) + c2) + 2.0f) / 2.0f;
    }),

    /** 缓入弹性——指数衰减正弦振荡，从静止加速弹入 */
    EASE_IN_ELASTIC(t -> {
        if (t == 0.0f || t == 1.0f) return t;
        float c4 = (float) (2.0f * Math.PI / 3.0f);
        return -(float) (Math.pow(2.0f, 10.0f * t - 10.0f) * Math.sin((t * 10.0f - 10.75f) * c4));
    }),

    /** 缓入弹跳——从静止加速弹入（通过 1-bounceOut(1-t) 实现） */
    EASE_IN_BOUNCE(t -> 1.0f - bounceOut(1.0f - t));

    // ======================== 函数式接口 ========================

    /**
     * 缓动函数接口——接收原始进度 t，返回变换后的进度。
     */
    @FunctionalInterface
    public interface EasingFunction {
        /**
         * @param t 原始动画进度 [0, 1]
         * @return 变换后的进度 [0, 1]
         */
        float apply(float t);
    }

    // ======================== 字段与方法 ========================

    private final EasingFunction function;

    EasingFunctions(EasingFunction function) {
        this.function = function;
    }

    /**
     * 应用缓动函数——自动钳位输入到 [0, 1]。
     *
     * @param t 原始进度（会自动钳位到 [0, 1]）
     * @return 缓动后的进度 [0, 1]
     */
    public float apply(float t) {
        if (t <= 0.0f) return 0.0f;
        if (t >= 1.0f) return 1.0f;
        return function.apply(t);
    }

    // ======================== 内部辅助 ========================

    /**
     * bounceOut 核心算法——四分段多项式弹跳曲线。
     * <p>分段点：1/2.75, 2/2.75, 2.5/2.75</p>
     */
    private static float bounceOut(float t) {
        if (t < 1.0f / 2.75f) {
            return 7.5625f * t * t;
        } else if (t < 2.0f / 2.75f) {
            t -= 1.5f / 2.75f;
            return 7.5625f * t * t + 0.75f;
        } else if (t < 2.5f / 2.75f) {
            t -= 2.25f / 2.75f;
            return 7.5625f * t * t + 0.9375f;
        } else {
            t -= 2.625f / 2.75f;
            return 7.5625f * t * t + 0.984375f;
        }
    }
}
