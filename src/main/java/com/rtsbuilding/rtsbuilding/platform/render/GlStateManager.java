package com.rtsbuilding.rtsbuilding.platform.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * 把后续版本的 GlStateManager 调用集中映射到 1.7.10 的固定管线 OpenGL。
 *
 * <p>这个类只负责等价的状态切换，不缓存也不猜测调用方状态。RTS 渲染器现有的
 * 快照/恢复逻辑仍是状态所有者，因此不会再次引入 1.12.2 移植早期的黑屏和灰白 UI 问题。</p>
 */
public final class GlStateManager {
    private GlStateManager() {
    }

    public static void pushMatrix() { GL11.glPushMatrix(); }
    public static void popMatrix() { GL11.glPopMatrix(); }
    public static void translate(double x, double y, double z) { GL11.glTranslated(x, y, z); }
    public static void scale(double x, double y, double z) { GL11.glScaled(x, y, z); }
    public static void rotate(float angle, float x, float y, float z) { GL11.glRotatef(angle, x, y, z); }
    public static void color(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
    public static void resetColor() { color(1.0F, 1.0F, 1.0F, 1.0F); }
    public static void glLineWidth(float width) { GL11.glLineWidth(width); }
    public static void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
    public static void bindTexture(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
    public static void doPolygonOffset(float factor, float units) { GL11.glPolygonOffset(factor, units); }

    public static void enableBlend() { enable(GL11.GL_BLEND); }
    public static void disableBlend() { disable(GL11.GL_BLEND); }
    public static void enableTexture2D() { enable(GL11.GL_TEXTURE_2D); }
    public static void disableTexture2D() { disable(GL11.GL_TEXTURE_2D); }
    public static void enableDepth() { enable(GL11.GL_DEPTH_TEST); }
    public static void disableDepth() { disable(GL11.GL_DEPTH_TEST); }
    public static void enableCull() { enable(GL11.GL_CULL_FACE); }
    public static void disableCull() { disable(GL11.GL_CULL_FACE); }
    public static void enableAlpha() { enable(GL11.GL_ALPHA_TEST); }
    public static void disableAlpha() { disable(GL11.GL_ALPHA_TEST); }
    public static void enableLighting() { enable(GL11.GL_LIGHTING); }
    public static void disableLighting() { disable(GL11.GL_LIGHTING); }
    public static void enableLight(int light) { enable(GL11.GL_LIGHT0 + light); }
    public static void disableLight(int light) { disable(GL11.GL_LIGHT0 + light); }
    public static void enableColorMaterial() { enable(GL11.GL_COLOR_MATERIAL); }
    public static void disableColorMaterial() { disable(GL11.GL_COLOR_MATERIAL); }
    public static void enableFog() { enable(GL11.GL_FOG); }
    public static void disableFog() { disable(GL11.GL_FOG); }
    public static void enableRescaleNormal() { enable(GL12.GL_RESCALE_NORMAL); }
    public static void disableRescaleNormal() { disable(GL12.GL_RESCALE_NORMAL); }
    public static void enablePolygonOffset() { enable(GL11.GL_POLYGON_OFFSET_FILL); }
    public static void disablePolygonOffset() { disable(GL11.GL_POLYGON_OFFSET_FILL); }

    public static void blendFunc(int source, int destination) {
        GL11.glBlendFunc(source, destination);
    }

    public static void tryBlendFuncSeparate(SourceFactor sourceRgb, DestFactor destinationRgb,
            SourceFactor sourceAlpha, DestFactor destinationAlpha) {
        tryBlendFuncSeparate(sourceRgb.value, destinationRgb.value, sourceAlpha.value, destinationAlpha.value);
    }

    public static void tryBlendFuncSeparate(int sourceRgb, int destinationRgb,
            int sourceAlpha, int destinationAlpha) {
        GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    private static void enable(int capability) { GL11.glEnable(capability); }
    private static void disable(int capability) { GL11.glDisable(capability); }

    public enum SourceFactor {
        ZERO(GL11.GL_ZERO), ONE(GL11.GL_ONE), SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR), DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR), SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA), DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA), SRC_ALPHA_SATURATE(GL11.GL_SRC_ALPHA_SATURATE);

        private final int value;
        SourceFactor(int value) { this.value = value; }
    }

    public enum DestFactor {
        ZERO(GL11.GL_ZERO), ONE(GL11.GL_ONE), SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR), DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR), SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA), DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA);

        private final int value;
        DestFactor(int value) { this.value = value; }
    }
}
