package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.Objects;

/**
 * 九宫格渲染缓存——将平铺拼装结果预先渲染到一张 {@link DynamicTexture} 中，
 * 后续帧直接以单次 blit 绘制，避免每帧重复的循环平铺开销。
 *
 * <p>使用 {@link NativeImage} 在 CPU 端完成像素拼装（与 GPU 渲染结果像素级一致），
 * 然后上传到 GPU，后续每帧只需一次纹理 blit 操作。</p>
 *
 * <p>适用场景：尺寸相对固定的大面板（全屏背景、装饰内嵌层等），
 * 可在不改变渲染质量的前提下将数百次 blit 降为 1 次。</p>
 *
 * <p>缓存自动失效条件：</p>
 * <ul>
 *   <li>目标尺寸（dstW/dstH）变化</li>
 *   <li>双主题状态切换（暗色/亮色）</li>
 *   <li>源规格（NineSliceSource）变化</li>
 *   <li>贴图路径变化</li>
 * </ul>
 */
public class NineSliceCache implements AutoCloseable {

    /** 缓存纹理在 TextureManager 中注册的固定路径 */
    private static final ResourceLocation CACHE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            RtsbuildingMod.MODID, "nine_slice_cache");

    // ======================== 源纹理缓存（避免重复加载 PNG） ========================

    private ResourceLocation sourceRl;
    private int sourceTexW;
    private int sourceTexH;
    private NativeImage sourceImage;

    // ======================== 输出缓存 ========================

    private DynamicTexture cachedTexture;
    /** 缓存输出图像的 NativeImage 引用（与 DynamicTexture 共享数据） */
    private NativeImage cachedImage;
    private int cachedW = -1;
    private int cachedH = -1;

    // ======================== 缓存签名 ========================

    private int lastDstW = -1;
    private int lastDstH = -1;
    private int lastSrcX, lastSrcY, lastSrcW, lastSrcH, lastBorder;
    private boolean lastLightMode;

    // ======================== 公开 API ========================

    /**
     * 绘制九宫格（优先使用缓存）。
     * <p>缓存有效时直接 blit 缓存的纹理；否则走完整九宫格渲染并更新缓存。</p>
     */
    public void drawOrCache(GuiGraphics g, ResourceLocation texture,
                            int texW, int texFileH,
                            int dstX, int dstY, int dstW, int dstH,
                            NineSliceSource src) {
        if (dstW <= 0 || dstH <= 0) return;

        if (needsRebuild(texture, dstW, dstH, src)) {
            rebuild(texture, texW, texFileH, dstW, dstH, src);
        }

        if (cachedTexture != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(CACHE_LOCATION, dstX, dstY, dstW, dstH,
                    0, 0, cachedW, cachedH, cachedW, cachedH);
        } else {
            RtsClientUiUtil.drawNineSlice(g, texture, texW, texFileH,
                    dstX, dstY, dstW, dstH, src);
        }
    }

    /** 判断缓存是否需要重建 */
    private boolean needsRebuild(ResourceLocation texture, int dstW, int dstH, NineSliceSource src) {
        if (cachedTexture == null) return true;
        if (dstW != lastDstW || dstH != lastDstH) return true;
        if (!Objects.equals(texture, sourceRl)) return true;
        if (src.srcX() != lastSrcX || src.srcY() != lastSrcY) return true;
        if (src.srcW() != lastSrcW || src.srcH() != lastSrcH) return true;
        if (src.border() != lastBorder) return true;
        if (RtsClientUiUtil.isLightMode() != lastLightMode) return true;
        return false;
    }

    /** 重建缓存：CPU 像素拼装 → GPU 上传 */
    private void rebuild(ResourceLocation texture, int texW, int texFileH,
                         int dstW, int dstH, NineSliceSource src) {
        lastDstW = dstW;
        lastDstH = dstH;
        lastSrcX = src.srcX();
        lastSrcY = src.srcY();
        lastSrcW = src.srcW();
        lastSrcH = src.srcH();
        lastBorder = src.border();
        lastLightMode = RtsClientUiUtil.isLightMode();

        if (sourceImage == null || !Objects.equals(texture, sourceRl)
                || texW != sourceTexW || texFileH != sourceTexH) {
            loadSourceImage(texture, texW, texFileH);
        }
        if (sourceImage == null) return;

        ensureOutput(dstW, dstH);

        int border = src.border();
        int halfW = sourceTexW / 2;
        int themeOffset = lastLightMode ? halfW : 0;
        int srcLeft = themeOffset + src.srcX();
        int srcTop = src.srcY();
        int b = border;

        // 四角
        copyPixels(srcLeft, srcTop, b, b, 0, 0);
        copyPixels(srcLeft + src.srcW() - b, srcTop, b, b, dstW - b, 0);
        copyPixels(srcLeft, srcTop + src.srcH() - b, b, b, 0, dstH - b);
        copyPixels(srcLeft + src.srcW() - b, srcTop + src.srcH() - b, b, b, dstW - b, dstH - b);

        int srcInnerW = src.srcW() - 2 * b;
        int srcInnerH = src.srcH() - 2 * b;
        int innerW = dstW - 2 * b;
        int innerH = dstH - 2 * b;

        // 上边缘
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = 0; dx < innerW; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, innerW - dx);
                copyPixels(srcLeft + b, srcTop, tileW, b, b + dx, 0);
            }
        }
        // 下边缘
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = 0; dx < innerW; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, innerW - dx);
                copyPixels(srcLeft + b, srcTop + src.srcH() - b, tileW, b, b + dx, dstH - b);
            }
        }
        // 左边缘
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = 0; dy < innerH; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, innerH - dy);
                copyPixels(srcLeft, srcTop + b, b, tileH, 0, b + dy);
            }
        }
        // 右边缘
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = 0; dy < innerH; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, innerH - dy);
                copyPixels(srcLeft + src.srcW() - b, srcTop + b, b, tileH, dstW - b, b + dy);
            }
        }
        // 中心区域
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            for (int dy = 0; dy < innerH; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, innerH - dy);
                for (int dx = 0; dx < innerW; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, innerW - dx);
                    copyPixels(srcLeft + b, srcTop + b, tileW, tileH, b + dx, b + dy);
                }
            }
        }

        cachedTexture.upload();
    }

    /** 像素拷贝：从源纹理复制矩形区域到缓存目标 */
    private void copyPixels(int srcX, int srcY, int w, int h, int dstX, int dstY) {
        if (w <= 0 || h <= 0) return;
        int cw = Math.min(w, sourceImage.getWidth() - srcX);
        int ch = Math.min(h, sourceImage.getHeight() - srcY);
        cw = Math.min(cw, cachedImage.getWidth() - dstX);
        ch = Math.min(ch, cachedImage.getHeight() - dstY);
        if (cw <= 0 || ch <= 0) return;
        for (int row = 0; row < ch; row++) {
            for (int col = 0; col < cw; col++) {
                int pixel = sourceImage.getPixelRGBA(srcX + col, srcY + row);
                cachedImage.setPixelRGBA(dstX + col, dstY + row, pixel);
            }
        }
    }

    /** 加载源贴图像素到 NativeImage */
    private void loadSourceImage(ResourceLocation texture, int texW, int texFileH) {
        if (sourceImage != null) {
            sourceImage.close();
            sourceImage = null;
        }
        sourceRl = texture;
        sourceTexW = texW;
        sourceTexH = texFileH;
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(texture).orElse(null);
            if (resource == null) {
                RtsbuildingMod.LOGGER.warn("NineSliceCache: texture not found: {}", texture);
                return;
            }
            try (var stream = resource.open()) {
                sourceImage = NativeImage.read(stream);
            }
        } catch (IOException e) {
            RtsbuildingMod.LOGGER.error("NineSliceCache: failed to load texture {}: {}", texture, e.getMessage());
        }
    }

    /** 确保输出纹理存在且尺寸正确 */
    private void ensureOutput(int w, int h) {
        if (cachedTexture != null && cachedW == w && cachedH == h) {
            return;
        }
        if (cachedTexture != null) {
            cachedTexture.close();
            cachedTexture = null;
        }
        if (cachedImage != null) {
            cachedImage.close();
            cachedImage = null;
        }
        cachedImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        cachedTexture = new DynamicTexture(cachedImage);
        cachedW = w;
        cachedH = h;
        Minecraft.getInstance().getTextureManager().register(CACHE_LOCATION, cachedTexture);
    }

    @Override
    public void close() {
        if (sourceImage != null) {
            sourceImage.close();
            sourceImage = null;
        }
        if (cachedTexture != null) {
            cachedTexture.close();
            cachedTexture = null;
        }
        if (cachedImage != null) {
            cachedImage.close();
            cachedImage = null;
        }
        cachedW = -1;
        cachedH = -1;
    }
}
