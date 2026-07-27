package com.rtsbuilding.rtsbuilding.client.rendering;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.AdvancedShapeSelectionBoxRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.culling.RtsCullingRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.BoundaryLineRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.ChunkGuideRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.InteractionTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.PlayerMoveTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.StorageRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.PlacedBlockRotationHandleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

/**
 * Forge 1.12 世界末尾阶段的 RTS 视觉总入口。
 *
 * <p>本类是这一阶段唯一的事件订阅点，并按固定顺序分派边界、交互、建造、剔除、
 * 蓝图和放置动画。已经拥有私有缓冲的子渲染器由它们自行提交；只有仍接受调用方
 * 缓冲的形状预览与选择手柄使用本类的私有缓冲。这里永远不会读取、结束或重置
 * {@code Tessellator.getInstance().getBuffer()}。</p>
 */
public final class RtsVisualOverlayRenderer {
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(2 * 1024 * 1024);
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(2 * 1024 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private RtsVisualOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null || minecraft.player == null) return;

        ClientRtsController controller = ClientRtsController.get();
        if (controller == null || !controller.hasBounds()) return;

        RenderManager manager = minecraft.getRenderManager();
        Vec3d camera = new Vec3d(manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);

        // 顺序与 1.21 主线一致：先环境引导，再交互/建造，最后覆盖型预览和动画。
        if (controller.isChunkCurtainVisible()) {
            ChunkGuideRenderer.renderChunkGuides(minecraft, camera);
        }

        double radius = controller.getMaxRadius();
        double minX = controller.getAnchorX() - radius;
        double maxX = controller.getAnchorX() + radius;
        double minZ = controller.getAnchorZ() - radius;
        double maxZ = controller.getAnchorZ() + radius;
        BoundaryLineRenderer.renderBarrierBoundary(
                minX, minZ, maxX, maxZ, controller.getAnchorY(), minecraft.world);
        StorageRenderer.renderLinkedStorages(minecraft, controller);
        InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller);
        PlayerMoveTargetRenderer.render(minecraft);

        renderCallerBufferedLayers(minecraft, manager);
        PlacedBlockRotationHandleRenderer.render(minecraft);
        RtsCullingRenderer.render();
        BlueprintCaptureRenderer.renderBlueprintCaptureBox();
        BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft);
        PlacementAnimationRenderer.render(minecraft);
    }

    private static void renderCallerBufferedLayers(Minecraft minecraft, RenderManager manager) {
        LINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        LINE_BUFFER.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        try {
            FILL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            FILL_BUFFER.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        } catch (RuntimeException exception) {
            discard(LINE_BUFFER);
            resetTranslations();
            throw exception;
        }

        try {
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, LINE_BUFFER, FILL_BUFFER);
            AdvancedShapeSelectionBoxRenderer.render(minecraft, LINE_BUFFER, FILL_BUFFER);
            drawPrivateBuffers();
        } catch (RuntimeException exception) {
            discard(LINE_BUFFER);
            discard(FILL_BUFFER);
            resetTranslations();
            throw exception;
        }
    }

    private static void drawPrivateBuffers() {
        GlSnapshot snapshot = GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            uploadOrDiscard(FILL_BUFFER);
            GlStateManager.glLineWidth(1.5F);
            uploadOrDiscard(LINE_BUFFER);
        } finally {
            resetTranslations();
            snapshot.restore();
        }
    }

    private static void uploadOrDiscard(BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) {
            UPLOADER.draw(buffer);
        } else {
            discard(buffer);
        }
    }

    private static void discard(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 仅清理本类私有缓冲；上传器可能已经结束了它。
        }
        buffer.reset();
    }

    private static void resetTranslations() {
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    /** 精确恢复总入口本身修改的兼容管线状态。 */
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

        private static GlSnapshot capture() {
            return new GlSnapshot();
        }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(
                    this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
            set(GL11.GL_BLEND, this.blend);
            set(GL11.GL_TEXTURE_2D, this.texture);
            set(GL11.GL_CULL_FACE, this.cull);
            set(GL11.GL_DEPTH_TEST, this.depth);
            GlStateManager.depthMask(this.depthMask);
            GlStateManager.glLineWidth(this.lineWidth);
            GlStateManager.color(this.color[0], this.color[1], this.color[2], this.color[3]);
        }

        private static float[] currentColor() {
            FloatBuffer values = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, values);
            return new float[] {values.get(0), values.get(1), values.get(2), values.get(3)};
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }
}
