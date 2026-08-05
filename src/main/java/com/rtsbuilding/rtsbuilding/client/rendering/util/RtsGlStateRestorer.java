package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * 通过 Minecraft 自己的状态管理器归还固定管线状态。
 *
 * <p>本类只负责 Forge 1.12.2 渲染器会临时修改的能力开关和纹理绑定，不负责抓取快照。
 * 不能用 {@code GL11.glEnable/glDisable/glBindTexture} 直接归还这些状态：那会改变显卡状态，
 * 却不更新 {@link GlStateManager} 的缓存，随后 vanilla 可能跳过必要的状态切换，表现为世界黑屏、
 * GUI 灰白或贴图错绑。</p>
 */
public final class RtsGlStateRestorer {
    private RtsGlStateRestorer() {
    }

    public static void restoreCapability(int capability, boolean enabled) {
        switch (capability) {
            case GL11.GL_BLEND:
                if (enabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
                return;
            case GL11.GL_TEXTURE_2D:
                if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
                return;
            case GL11.GL_CULL_FACE:
                if (enabled) GlStateManager.enableCull(); else GlStateManager.disableCull();
                return;
            case GL11.GL_DEPTH_TEST:
                if (enabled) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
                return;
            case GL11.GL_ALPHA_TEST:
                if (enabled) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
                return;
            case GL11.GL_LIGHTING:
                if (enabled) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
                return;
            case GL11.GL_LIGHT0:
                if (enabled) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
                return;
            case GL11.GL_LIGHT1:
                if (enabled) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
                return;
            case GL11.GL_COLOR_MATERIAL:
                if (enabled) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
                return;
            case GL11.GL_FOG:
                if (enabled) GlStateManager.enableFog(); else GlStateManager.disableFog();
                return;
            case GL11.GL_POLYGON_OFFSET_FILL:
                if (enabled) GlStateManager.enablePolygonOffset(); else GlStateManager.disablePolygonOffset();
                return;
            default:
                throw new IllegalArgumentException("不支持归还的 GL capability: " + capability);
        }
    }

    public static void restoreTextureBinding(int textureId) {
        GlStateManager.bindTexture(textureId);
    }
}
