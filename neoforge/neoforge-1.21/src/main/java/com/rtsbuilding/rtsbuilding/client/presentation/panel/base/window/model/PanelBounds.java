package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.model;

public final class PanelBounds {

    private int x;
    private int y;
    private int width;
    private int height;
    private int defaultWidth;
    private int defaultHeight;
    private boolean initialized;

    public PanelBounds(int defaultWidth, int defaultHeight) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    
    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    

    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean v) { this.initialized = v; }

    
    public boolean needsSizeInit() {
        return width <= 0 || height <= 0;
    }

    
    public boolean needsInit() {
        return !initialized;
    }

    

    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultHeight() { return defaultHeight; }
    public void setDefaults(int defaultWidth, int defaultHeight) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    
    public void resetToDefaults() {
        this.width = this.defaultWidth;
        this.height = this.defaultHeight;
    }
}
