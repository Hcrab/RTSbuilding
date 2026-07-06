package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * FBO 资源管理器——轻量级 OpenGL 帧缓冲对象封装。
 *
 * <p>管理 FBO 的创建、绑定、解绑和清理生命周期，配合 {@link NineSliceGpuCache}
 * 实现 GPU 端的九宫格拼贴缓存，避免 CPU 像素拷贝的 JNI 开销。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 创建 FBO（自动创建颜色纹理附件）
 * GlFbo fbo = new GlFbo(256, 256);
 *
 * // 绑定为渲染目标
 * fbo.bind();
 * // ... 在此 FBO 上渲染 ...
 * fbo.unbind();
 *
 * // 清理
 * fbo.close();
 * }</pre>
 */
public final class GlFbo implements AutoCloseable {

    private int fboId = -1;
    private int texId = -1;
    private int width;
    private int height;
    private boolean disposed;

    /** 缓存的只读 FBO——避免每次 blitFromTexture 重复创建/删除 */
    private int cachedReadFboId = -1;
    /** 缓存只读 FBO 绑定的源纹理 ID */
    private int cachedReadTexId = -1;

    /**
     * 创建具有颜色纹理附件的 FBO。
     *
     * @param width  FBO 宽度（像素）
     * @param height FBO 高度（像素）
     */
    public GlFbo(int width, int height) {
        this.width = width;
        this.height = height;
        create();
    }

    /**
     * FBO 宽度。
     */
    public int getWidth() {
        return width;
    }

    /**
     * FBO 高度。
     */
    public int getHeight() {
        return height;
    }

    /**
     * 颜色纹理附件的 OpenGL 纹理 ID。
     */
    public int getTexId() {
        return texId;
    }

    /**
     * 绑定 FBO 为渲染目标。
     * <p>调用前需保存当前视口和帧缓冲状态（由调用方负责）。</p>
     */
    public void bind() {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL11.glViewport(0, 0, width, height);
    }

    /**
     * 解绑 FBO，恢复为默认帧缓冲。
     */
    public void unbind() {
        RenderSystem.assertOnRenderThreadOrInit();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * 将源纹理的指定区域通过 GPU blit 拷贝到本 FBO 的目标位置。
     * <p>使用 {@link GL30#glBlitFramebuffer} 实现 GPU 端像素拷贝，
     * 零 CPU 参与，适合在重建缓存时使用。</p>
     *
     * @param srcTexId  源纹理 OpenGL ID
     * @param srcTexW   源纹理宽度
     * @param srcTexH   源纹理高度
     * @param srcX      源区域左边界
     * @param srcY      源区域上边界（从底部算起，OpenGL 坐标系）
     * @param srcW      源区域宽度
     * @param srcH      源区域高度
     * @param dstX      目标区域左边界
     * @param dstY      目标区域上边界（从底部算起）
     * @param dstW      目标区域宽度
     * @param dstH      目标区域高度
     */
    public void blitFromTexture(int srcTexId, int srcTexW, int srcTexH,
                                 int srcX, int srcY, int srcW, int srcH,
                                 int dstX, int dstY, int dstW, int dstH) {
        // 使用缓存的只读 FBO 包裹源纹理，避免重复创建/删除
        if (cachedReadTexId != srcTexId) {
            if (cachedReadFboId >= 0) {
                GL30.glDeleteFramebuffers(cachedReadFboId);
            }
            cachedReadFboId = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cachedReadFboId);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, srcTexId, 0);
            cachedReadTexId = srcTexId;
        } else {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cachedReadFboId);
        }

        // 绑定本 FBO 为写入目标
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboId);

        // GPU blit：源矩形 → 目标矩形
        GL30.glBlitFramebuffer(
                srcX, srcY, srcX + srcW, srcY + srcH,
                dstX, dstY, dstX + dstW, dstY + dstH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        // 清理：解绑帧缓冲
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * 清空 FBO 颜色缓冲（透明黑色）。
     */
    public void clear() {
        bind();
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        unbind();
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        RenderSystem.assertOnRenderThreadOrInit();
        if (fboId >= 0) {
            GL30.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (texId >= 0) {
            GL11.glDeleteTextures(texId);
            texId = -1;
        }
        if (cachedReadFboId >= 0) {
            GL30.glDeleteFramebuffers(cachedReadFboId);
            cachedReadFboId = -1;
            cachedReadTexId = -1;
        }
        width = 0;
        height = 0;
    }

    /**
     * 调整 FBO 尺寸（重建资源）。
     *
     * @param newW 新宽度
     * @param newH 新高度
     */
    public void resize(int newW, int newH) {
        if (newW == width && newH == height) return;
        close();
        this.width = newW;
        this.height = newH;
        create();
    }

    // ======================== 内部 ========================

    private void create() {
        RenderSystem.assertOnRenderThreadOrInit();

        // 创建颜色纹理
        texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 创建 FBO 并附加纹理
        fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texId, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
