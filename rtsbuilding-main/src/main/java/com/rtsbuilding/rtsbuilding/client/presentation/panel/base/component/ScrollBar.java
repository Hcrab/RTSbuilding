package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component;

import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class ScrollBar {

    

    
    private static final int DEFAULT_SCROLL_STEP = 4;
    private static final int DEFAULT_TRACK_WIDTH = 2;
    
    private static final int MIN_THUMB_SIZE = 12;

    
    private static final int TRACK_THICKNESS = 7;
    
    private static final int THUMB_THICKNESS = TRACK_THICKNESS + 2;
    
    private static final int TOP_SHRINK = 1;

    
    public enum Orientation {
        VERTICAL,
        HORIZONTAL
    }

    public enum ThumbStyle {
        PILL,
        CIRCLE
    }

    

    private int scroll;
    private int maxScroll;
    private int totalContent;
    private int visibleContent;

    
    private boolean dragging;
    private int dragStartPos;
    private int dragStartScroll;

    private Orientation orientation = Orientation.VERTICAL;

    
    private boolean hovering;

    

    private static final int DEFAULT_TRACK_COLOR     = 0x662E3B4C;
    private static final int DEFAULT_THUMB_COLOR     = 0xFF586A80;
    private static final int DEFAULT_THUMB_HOVER_COLOR = 0xFF6A7E96;

    private int scrollStep = DEFAULT_SCROLL_STEP;
    private int trackColor = DEFAULT_TRACK_COLOR;
    private int thumbColor = DEFAULT_THUMB_COLOR;
    private int thumbHoverColor = DEFAULT_THUMB_HOVER_COLOR;
    private int trackWidth = DEFAULT_TRACK_WIDTH;
    private int minThumbSize = MIN_THUMB_SIZE;
    private ThumbStyle thumbStyle = ThumbStyle.PILL;

    

    public ScrollBar() {
    }

    

    
    public ScrollBar withOrientation(Orientation orientation) {
        this.orientation = orientation;
        return this;
    }

    
    public Orientation getOrientation() {
        return this.orientation;
    }

    

    
    public void setContent(int totalContent, int visibleContent) {
        this.totalContent = Math.max(1, totalContent);
        this.visibleContent = Math.max(1, visibleContent);
        this.maxScroll = Math.max(0, this.totalContent - this.visibleContent + 6);
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
    }

    
    public void setRange(int scroll, int maxScroll) {
        this.maxScroll = Math.max(0, maxScroll);
        this.scroll = Mth.clamp(scroll, 0, this.maxScroll);
        
        this.visibleContent = 1;
        this.totalContent = this.maxScroll + 1;
    }

    

    public int getScroll() {
        return this.scroll;
    }

    public int getMaxScroll() {
        return this.maxScroll;
    }

    
    public void setScroll(int scroll) {
        this.scroll = Math.max(0, scroll);
    }

    public boolean isDragging() {
        return this.dragging;
    }

    
    public boolean isVisible() {
        return this.maxScroll > 0;
    }

    

    
    public boolean handleScroll(double scrollY) {
        if (this.maxScroll <= 0) return false;
        int before = this.scroll;
        int step = scrollY > 0.0D ? -this.scrollStep : this.scrollStep;
        this.scroll = Mth.clamp(this.scroll + step, 0, this.maxScroll);
        return this.scroll != before;
    }

    
    public boolean handleClick(double mouseX, double mouseY, int barX, int barY, int barLength) {
        if (this.maxScroll <= 0) return false;
        int barYOff = barY + TOP_SHRINK;
        int barLenOff = barLength - TOP_SHRINK;
        if (!isInsideBar(mouseX, mouseY, barX, barYOff, barLenOff)) return false;

        int thumbLen = computeThumbLength(barLenOff);
        int thumbPos = computeThumbPos(barX, barYOff, barLenOff, thumbLen);

        
        double mouseAlong = orientation == Orientation.VERTICAL ? mouseY : mouseX;

        if (mouseAlong >= thumbPos && mouseAlong < thumbPos + thumbLen) {
            
            this.dragging = true;
            this.dragStartPos = (int) mouseAlong;
            this.dragStartScroll = this.scroll;
        } else {
            
            int pageStep = Math.max(1, (this.visibleContent * 3) / 4);
            this.scroll = Mth.clamp(
                    mouseAlong < thumbPos ? this.scroll - pageStep : this.scroll + pageStep,
                    0, this.maxScroll);
        }
        return true;
    }

    
    public boolean handleDrag(double mousePos, int barPos, int barLength) {
        if (!this.dragging || this.maxScroll <= 0) return false;

        int thumbLen = computeThumbLength(barLength);
        int availableTrack = barLength - thumbLen;
        if (availableTrack <= 0) return false;

        int before = this.scroll;
        int delta = (int) mousePos - this.dragStartPos;
        this.scroll = Mth.clamp(
                this.dragStartScroll + (delta * this.maxScroll + availableTrack / 2) / availableTrack,
                0, this.maxScroll);
        return this.scroll != before;
    }

    
    public void endDrag() {
        this.dragging = false;
    }

    

    
    public void render(GuiGraphics g, int barX, int barY, int barLength) {
        if (this.maxScroll <= 0) return;

        int renderY = barY + TOP_SHRINK;
        int renderLen = barLength - TOP_SHRINK;
        if (renderLen <= 0) return;
        int thumbLen = computeThumbLength(renderLen);
        int thumbPos = computeThumbPos(barX, renderY, renderLen, thumbLen);

        boolean active = this.dragging || this.hovering;
        int curThumbColor = active ? thumbHoverColor : thumbColor;

        if (orientation == Orientation.VERTICAL) {
            SdfRenderer.drawPill(g, barX, renderY, TRACK_THICKNESS, renderLen, trackColor);
            if (thumbStyle == ThumbStyle.CIRCLE) {
                int thumbW = Math.min(thumbLen, THUMB_THICKNESS);
                int thumbH = thumbW;
                int thumbOffX = barX - 1 + (THUMB_THICKNESS - thumbW) / 2;
                int thumbOffY = thumbPos + (thumbLen - thumbH) / 2;
                SdfRenderer.drawPill(g, thumbOffX, thumbOffY, thumbW, thumbH, curThumbColor);
            } else {
                SdfRenderer.drawPill(g, barX - 1, thumbPos, THUMB_THICKNESS, thumbLen, curThumbColor);
            }
        } else {
            SdfRenderer.drawPill(g, barX, renderY, renderLen, TRACK_THICKNESS, trackColor);
            if (thumbStyle == ThumbStyle.CIRCLE) {
                int thumbH = Math.min(thumbLen, THUMB_THICKNESS);
                int thumbW = thumbH;
                int thumbOffX = thumbPos + (thumbLen - thumbW) / 2;
                int thumbOffY = renderY - 1 + (THUMB_THICKNESS - thumbH) / 2;
                SdfRenderer.drawPill(g, thumbOffX, thumbOffY, thumbW, thumbH, curThumbColor);
            } else {
                SdfRenderer.drawPill(g, thumbPos, renderY - 1, thumbLen, THUMB_THICKNESS, curThumbColor);
            }
        }
    }

    

    
    public boolean isInsideBar(double mouseX, double mouseY, int barX, int barY, int barLength) {
        if (orientation == Orientation.VERTICAL) {
            return mouseX >= barX - 1
                    && mouseX < barX + TRACK_THICKNESS + 1
                    && mouseY >= barY
                    && mouseY < barY + barLength;
        } else {
            return mouseY >= barY - 1
                    && mouseY < barY + TRACK_THICKNESS + 1
                    && mouseX >= barX
                    && mouseX < barX + barLength;
        }
    }

    

    
    private int computeThumbLength(int barLength) {
        int thumbLen = barLength * this.visibleContent / this.totalContent;
        return Math.max(this.minThumbSize, Math.min(thumbLen, barLength));
    }

    
    private int computeThumbPos(int barX, int barY, int barLength, int thumbLen) {
        if (this.maxScroll <= 0) return orientation == Orientation.VERTICAL ? barY : barX;
        int barStart = orientation == Orientation.VERTICAL ? barY : barX;
        return barStart + (barLength - thumbLen) * this.scroll / this.maxScroll;
    }

    

    public ScrollBar withTrackColor(int color) {
        this.trackColor = color;
        return this;
    }

    public ScrollBar withThumbColor(int color) {
        this.thumbColor = color;
        return this;
    }

    public ScrollBar withThumbHoverColor(int color) {
        this.thumbHoverColor = color;
        return this;
    }

    public ScrollBar withScrollStep(int step) {
        this.scrollStep = Math.max(1, step);
        return this;
    }

    public ScrollBar withTrackWidth(int width) {
        this.trackWidth = width;
        return this;
    }

    public ScrollBar withMinThumbSize(int size) {
        this.minThumbSize = size;
        return this;
    }

    public ScrollBar withThumbStyle(ThumbStyle style) {
        this.thumbStyle = style;
        return this;
    }

    public int getTrackColor() {
        return trackColor;
    }

    public int getThumbColor() {
        return thumbColor;
    }

    public int getThumbHoverColor() {
        return thumbHoverColor;
    }

    public int getTrackWidth() {
        return trackWidth;
    }
}

