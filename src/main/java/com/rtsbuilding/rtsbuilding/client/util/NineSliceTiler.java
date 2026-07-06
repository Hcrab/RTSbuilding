package com.rtsbuilding.rtsbuilding.client.util;

/**
 * 九宫格平铺计算器——提取九宫格拼贴坐标计算逻辑，
 * 供 {@link com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer#drawNineSlice} 复用。
 *
 * <p>消除两边重复的 {@code for} 循环平铺算法，确保一处修改两处生效。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * NineSliceTiler.forEachTile(srcLeft, srcTop, srcW, srcH, border,
 *         dstX, dstY, dstW, dstH,
 *         (sx, sy, sw, sh, dx, dy, dw, dh) -> {
 *             // blit:  g.blit(texture, dx, dy, dw, dh, sx, sy, sw, sh, texW, texH);
 *             // copy:  copyPixels(sx, sy, sw, sh, dx, dy);
 *         });
 * }</pre>
 */
public final class NineSliceTiler {

    /**
     * 九宫格拼贴片渲染回调。
     */
    @FunctionalInterface
    public interface TileCallback {
        /**
         * 渲染一个拼贴块。
         *
         * @param srcX 源区域 X（贴图坐标）
         * @param srcY 源区域 Y（贴图坐标）
         * @param srcW 源区域宽度
         * @param srcH 源区域高度
         * @param dstX 目标区域 X（屏幕/缓存坐标）
         * @param dstY 目标区域 Y
         * @param dstW 目标宽度
         * @param dstH 目标高度
         */
        void accept(int srcX, int srcY, int srcW, int srcH,
                    int dstX, int dstY, int dstW, int dstH);
    }

    /**
     * 遍历九宫格的所有拼贴块（四角、四边、中心），依次回调 {@code renderer}。
     * <p>回调按以下顺序调用：</p>
     * <ol>
     *   <li>左上角</li>
     *   <li>右上角</li>
     *   <li>左下角</li>
     *   <li>右下角</li>
     *   <li>上边（水平平铺，从左到右）</li>
     *   <li>下边（水平平铺，从左到右）</li>
     *   <li>左边（垂直平铺，从上到下）</li>
     *   <li>右边（垂直平铺，从上到下）</li>
     *   <li>中心区域（逐行平铺，从上到下，每行从左到右）</li>
     * </ol>
     *
     * @param srcLeft  源区域左边界（贴图坐标 X）
     * @param srcTop   源区域上边界（贴图坐标 Y）
     * @param srcW     源区域宽度
     * @param srcH     源区域高度
     * @param border   九宫格边框宽度
     * @param dstX     目标区域左边界 X
     * @param dstY     目标区域上边界 Y
     * @param dstW     目标区域宽度
     * @param dstH     目标区域高度
     * @param renderer 每块拼贴的回调
     */
    public static void forEachTile(int srcLeft, int srcTop, int srcW, int srcH, int border,
                                    int dstX, int dstY, int dstW, int dstH,
                                    TileCallback renderer) {
        int b = border;
        int innerW = dstW - 2 * b;
        int innerH = dstH - 2 * b;
        int srcInnerW = srcW - 2 * b;
        int srcInnerH = srcH - 2 * b;

        // ==== 四角 ====
        renderer.accept(srcLeft, srcTop, b, b, dstX, dstY, b, b);
        renderer.accept(srcLeft + srcW - b, srcTop, b, b, dstX + dstW - b, dstY, b, b);
        renderer.accept(srcLeft, srcTop + srcH - b, b, b, dstX, dstY + dstH - b, b, b);
        renderer.accept(srcLeft + srcW - b, srcTop + srcH - b, b, b, dstX + dstW - b, dstY + dstH - b, b, b);

        // ==== 上边 / 下边（水平平铺）====
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                renderer.accept(srcLeft + b, srcTop, tileW, b, dx, dstY, tileW, b);
            }
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                renderer.accept(srcLeft + b, srcTop + srcH - b, tileW, b, dx, dstY + dstH - b, tileW, b);
            }
        }

        // ==== 左边 / 右边（垂直平铺）====
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                renderer.accept(srcLeft, srcTop + b, b, tileH, dstX, dy, b, tileH);
            }
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                renderer.accept(srcLeft + srcW - b, srcTop + b, b, tileH, dstX + dstW - b, dy, b, tileH);
            }
        }

        // ==== 中心区域（逐行平铺）====
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                    renderer.accept(srcLeft + b, srcTop + b, tileW, tileH, dx, dy, tileW, tileH);
                }
            }
        }
    }

    private NineSliceTiler() {}
}
