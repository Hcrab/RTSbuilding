package com.rtsbuilding.rtsbuilding.client.rendering.selection;

import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * 1.12 已放置方块增量旋转手柄的世界渲染入口。
 *
 * <p>圆弧 ribbon、悬停加宽/提亮和末端菱形箭头沿用主线几何。缓冲完全由本类独占，
 * 不会结束 Minecraft 或调用方的共享缓冲；绘制后恢复深度、混合、纹理、剔除与线宽状态。</p>
 */
public final class PlacedBlockRotationHandleRenderer {
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(64 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(64 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private PlacedBlockRotationHandleRenderer() {
    }

    public static void render(Minecraft minecraft) {
        if (minecraft == null || !(minecraft.currentScreen instanceof BuilderScreen)
                || minecraft.world == null) {
            return;
        }
        BuilderScreen screen = (BuilderScreen) minecraft.currentScreen;
        PlacedBlockRotationHandles handles = screen.getRotationHandles();
        if (handles == null || !handles.hasTarget()) {
            return;
        }

        EnumFacing cameraForward = screen.currentCameraHorizontalDirection();
        handles.updateHover(minecraft.world, screen.currentRayOrigin(),
                screen.computeCursorRayDirection(), cameraForward);
        List<PlacedBlockRotationHandles.ArcHandle> arcs = handles.arcs(minecraft.world, cameraForward);
        if (arcs == null || arcs.isEmpty()) {
            return;
        }

        RenderManager renderManager = minecraft.getRenderManager();
        beginBuffers(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        try {
            for (PlacedBlockRotationHandles.ArcHandle arc : arcs) {
                appendArcGeometry(FILL_BUFFER, LINE_BUFFER, arc.center(), arc.planeNormal(), arc.points(),
                        arc.gesture() == handles.hoveredGesture());
            }
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        }
    }

    /**
     * 迁移期兼容入口。两个缓冲参数不会被本类读取、结束或上传。
     */
    public static void render(Minecraft minecraft,
            BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer) {
        render(minecraft);
    }

    static void appendArcGeometry(BufferBuilder fillBuffer, BufferBuilder lineBuffer,
            Vec3d center, Vec3d planeNormal, List<Vec3d> points, boolean hovered) {
        if (center == null || planeNormal == null || points == null || points.size() < 2) {
            return;
        }
        Color color = axisColor(planeNormal, hovered);
        double halfWidth = hovered ? 0.105D : 0.072D;
        float fillAlpha = hovered ? 0.72F : 0.38F;
        float lineAlpha = hovered ? 1.0F : 0.78F;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d first = points.get(i);
            Vec3d second = points.get(i + 1);
            Vec3d firstSide = first.subtract(center).normalize().scale(halfWidth);
            Vec3d secondSide = second.subtract(center).normalize().scale(halfWidth);
            Vec3d firstOuter = first.add(firstSide);
            Vec3d firstInner = first.subtract(firstSide);
            Vec3d secondOuter = second.add(secondSide);
            Vec3d secondInner = second.subtract(secondSide);

            addQuad(fillBuffer, firstOuter, secondOuter, secondInner, firstInner,
                    color, fillAlpha);
            addLine(lineBuffer, firstOuter, secondOuter, color, lineAlpha);
            addLine(lineBuffer, firstInner, secondInner, color, lineAlpha);
        }

        Vec3d last = points.get(points.size() - 1);
        Vec3d previous = points.get(points.size() - 2);
        Vec3d tangent = last.subtract(previous).normalize();
        Vec3d side = planeNormal.crossProduct(tangent).normalize();
        Vec3d tip = last.add(tangent.scale(0.19D));
        Vec3d back = last.subtract(tangent.scale(0.13D));
        Vec3d left = last.add(side.scale(hovered ? 0.18D : 0.15D));
        Vec3d right = last.subtract(side.scale(hovered ? 0.18D : 0.15D));
        addQuad(fillBuffer, tip, left, back, right, color, hovered ? 0.90F : 0.62F);
        addLine(lineBuffer, tip, left, color, lineAlpha);
        addLine(lineBuffer, left, back, color, lineAlpha);
        addLine(lineBuffer, back, right, color, lineAlpha);
        addLine(lineBuffer, right, tip, color, lineAlpha);
    }

    private static void addQuad(BufferBuilder buffer, Vec3d first, Vec3d second,
            Vec3d third, Vec3d fourth, Color color, float alpha) {
        addVertex(buffer, first, color, alpha);
        addVertex(buffer, second, color, alpha);
        addVertex(buffer, third, color, alpha);
        addVertex(buffer, fourth, color, alpha);
    }

    private static void addLine(BufferBuilder buffer, Vec3d first, Vec3d second,
            Color color, float alpha) {
        addVertex(buffer, first, color, alpha);
        addVertex(buffer, second, color, alpha);
    }

    private static void addVertex(BufferBuilder buffer, Vec3d point, Color color, float alpha) {
        buffer.pos(point.x, point.y, point.z)
                .color(color.r(), color.g(), color.b(), alpha)
                .endVertex();
    }

    private static void beginBuffers(double translateX, double translateY, double translateZ) {
        FILL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
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
            UPLOADER.draw(buffer);
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
            // 上传器可能已结束该私有缓冲。
        }
        buffer.reset();
    }

    private static void resetTranslations() {
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static Color axisColor(Vec3d planeNormal, boolean hovered) {
        Color base;
        if (Math.abs(planeNormal.y) > 0.5D) {
            base = new Color(0.36F, 1.00F, 0.42F);
        } else if (Math.abs(planeNormal.x) > 0.5D) {
            base = new Color(1.00F, 0.34F, 0.32F);
        } else {
            base = new Color(0.38F, 0.64F, 1.00F);
        }
        if (!hovered) {
            return base;
        }
        return new Color(
                base.r() + (1.0F - base.r()) * 0.22F,
                base.g() + (1.0F - base.g()) * 0.22F,
                base.b() + (1.0F - base.b()) * 0.22F);
    }

    private static final class Color {
        private final float r;
        private final float g;
        private final float b;

        private Color(float r, float g, float b) {
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
