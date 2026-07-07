package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceTiler;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeListener;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * GPU 加速九宫格渲染缓存——使用 FBO 将平铺拼装结果渲染到 GPU 纹理，
 * 后续帧直接以单次 blit 绘制，避免每帧重复的循环平铺开销。
 *
 * <p><b>相比 {@code NineSliceCache} 的性能提升：</b></p>
 * <ul>
 *   <li><b>重建时</b>：使用 {@link GlFbo#blitFromTexture} 在 GPU 端完成拼贴，
 *       零 CPU 像素拷贝，避免 JNI 穿越</li>
 *   <li><b>渲染时</b>：与旧版相同，单次 blit 绘制</li>
 *   <li><b>FBO 不可用时</b>：自动回退到 {@link SpriteRenderer#drawNineSlice} 直接渲染</li>
 * </ul>
 *
 * <p>缓存自动失效条件：</p>
 * <ul>
 *   <li>目标尺寸（dstW/dstH）变化</li>
 *   <li>双主题状态切换（通过 {@link ThemeListener} 主动失效）</li>
 *   <li>源规格（NineSliceRegion）变化</li>
 *   <li>贴图路径变化</li>
 * </ul>
 */
public class NineSliceGpuCache implements AutoCloseable, ThemeListener {

    // ======================== 缓存纹理注册 ========================

    private static final ResourceLocation CACHE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            RtsbuildingMod.MODID, "gpu_nine_slice_cache");

    // ======================== 缓存键（驱动失效检测） ========================

    /**
     * 九宫格缓存键——封装所有影响缓存有效性的参数。
     * <p>将此 7 个分散字段收拢为一个不可变值对象，
     * 避免 {@link NineSliceGpuCache} 中 10+ 个状态字段的杂乱局面。</p>
     */
    private record CacheKey(
            int dstW, int dstH,
            int srcX, int srcY, int srcW, int srcH,
            int border,
            ResourceLocation textureLocation,
            boolean lightMode
    ) {
        static CacheKey from(NineSliceRegion spec, int dstW, int dstH, boolean lightMode) {
            SpriteRegion r = spec.region();
            return new CacheKey(
                    dstW, dstH,
                    r.u(), r.v(), r.regionWidth(), r.regionHeight(),
                    spec.border(),
                    r.texture().location(),
                    lightMode
            );
        }
    }

    /** 回退到 CPU 渲染模式后的冷却时长（毫秒），冷却期满后尝试恢复 GPU */
    private static final long FALLBACK_RETRY_MS = 5000L;

    // ======================== 状态 ========================

    private GlFbo fbo;
    private int cachedW = -1;
    private int cachedH = -1;
    private CacheKey lastKey;

    /** 下次允许尝试 GPU 重建的系统时间戳（毫秒），0=未处于 fallback 模式 */
    private long nextGpuRetryMs;

    /** 注册到 TextureManager 的包装纹理 */
    private FboTexture registeredTexture;

    /** 是否已释放资源 */
    private boolean disposed;

    public NineSliceGpuCache() {
        ThemeManager.getInstance().addListener(this);
        registeredTexture = new FboTexture();
        // 防止重复构造导致 TextureManager 中残留旧 FboTexture
        var texMgr = Minecraft.getInstance().getTextureManager();
        var existing = texMgr.getTexture(CACHE_LOCATION);
        if (existing != null) {
            RtsbuildingMod.LOGGER.warn("NineSliceGpuCache: CACHE_LOCATION 已有注册纹理，将覆盖");
        }
        texMgr.register(CACHE_LOCATION, registeredTexture);
    }

    // ======================== 核心渲染方法 ========================

    /**
     * 绘制九宫格（优先使用 GPU 缓存，回退到直接渲染）。
     *
     * <p>内部自动拆分为缓存有效性检测 + 渲染两步，避免单方法职责过重。</p>
     */
    public void drawOrCache(GuiGraphics g, NineSliceRegion spec,
                             int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        ensureCacheValid(spec, dstW, dstH);
        if (isFallback()) {
            SpriteRenderer.drawNineSlice(g, spec, dstX, dstY, dstW, dstH);
            return;
        }
        renderFromCache(g, dstX, dstY, dstW, dstH);
    }

    /**
     * 确保 GPU 缓存有效——缓存键变化时重建，fallback 冷却期满后自动重试 GPU。
     */
    private void ensureCacheValid(NineSliceRegion spec, int dstW, int dstH) {
        if (!needsRebuild(spec, dstW, dstH)) return;

        // fallback 冷却期中，跳过 GPU 尝试
        if (isFallback()) {
            long now = System.currentTimeMillis();
            if (now < nextGpuRetryMs) return;
            // 冷却期满，恢复 GPU 尝试
        }

        rebuildGpu(spec, dstW, dstH);
    }

    /**
     * 从 GPU 缓存纹理渲染九宫格。
     */
    private void renderFromCache(GuiGraphics g, int dstX, int dstY, int dstW, int dstH) {
        if (fbo == null || disposed) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(CACHE_LOCATION, dstX, dstY, dstW, dstH,
                0, 0, cachedW, cachedH, cachedW, cachedH);
    }

    /**
     * 当前是否处于 fallback 模式（GPU 缓存不可用，需使用 CPU 直接渲染）。
     */
    private boolean isFallback() {
        return nextGpuRetryMs > 0;
    }

    // ======================== 缓存失效检测 ========================

    private boolean needsRebuild(NineSliceRegion spec, int dstW, int dstH) {
        if (fbo == null) return true;
        CacheKey key = CacheKey.from(spec, dstW, dstH, isLightMode());
        return !key.equals(lastKey);
    }

    private void rebuildGpu(NineSliceRegion spec, int dstW, int dstH) {
        SpriteRegion r = spec.region();
        TextureInfo texInfo = r.texture();
        ResourceLocation texture = texInfo.location();

        // 保存缓存键
        this.lastKey = CacheKey.from(spec, dstW, dstH, isLightMode());

        // 获取源纹理的 OpenGL ID
        var mcTex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (mcTex == null) {
            enterFallback();
            return;
        }
        int srcTexId = mcTex.getId();
        if (srcTexId < 0) {
            enterFallback();
            return;
        }

        // 确保 FBO 尺寸匹配
        if (fbo == null) {
            try {
                fbo = new GlFbo(dstW, dstH);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.warn("NineSliceGpuCache: FBO creation failed, falling back to CPU: {}", e.getMessage());
                enterFallback();
                return;
            }
        } else {
            fbo.resize(dstW, dstH);
        }
        cachedW = dstW;
        cachedH = dstH;

        // GPU 重建成功，清除 fallback 状态
        this.nextGpuRetryMs = 0L;

        // 更新包装纹理的 GL ID，使 g.blit(CACHE_LOCATION, ...) 能正确查找
        if (registeredTexture != null && fbo != null) {
            registeredTexture.setGlTextureId(fbo.getTexId());
        }

        // 清空 FBO
        fbo.clear();

        // 使用 GPU blit 完成所有拼贴
        int texW = texInfo.fullWidth();
        int texFileH = texInfo.fullHeight();
        // OpenGL Y 轴从底部算起，需要翻转
        int texH = texFileH;

        NineSliceTiler.forEachTile(
                r.u(), r.v(), r.regionWidth(), r.regionHeight(), spec.border(),
                0, 0, dstW, dstH,
                (sx, sy, sw, sh, dx, dy, dw, dh) ->
                        blitGpuTile(srcTexId, texW, texH, dstH, sx, sy, sw, sh, dx, dy, dw, dh));
    }

    /**
     * 将贴图坐标系的九宫格块通过 GPU blit 拷贝到 FBO。
     * <p>{@code NineSliceTiler} 提供的坐标为贴图坐标系（左上原点），
     * 而 OpenGL {@code glBlitFramebuffer} 使用左下原点坐标系。
     * 此方法统一处理 Y 轴翻转转换，避免在各处重复且容易出错的内联翻转逻辑。</p>
     *
     * @param srcTexId  源纹理 OpenGL ID
     * @param texW      源纹理宽度
     * @param texH      源纹理高度（贴图坐标系高度，用于 Y 轴翻转）
     * @param dstH      目标 FBO 高度（用于目标 Y 轴翻转）
     * @param sx        源块左边界（贴图坐标）
     * @param sy        源块上边界（贴图坐标）
     * @param sw        源块宽度
     * @param sh        源块高度
     * @param dx        目标块左边界（贴图坐标）
     * @param dy        目标块上边界（贴图坐标）
     * @param dw        目标块宽度
     * @param dh        目标块高度
     */
    private void blitGpuTile(int srcTexId, int texW, int texH, int dstH,
                              int sx, int sy, int sw, int sh,
                              int dx, int dy, int dw, int dh) {
        // 翻转源 Y：从贴图左上原点 → OpenGL 左下原点
        int glSrcY = texH - (sy + sh);
        // 翻转目标 Y 同理
        fbo.blitFromTexture(srcTexId, texW, texH,
                sx, glSrcY, sw, sh,
                dx, dstH - dy - dh, dw, dh);
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        ThemeManager.getInstance().removeListener(this);
        if (fbo != null) {
            fbo.close();
            fbo = null;
        }
        if (registeredTexture != null) {
            registeredTexture.setGlTextureId(-1);
            registeredTexture = null;
        }
        cachedW = -1;
        cachedH = -1;
        lastKey = null;
        nextGpuRetryMs = 0L;
    }

    // ======================== ThemeListener ========================

    @Override
    public void onThemeChanged(boolean lightMode) {
        // 使缓存键中 lightMode 失效：将 lastKey 置为 null，
        // 下次 drawOrCache 时 needsRebuild 必然返回 true
        this.lastKey = null;
    }

    // ======================== 辅助 ========================

    private static boolean isLightMode() {
        return ThemeManager.getInstance().isLightMode();
    }

    /** 进入 fallback 模式——设定冷却时间，冷却期满后自动尝试恢复 GPU 重建。 */
    private void enterFallback() {
        this.nextGpuRetryMs = System.currentTimeMillis() + FALLBACK_RETRY_MS;
    }

    // ======================== 包装纹理 ========================

    /**
     * 包装 FBO 纹理的 AbstractTexture，使 {@link GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int, int, int, int, int)}
     * 能通过 CACHE_LOCATION 查找到 FBO 纹理进行渲染。
     */
    private static final class FboTexture extends AbstractTexture {
        FboTexture() {
            this.blur = true;
        }

        @Override
        public void load(@NotNull ResourceManager resourceManager) {
        }

        /** 设置本 FBO 的 OpenGL 纹理 ID。由 NineSliceGpuCache 在重建时调用。 */
        void setGlTextureId(int glId) {
            this.id = glId;
        }
    }
}
