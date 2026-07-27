package com.rtsbuilding.rtsbuilding.client.rendering.culling;

import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

/**
 * 范围剔除盒子的 1.12 世界空间预览渲染器。
 *
 * <p>普通、选中、悬停和草稿盒沿用主线颜色与透明度；选中盒的六向手柄交给共享的
 * {@link RtsBoxHandleRenderer}。盒体使用本类私有缓冲并完整恢复 GL 状态，不接管任何调用方缓冲。</p>
 */
public final class RtsCullingRenderer {
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(512 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(256 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private static final float BLUE_R = 0.20F;
    private static final float BLUE_G = 0.56F;
    private static final float BLUE_B = 1.00F;
    private static final float YELLOW_R = 1.00F;
    private static final float YELLOW_G = 0.82F;
    private static final float YELLOW_B = 0.16F;
    private static final float SELECT_R = 0.56F;
    private static final float SELECT_G = 0.84F;
    private static final float SELECT_B = 1.00F;

    private RtsCullingRenderer() {
    }

    public static void render() {
        RtsCullingManager manager = RtsCullingClientState.activeManager();
        if (manager == null || !manager.isManagementMode()) return;

        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        beginOwnedBuffers(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        try {
            for (RtsCullingBox box : manager.boxes()) {
                AxisAlignedBB renderBox = manager.renderAabb(box);
                if (box.id() == manager.hoveredId()) {
                    appendBox(FILL_BUFFER, LINE_BUFFER, renderBox,
                            YELLOW_R, YELLOW_G, YELLOW_B, 0.22F, 0.95F);
                } else if (box.id() == manager.selectedId()) {
                    appendBox(FILL_BUFFER, LINE_BUFFER, renderBox,
                            SELECT_R, SELECT_G, SELECT_B, 0.18F, 0.98F);
                } else {
                    appendBox(FILL_BUFFER, LINE_BUFFER, renderBox,
                            BLUE_R, BLUE_G, BLUE_B, 0.12F, 0.82F);
                }
            }
            RtsCullingBox preview = manager.previewBox();
            if (preview != null) {
                appendBox(FILL_BUFFER, LINE_BUFFER, preview.asAabb(),
                        YELLOW_R, YELLOW_G, YELLOW_B, 0.16F, 0.92F);
            }
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        }

        RtsCullingBox selected = manager.selectedBox().orElse(null);
        if (selected != null) {
            RtsBoxHandleRenderer.renderAxisHandles(manager.renderAabb(selected),
                    manager.hoveredHandleDirection(), manager.activeHandleDirection());
        }
    }

    /** 迁移期兼容入口：参数缓冲仍完全归调用方管理。 */
    public static void render(BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer,
            BufferBuilder callerHandleLineBuffer, BufferBuilder callerHandleFillBuffer) {
        render();
    }

    static void appendBox(BufferBuilder fillBuffer, BufferBuilder lineBuffer, AxisAlignedBB box,
            float r, float g, float b, float fillAlpha, float lineAlpha) {
        double minX = box.minX - 0.01D;
        double minY = box.minY - 0.01D;
        double minZ = box.minZ - 0.01D;
        double maxX = box.maxX + 0.01D;
        double maxY = box.maxY + 0.01D;
        double maxZ = box.maxZ + 0.01D;
        RenderGlobal.addChainedFilledBoxVertices(fillBuffer,
                minX, minY, minZ, maxX, maxY, maxZ, r, g, b, fillAlpha);
        RenderGlobal.drawBoundingBox(lineBuffer,
                minX, minY, minZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
    }

    private static void beginOwnedBuffers(double translateX, double translateY, double translateZ) {
        FILL_BUFFER.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        FILL_BUFFER.setTranslation(translateX, translateY, translateZ);
        try {
            // RenderGlobal#drawBoundingBox 输出一条带透明断点的连续线带。
            LINE_BUFFER.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            LINE_BUFFER.setTranslation(translateX, translateY, translateZ);
        } catch (RuntimeException exception) {
            discard(FILL_BUFFER);
            throw exception;
        }
    }

    private static void drawOwnedBuffers() {
        GlSnapshot state = GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            uploadOrReset(FILL_BUFFER);
            GlStateManager.glLineWidth(1.5F);
            uploadOrReset(LINE_BUFFER);
        } finally {
            resetTranslations();
            state.restore();
        }
    }

    private static void uploadOrReset(BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) UPLOADER.draw(buffer); else discard(buffer);
    }

    private static void discardOwnedBuffers() {
        discard(FILL_BUFFER); discard(LINE_BUFFER); resetTranslations();
    }

    private static void discard(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 仅清理本类私有缓冲。
        }
        buffer.reset();
    }

    private static void resetTranslations() {
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static final class GlSnapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float[] color = currentColor();

        private static GlSnapshot capture() { return new GlSnapshot(); }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            setBlend(blend); setTexture(texture); setCull(cull); setDepth(depth);
            GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);
            GlStateManager.color(color[0], color[1], color[2], color[3]);
        }

        private static float[] currentColor() {
            FloatBuffer values = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, values);
            return new float[] {values.get(0), values.get(1), values.get(2), values.get(3)};
        }

        private static void setBlend(boolean enabled) {
            if (enabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
        private static void setTexture(boolean enabled) {
            if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
        private static void setCull(boolean enabled) {
            if (enabled) GlStateManager.enableCull(); else GlStateManager.disableCull();
        }
        private static void setDepth(boolean enabled) {
            if (enabled) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }
}
