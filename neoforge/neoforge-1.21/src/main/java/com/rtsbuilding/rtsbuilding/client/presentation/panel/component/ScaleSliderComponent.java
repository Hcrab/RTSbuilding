package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

public class ScaleSliderComponent {

    

    
    private static final int TRACK_H = 9;
    private static final int TRACK_COLOR = 0x662E3B4C;
    
    private static final int THUMB_H = 9;
    private static final int THUMB_W = 9;
    private static final int THUMB_COLOR = 0xFF586A80;
    private static final int THUMB_COLOR_ACTIVE = 0xFF6A7E96;
    
    private static final int TRACK_CLICK_PADDING = 3;

    

    
    private static final double LERP_BASE = 0.12;
    
    private static final double LERP_DISTANCE_BOOST = 3.0;

    

    private boolean dragging = false;
    
    private double clickMouseX = 0;
    
    private double clickValue = 0;
    
    private double valuePerPixel = 0;
    
    private int renderedThumbX = 0;

    

    
    private double smoothValue = 0;
    
    private double externalValue = 0;
    
    private boolean initialized;

    

    
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       int trackX, int trackY, int trackW,
                       double min, double max, double value) {
        if (trackW <= 0) return;

        this.externalValue = value;

        
        if (!initialized) {
            initialized = true;
            smoothValue = value;
        }

        
        
        if (dragging) {
            smoothValue = value;
        } else {
            double diff = value - smoothValue;
            double speed = Mth.clamp(LERP_BASE + Math.abs(diff) * LERP_DISTANCE_BOOST, 0.0, 1.0);
            smoothValue += diff * speed;
        }

        
        boolean active = this.dragging;

        SdfRenderer.drawPill(g, trackX, trackY, trackW, TRACK_H, TRACK_COLOR);

        int thumbX = trackX + (int) Math.round((smoothValue - min) / (max - min) * (trackW - THUMB_W));
        this.renderedThumbX = thumbX;
        int thumbY = trackY + (TRACK_H - THUMB_H) / 2;
        int thumbColor = active ? THUMB_COLOR_ACTIVE : THUMB_COLOR;
        SdfRenderer.drawPill(g, thumbX, thumbY, THUMB_W, THUMB_H, thumbColor);
    }

    

    
    @Nullable
    public Double handleClick(double mouseX, double mouseY,
                              int trackX, int trackY, int trackW,
                              double min, double max) {
        if (trackW <= 0) return null;

        
        
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_H + TRACK_CLICK_PADDING
                && mouseX >= renderedThumbX && mouseX < renderedThumbX + THUMB_W) {

            
            clickMouseX = mouseX;
            clickValue = smoothValue;
            double pixelRange = Math.max(1, trackW - THUMB_W);
            valuePerPixel = (max - min) / pixelRange;
            dragging = true;

            return null;
        }
        return null;
    }

    

    
    @Nullable
    public Double handleScroll(double mouseX, double mouseY, double scrollY,
                               int trackX, int trackY, int trackW,
                               double min, double max) {
        if (trackW <= 0) return null;

        
        if (mouseY >= trackY - TRACK_CLICK_PADDING
                && mouseY < trackY + TRACK_H + TRACK_CLICK_PADDING
                && mouseX >= trackX && mouseX < trackX + trackW) {

            double step = 0.1; 
            double newValue = smoothValue + (scrollY > 0 ? step : -step);
            newValue = Mth.clamp(newValue, min, max);
            newValue = Math.round(newValue * 100.0) / 100.0;
            return newValue;
        }
        return null;
    }

    

    
    public double handleDrag(double mouseX, int trackX, int trackW,
                             double min, double max) {
        if (!dragging || trackW <= 0) return min;

        double dx = mouseX - clickMouseX;
        double newValue = clickValue + dx * valuePerPixel;
        newValue = Mth.clamp(newValue, min, max);
        newValue = Math.round(newValue * 10.0) / 10.0;
        return newValue;
    }

    

    
    public boolean isDragging() {
        return dragging;
    }

    
    public void endDrag() {
        this.dragging = false;
    }

    
    public double getSmoothValue() {
        return smoothValue;
    }
}
