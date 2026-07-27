package com.rtsbuilding.rtsbuilding.client.render;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ViewCaptureService {

    private ViewCaptureService() {}

    
    private static final ViewCaptureService INSTANCE = new ViewCaptureService();

    
    public static final ResourceLocation CAPTURED_FRAME = 
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "captured_frame");

    

    
    private int captureFboId = -1;
    
    private int captureTexId = -1;
    
    private int captureW = -1;
    private int captureH = -1;

    
    private CapturedFrameTexture registeredTexture;

    

    
    private void ensureResources(int fboW, int fboH) {
        if (fboW <= 0 || fboH <= 0) return;

        
        if (registeredTexture == null) {
            registeredTexture = new CapturedFrameTexture();
            Minecraft.getInstance().getTextureManager().register(CAPTURED_FRAME, registeredTexture);
        }

        
        if (captureTexId >= 0 && captureFboId >= 0 && captureW == fboW && captureH == fboH) {
            return;
        }
        cleanup();
        createResources(fboW, fboH);
    }

    private void createResources(int w, int h) {
        
        captureTexId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTexId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        
        captureFboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, captureTexId, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        captureW = w;
        captureH = h;

        
        if (registeredTexture != null) {
            registeredTexture.setGlTextureId(captureTexId);
        }

        RtsbuildingMod.LOGGER.debug("ViewCapture: created {}x{} FBO+texture (fbo={}, tex={})",
                w, h, captureFboId, captureTexId);
    }

    
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

    

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        
        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) return;

        INSTANCE.captureCurrentFrame();
    }

    

    
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

        
        
        GL11.glColorMask(false, false, false, true);   
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColorMask(true, true, true, true);      

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, srcFbo);

        if (registeredTexture != null) {
            registeredTexture.setGlTextureId(captureTexId);
        }
    }

    

    
    public static boolean hasValidFrame() {
        return INSTANCE.captureTexId >= 0;
    }

    
    public static int getCaptureWidth() {
        return INSTANCE.captureW;
    }

    
    public static int getCaptureHeight() {
        return INSTANCE.captureH;
    }

    
    public static ResourceLocation getCapturedFrameLocation() {
        return CAPTURED_FRAME;
    }

    
    public static ViewCaptureService getInstance() {
        return INSTANCE;
    }
}
