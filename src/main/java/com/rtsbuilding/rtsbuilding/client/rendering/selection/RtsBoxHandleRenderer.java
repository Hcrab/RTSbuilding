package com.rtsbuilding.rtsbuilding.client.rendering.selection;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingAxisHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.Set;

/**
 * 1.12 世界空间盒选手柄渲染器。
 *
 * <p>本类只负责六向手柄的视觉表达，不负责射线命中或修改选区。填充和线框使用本类独占的
 * {@link BufferBuilder}，绝不 begin、finish、reset 或上传调用方/Tessellator 的共享缓冲。
 * 每次绘制还会恢复本类改动过的 OpenGL 状态，避免污染后续世界渲染。</p>
 */
public final class RtsBoxHandleRenderer {
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(64 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(64 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private static final float HANDLE_X_R = 1.00F;
    private static final float HANDLE_X_G = 0.34F;
    private static final float HANDLE_X_B = 0.32F;
    private static final float HANDLE_Y_R = 0.36F;
    private static final float HANDLE_Y_G = 1.00F;
    private static final float HANDLE_Y_B = 0.42F;
    private static final float HANDLE_Z_R = 0.38F;
    private static final float HANDLE_Z_G = 0.64F;
    private static final float HANDLE_Z_B = 1.00F;
    private static final float ACTIVE_R = 1.00F;
    private static final float ACTIVE_G = 0.78F;
    private static final float ACTIVE_B = 0.18F;

    private RtsBoxHandleRenderer() {
    }

    public static void renderAxisHandles(AxisAlignedBB box,
            EnumFacing hoveredDirection, EnumFacing activeDirection) {
        renderAxisHandles(box, hoveredDirection, activeDirection, null);
    }

    public static void renderAxisHandles(AxisAlignedBB box, EnumFacing hoveredDirection,
            EnumFacing activeDirection, Set<EnumFacing> allowedDirections) {
        if (box == null) {
            return;
        }

        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        beginBuffers(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        try {
            appendAxisHandles(FILL_BUFFER, LINE_BUFFER, box,
                    hoveredDirection, activeDirection, allowedDirections);
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        }
    }

    /**
     * 迁移期兼容入口。两个缓冲参数只用于兼容尚未迁完的调用点，本类不会触碰它们。
     */
    public static void renderAxisHandles(BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer,
            AxisAlignedBB box, EnumFacing hoveredDirection, EnumFacing activeDirection) {
        renderAxisHandles(box, hoveredDirection, activeDirection, null);
    }

    /**
     * 迁移期兼容入口。调用方缓冲的生命周期仍完全属于调用方。
     */
    public static void renderAxisHandles(BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer,
            AxisAlignedBB box, EnumFacing hoveredDirection, EnumFacing activeDirection,
            Set<EnumFacing> allowedDirections) {
        renderAxisHandles(box, hoveredDirection, activeDirection, allowedDirections);
    }

    static void appendAxisHandles(BufferBuilder fillBuffer, BufferBuilder lineBuffer,
            AxisAlignedBB box, EnumFacing hoveredDirection, EnumFacing activeDirection,
            Set<EnumFacing> allowedDirections) {
        for (RtsCullingAxisHandle.Handle handle
                : RtsCullingAxisHandle.handles(box, allowedDirections)) {
            boolean hovered = handle.direction() == hoveredDirection;
            boolean active = handle.direction() == activeDirection;
            AxisColor axisColor = color(handle.axis());
            AxisColor drawColor = active ? new AxisColor(ACTIVE_R, ACTIVE_G, ACTIVE_B)
                    : hovered ? highlight(axisColor) : axisColor;
            float fillAlpha = active ? 0.58F : hovered ? 0.42F : 0.22F;
            float lineAlpha = active ? 1.00F : hovered ? 0.95F : 0.70F;

            if (hovered && !active) {
                appendHandleBox(fillBuffer, lineBuffer, handle.shaft().grow(0.05D),
                        drawColor, 0.10F, 0.30F);
                appendHandleBox(fillBuffer, lineBuffer, handle.head().grow(0.07D),
                        drawColor, 0.12F, 0.38F);
            }
            if (active) {
                appendHandleBox(fillBuffer, lineBuffer, handle.shaft().grow(0.06D),
                        drawColor, 0.16F, 0.42F);
                appendHandleBox(fillBuffer, lineBuffer, handle.head().grow(0.08D),
                        drawColor, 0.20F, 0.54F);
            }
            appendHandleBox(fillBuffer, lineBuffer, handle.shaft(), drawColor, fillAlpha, lineAlpha);
            appendHandleBox(fillBuffer, lineBuffer, handle.head(), drawColor, fillAlpha, lineAlpha);
        }
    }

    private static void appendHandleBox(BufferBuilder fillBuffer, BufferBuilder lineBuffer,
            AxisAlignedBB box, AxisColor color, float fillAlpha, float lineAlpha) {
        RenderGlobal.addChainedFilledBoxVertices(fillBuffer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                color.r(), color.g(), color.b(), fillAlpha);
        RenderGlobal.drawBoundingBox(lineBuffer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                color.r(), color.g(), color.b(), lineAlpha);
    }

    private static void beginBuffers(double translateX, double translateY, double translateZ) {
        // RenderGlobal#addChainedFilledBoxVertices 输出的是连续 quad strip（每盒 30 顶点）。
        FILL_BUFFER.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        FILL_BUFFER.setTranslation(translateX, translateY, translateZ);
        try {
            LINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
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
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);

            uploadOrReset(FILL_BUFFER);
            GlStateManager.glLineWidth(2.0F);
            uploadOrReset(LINE_BUFFER);
        } finally {
            resetTranslations();
            state.restore();
        }
    }

    private static void uploadOrReset(BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) {
            RtsOwnedBufferUploader.draw(buffer);
        } else {
            discard(buffer);
        }
    }

    private static void discardOwnedBuffers() {
        discard(FILL_BUFFER);
        discard(LINE_BUFFER);
        resetTranslations();
    }

    private static void discard(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 上传器可能已经结束了该私有缓冲，仅需确保 reset。
        }
        buffer.reset();
    }

    private static void resetTranslations() {
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static AxisColor color(EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return new AxisColor(HANDLE_X_R, HANDLE_X_G, HANDLE_X_B);
            case Y:
                return new AxisColor(HANDLE_Y_R, HANDLE_Y_G, HANDLE_Y_B);
            case Z:
                return new AxisColor(HANDLE_Z_R, HANDLE_Z_G, HANDLE_Z_B);
            default:
                throw new AssertionError(axis);
        }
    }

    private static AxisColor highlight(AxisColor color) {
        return new AxisColor(
                color.r() + (1.0F - color.r()) * 0.18F,
                color.g() + (1.0F - color.g()) * 0.18F,
                color.b() + (1.0F - color.b()) * 0.18F);
    }

    private static final class AxisColor {
        private final float r;
        private final float g;
        private final float b;

        private AxisColor(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        private float r() { return this.r; }
        private float g() { return this.g; }
        private float b() { return this.b; }
    }

    /** 只保存并恢复本渲染器会修改的兼容性状态。 */
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

        private static GlSnapshot capture() {
            return new GlSnapshot();
        }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(
                    this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
            setBlend(this.blend);
            setTexture(this.texture);
            setCull(this.cull);
            setDepth(this.depth);
            GlStateManager.depthMask(this.depthMask);
            GlStateManager.glLineWidth(this.lineWidth);
            GlStateManager.resetColor();
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
