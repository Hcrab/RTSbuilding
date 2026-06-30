package com.rtsbuilding.rtsbuilding.client.render;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * 无人机视角画面捕获服务——在 {@link RenderLevelStageEvent.Stage#AFTER_LEVEL} 阶段
 * 将 Minecraft 主渲染目标（即 RTS 摄像机的世界渲染画面）拷贝到一张 OpenGL 纹理中，
 * 供 {@link com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen} 作为背景显示。
 *
 * <p>架构概览：</p>
 * <ol>
 *   <li>Minecraft 以 RTS 摄像机（{@link com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity}）
 *       视角将世界渲染到 MainRenderTarget</li>
 *   <li>本服务在 {@code AFTER_LEVEL} 事件中通过 {@link GL30#glBlitFramebuffer}
 *       将主渲染目标拷贝到自有 FBO 的纹理附件</li>
 *   <li>BuilderScreen 渲染时通过 {@link CapturedFrameTexture} 将此纹理作为背景绘制</li>
 * </ol>
 *
 * <p>此方案使世界画面的缩放完全独立于 Minecraft GUI Scale 系统，
 * 无需 PoseStack 缩放 / 坐标反算 / 递归防溢出等复杂处理。</p>
 *
 * <p>单例模式 + 静态委托——{@link SubscribeEvent} 要求静态方法，
 * 但所有 OpenGL 资源状态由 {@link #INSTANCE} 实例管理，可测试、可 Mock。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ViewCaptureService {

    private ViewCaptureService() {}

    /** 单例实例——所有 OpenGL 资源由此实例管理 */
    private static final ViewCaptureService INSTANCE = new ViewCaptureService();

    /** 捕获帧纹理在 TextureManager 中注册的资源路径 */
    public static final ResourceLocation CAPTURED_FRAME = 
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "captured_frame");

    // ======================== 实例级 OpenGL 资源 ========================

    /** 自有 FBO 的 GL 对象 ID */
    private int captureFboId = -1;
    /** 自有 FBO 的颜色纹理附件 GL 对象 ID */
    private int captureTexId = -1;
    /** 当前纹理尺寸（GLFW 帧缓冲像素） */
    private int captureW = -1;
    private int captureH = -1;

    /** 注册到 TextureManager 的包装纹理 */
    private CapturedFrameTexture registeredTexture;

    // ======================== 初始化 ========================

    /**
     * 确保所有 OpenGL 资源已就绪。
     * <p>延迟初始化，在第一次捕获时自动调用。</p>
     */
    private void ensureResources(int fboW, int fboH) {
        if (fboW <= 0 || fboH <= 0) return;

        // 注册 CapturedFrameTexture（只需一次）
        if (registeredTexture == null) {
            registeredTexture = new CapturedFrameTexture();
            Minecraft.getInstance().getTextureManager().register(CAPTURED_FRAME, registeredTexture);
        }

        // 纹理尺寸变化时重建
        if (captureTexId >= 0 && captureFboId >= 0 && captureW == fboW && captureH == fboH) {
            return;
        }
        cleanup();
        createResources(fboW, fboH);
    }

    private void createResources(int w, int h) {
        // 创建纹理
        captureTexId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTexId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 创建 FBO
        captureFboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, captureTexId, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        captureW = w;
        captureH = h;

        // 更新包装纹理的 ID
        if (registeredTexture != null) {
            registeredTexture.setGlTextureId(captureTexId);
        }

        RtsbuildingMod.LOGGER.debug("ViewCapture: created {}x{} FBO+texture (fbo={}, tex={})",
                w, h, captureFboId, captureTexId);
    }

    /** 释放所有 OpenGL 资源。 */
    private void cleanup() {
        if (captureTexId >= 0) {
            GL11.glDeleteTextures(captureTexId);
            captureTexId = -1;
            if (registeredTexture != null) {
                registeredTexture.setGlTextureId(-1);
            }
        }
        if (captureFboId >= 0) {
            GL30.glDeleteFramebuffers(captureFboId);
            captureFboId = -1;
        }
        captureW = -1;
        captureH = -1;
    }

    // ======================== 事件回调（静态委托→实例） ========================

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        // 仅在 RTS 摄像机活跃时捕获
        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) return;

        INSTANCE.captureCurrentFrame();
    }

    // ======================== 帧捕获（实例方法） ========================

    /**
     * 将当前 GL 帧缓冲的颜色内容拷贝到自有纹理。
     */
    private void captureCurrentFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        int fboW = mc.getWindow().getWidth();
        int fboH = mc.getWindow().getHeight();
        if (fboW <= 0 || fboH <= 0) return;

        ensureResources(fboW, fboH);
        if (captureFboId < 0 || captureTexId < 0) return;

        int srcFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (srcFbo <= 0) return;

        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFboId);

        GL30.glBlitFramebuffer(
                0, fboH, fboW, 0,
                0, 0, fboW, fboH,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        // 强制捕获纹理 alpha = 1.0：Minecraft 主渲染目标的部分区域（如天空）
        // alpha 值可能为 0，导致后续 g.blit() 混合时透出底层黑底
        GL11.glColorMask(false, false, false, true);   // 只写 alpha
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // alpha = 1.0
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColorMask(true, true, true, true);      // 恢复

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, srcFbo);

        if (registeredTexture != null) {
            registeredTexture.setGlTextureId(captureTexId);
        }
    }

    // ======================== 公开 API（静态委托→实例） ========================

    /** 捕获纹理是否已就绪。 */
    public static boolean hasValidFrame() {
        return INSTANCE.captureTexId >= 0;
    }

    /** 返回当前帧捕获纹理的宽度。 */
    public static int getCaptureWidth() {
        return INSTANCE.captureW;
    }

    /** 返回当前帧捕获纹理的高度。 */
    public static int getCaptureHeight() {
        return INSTANCE.captureH;
    }

    /** 返回注册到 TextureManager 的 {@link ResourceLocation}。 */
    public static ResourceLocation getCapturedFrameLocation() {
        return CAPTURED_FRAME;
    }

    /** 返回单例实例（供测试/Mock 用）。 */
    public static ViewCaptureService getInstance() {
        return INSTANCE;
    }
}
