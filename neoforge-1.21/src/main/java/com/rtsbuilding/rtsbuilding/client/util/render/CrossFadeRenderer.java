package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

public final class CrossFadeRenderer {

    
    private static final float ALMOST_ONE = 0.999f;
    
    private static final float ALMOST_ZERO = 0.001f;

    private CrossFadeRenderer() {}

    
    public static void render(GuiGraphics g, float t, Runnable normal, Runnable hovered) {
        if (t > ALMOST_ZERO && t < ALMOST_ONE) {
            try (BlendScope blend = BlendScope.crossFade()) {
                normal.run();
                g.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, t);
                try {
                    hovered.run();
                } finally {
                    g.flush();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        } else if (t >= ALMOST_ONE) {
            hovered.run();
        } else {
            normal.run();
        }
    }
}
