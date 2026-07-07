package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceTiler;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 九宫格瓷砖指令缓存层——缓存九宫格展开后的瓷砖坐标，避免每帧重复计算。
 *
 * <p><b>解决的问题：</b></p>
 * <ul>
 *   <li>每次 {@link SpriteRenderer#drawNineSlice} 都重新计算九宫格 9~N 块瓷砖坐标</li>
 *   <li>静态镶边（右边栏、下边栏）的尺寸在拖拽事件之间不会变化</li>
 *   <li>缓存层在参数不变时直接回放已计算的瓷砖，省去循环/条件判断/对象分配</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * private final CachedLayer borderCache = new CachedLayer();
 *
 * public void render(...) {
 *     borderCache.drawNineSlice(g, texInfo, u, v, regionW, regionH, border,
 *             dstX, dstY, dstW, dstH);
 * }
 *
 * // 尺寸变化时：
 * borderCache.invalidate();
 * }</pre>
 */
public final class CachedLayer {

    /** 单块瓷砖的描述 */
    private record Tile(int srcX, int srcY, int srcW, int srcH,
                        int dstX, int dstY, int dstW, int dstH) {}

    /** 缓存的参数键——用于检测是否需要重算 */
    private static final class CacheKey {
        TextureInfo texInfo;
        int u, v, regionW, regionH, border;
        int dstX, dstY, dstW, dstH;

        boolean matches(TextureInfo texInfo, int u, int v, int rw, int rh, int border,
                        int dx, int dy, int dw, int dh) {
            return this.texInfo == texInfo
                    && this.u == u && this.v == v
                    && this.regionW == rw && this.regionH == rh
                    && this.border == border
                    && this.dstX == dx && this.dstY == dy
                    && this.dstW == dw && this.dstH == dh;
        }

        void set(TextureInfo texInfo, int u, int v, int rw, int rh, int border,
                 int dx, int dy, int dw, int dh) {
            this.texInfo = texInfo;
            this.u = u; this.v = v;
            this.regionW = rw; this.regionH = rh;
            this.border = border;
            this.dstX = dx; this.dstY = dy;
            this.dstW = dw; this.dstH = dh;
        }
    }

    private final CacheKey key = new CacheKey();
    private final List<Tile> tiles = new ArrayList<>(32);
    private boolean valid;

    /** 标记缓存失效，下次绘制时重新计算。 */
    public void invalidate() {
        this.valid = false;
    }

    /**
     * 绘制九宫格——优先回放缓存，缓存未命中时重新计算并缓存。
     *
     * @param g      渲染上下文
     * @param texInfo 贴图元数据
     * @param u       源区域 X
     * @param v       源区域 Y
     * @param regionW 源区域宽度
     * @param regionH 源区域高度
     * @param border  九宫格边框
     * @param dstX    目标 X
     * @param dstY    目标 Y
     * @param dstW    目标宽度
     * @param dstH    目标高度
     */
    public void drawNineSlice(GuiGraphics g, TextureInfo texInfo,
                               int u, int v, int regionW, int regionH, int border,
                               int dstX, int dstY, int dstW, int dstH) {
        // 参数变化则重新计算缓存
        if (!valid || !key.matches(texInfo, u, v, regionW, regionH, border,
                dstX, dstY, dstW, dstH)) {
            recompute(texInfo, u, v, regionW, regionH, border,
                    dstX, dstY, dstW, dstH);
        }

        // 回放缓存的瓷砖（FilterState 由 BatchCollector 或显式调用处理）
        FilterState.getInstance().apply(texInfo);
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        for (Tile tile : tiles) {
            g.blit(texInfo.location(),
                    tile.dstX(), tile.dstY(), tile.dstW(), tile.dstH(),
                    tile.srcX(), tile.srcY(), tile.srcW(), tile.srcH(),
                    texW, texH);
        }
    }

    // ======================== 内部 ========================

    private void recompute(TextureInfo texInfo, int u, int v, int rw, int rh, int border,
                           int dx, int dy, int dw, int dh) {
        key.set(texInfo, u, v, rw, rh, border, dx, dy, dw, dh);
        tiles.clear();
        NineSliceTiler.forEachTile(u, v, rw, rh, border, dx, dy, dw, dh,
                (sx, sy, sw, sh, tx, ty, tw, th) ->
                        tiles.add(new Tile(sx, sy, sw, sh, tx, ty, tw, th)));
        valid = true;
    }
}
