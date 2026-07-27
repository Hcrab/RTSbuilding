package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

public record BlendScope(boolean wasEnabled) implements AutoCloseable {

    
    public static BlendScope normal() {
        boolean was = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return new BlendScope(was);
    }

    
    public static BlendScope crossFade() {
        boolean was = GL11.glIsEnabled(GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        return new BlendScope(was);
    }

    @Override
    public void close() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (!wasEnabled()) {
            RenderSystem.disableBlend();
        }
    }
}
