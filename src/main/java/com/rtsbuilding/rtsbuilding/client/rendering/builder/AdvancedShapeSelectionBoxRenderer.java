package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import java.util.Set;

/**
 * 选择范围与六向手柄的 1.12 兼容入口。普通模式保留蓝色包围线，高级模式继续交给
 * {@link RtsBoxHandleRenderer} 绘制可用方向、悬停方向和拖拽方向。反射仅用于隔离尚未
 * 迁移完成的 BuilderScreen 编译类型；方法齐备后走的是同一组明确接口。
 */
public final class AdvancedShapeSelectionBoxRenderer {
    private static final BufferBuilder OUTLINE_BUFFER = new BufferBuilder(32 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private AdvancedShapeSelectionBoxRenderer() {}

    public static void render(Minecraft mc, BufferBuilder callerLines, BufferBuilder callerFills) {
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (screen == null || !"BuilderScreen".equals(screen.getClass().getSimpleName())) return;
        try {
            Object controller = screen.getClass().getMethod("getShapeController").invoke(screen);
            AxisAlignedBB box = (AxisAlignedBB) controller.getClass()
                    .getMethod("shapeSelectionRenderAabb").invoke(controller);
            if (box == null) return;
            boolean quickDestroy = (Boolean) screen.getClass()
                    .getMethod("isQuickBuildRangeDestroyMode").invoke(screen);
            if (!quickDestroy) renderOutline(box.grow(0.015D));
            boolean advanced = (Boolean) screen.getClass().getMethod("isAdvancedShapeMode").invoke(screen);
            if (!advanced) return;
            EnumFacing hovered = (EnumFacing) controller.getClass()
                    .getMethod("advancedRangeDestroyHoveredHandle").invoke(controller);
            EnumFacing active = (EnumFacing) controller.getClass()
                    .getMethod("advancedRangeDestroyActiveHandle").invoke(controller);
            @SuppressWarnings("unchecked")
            Set<EnumFacing> allowed = (Set<EnumFacing>) controller.getClass()
                    .getMethod("advancedRangeDestroyAllowedHandleDirections").invoke(controller);
            RtsBoxHandleRenderer.renderAxisHandles(callerLines, callerFills, box, hovered, active, allowed);
        } catch (ReflectiveOperationException ignored) {
            // BuilderScreen 本体迁移前明确跳过，避免现代类签名使 1.12 客户端类加载失败。
        }
    }

    private static void renderOutline(AxisAlignedBB box) {
        RenderManager manager = net.minecraft.client.renderer.entity.RenderManager.instance;
        OUTLINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        OUTLINE_BUFFER.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyRenderGeometry.drawBoundingBox(OUTLINE_BUFFER, box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ, 0.30F, 0.75F, 1.00F, 0.82F);
        UltimineGhostRenderer.GlSnapshot gl = UltimineGhostRenderer.GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            RtsOwnedBufferUploader.draw(OUTLINE_BUFFER);
        } finally {
            OUTLINE_BUFFER.setTranslation(0, 0, 0);
            gl.restore();
        }
    }
}
