package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 为 1.12.2 RTS 界面建立确定的固定管线基线，并在作用域结束后精确归还状态。
 *
 * <p>本类不绘制任何控件。它解决的是老版 {@code RenderItem} 的隐式副作用：数量文字、
 * 耐久条和空物品会走不同状态路径，若界面依赖这些路径“顺便”初始化光照、深度或混合，
 * 就会出现空槽灰白、有物品才恢复正常的帧间随机性。</p>
 */
public final class RtsGuiRenderState {
    private RtsGuiRenderState() {
    }

    public static Scope beginFrame() {
        Snapshot snapshot = new Snapshot();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return new Scope(snapshot);
    }

    public static Scope beginItem() {
        Snapshot snapshot = new Snapshot();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return new Scope(snapshot);
    }

    /**
     * 隔离 Forge 旧 GUI 工具的隐式状态修改。
     *
     * <p>例如 1.12.2 的 {@code GuiUtils.drawHoveringText} 会在返回前重新开启深度、
     * 标准物品光照和颜色材质。顶栏 tooltip 位于底栏之前绘制时，这些副作用会让
     * 同一帧后续的物品格和窗口全部变灰，因此所有这类调用都必须放在此作用域内。</p>
     */
    public static Scope preserveForExternalGuiCall() {
        return new Scope(new Snapshot());
    }

    public static final class Scope implements AutoCloseable {
        private final Snapshot snapshot;
        private boolean closed;

        private Scope(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            RenderHelper.disableStandardItemLighting();
            snapshot.restore();
        }
    }

    private static final class Snapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        private final boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHT0);
        private final boolean light1 = GL11.glIsEnabled(GL11.GL_LIGHT1);
        private final boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        private final boolean rescaleNormal = GL11.glIsEnabled(GL12Compat.GL_RESCALE_NORMAL);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float[] color = RtsGlStateQueries.currentColor();

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RtsGlStateRestorer.restoreCapability(GL11.GL_BLEND, blend);
            RtsGlStateRestorer.restoreCapability(GL11.GL_TEXTURE_2D, texture);
            RtsGlStateRestorer.restoreCapability(GL11.GL_CULL_FACE, cull);
            RtsGlStateRestorer.restoreCapability(GL11.GL_DEPTH_TEST, depth);
            RtsGlStateRestorer.restoreCapability(GL11.GL_ALPHA_TEST, alpha);
            RtsGlStateRestorer.restoreCapability(GL11.GL_LIGHTING, lighting);
            RtsGlStateRestorer.restoreCapability(GL11.GL_LIGHT0, light0);
            RtsGlStateRestorer.restoreCapability(GL11.GL_LIGHT1, light1);
            RtsGlStateRestorer.restoreCapability(GL11.GL_COLOR_MATERIAL, colorMaterial);
            if (rescaleNormal) GlStateManager.enableRescaleNormal();
            else GlStateManager.disableRescaleNormal();
            GlStateManager.depthMask(depthMask);
            GlStateManager.color(color[0], color[1], color[2], color[3]);
        }
    }

    /** LWJGL 2 的 GL12 常量在不同开发映射里类加载时机不稳定，仅收拢常量值。 */
    private static final class GL12Compat {
        private static final int GL_RESCALE_NORMAL = 0x803A;
    }
}
