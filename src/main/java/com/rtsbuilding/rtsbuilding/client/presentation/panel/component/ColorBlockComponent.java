package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import net.minecraft.client.gui.GuiGraphics;


public class ColorBlockComponent {

    public static final int DEFAULT_SIZE = 8;

    
    public void render(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + size, color);
    }

    
    public void render(GuiGraphics g, int x, int y, int color) {
        render(g, x, y, DEFAULT_SIZE, color);
    }
}
