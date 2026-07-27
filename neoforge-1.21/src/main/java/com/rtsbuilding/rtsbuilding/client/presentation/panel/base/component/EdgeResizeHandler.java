package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component;

public final class EdgeResizeHandler {

    
    public enum Orientation {
        
        HORIZONTAL,
        
        VERTICAL
    }

    
    public enum Side {
        
        LEADING,
        
        TRAILING
    }

    
    public static final int EDGE_THICKNESS = 5;

    private final Orientation orientation;
    private final Side side;

    
    private boolean active;
    
    private int startPos;
    
    private int startSize;
    
    private int screenSize;
    
    private int minSize;
    
    private int maxScreenRatio = 4;

    public EdgeResizeHandler(Orientation orientation, Side side) {
        this.orientation = orientation;
        this.side = side;
    }

    public EdgeResizeHandler(Orientation orientation, Side side, int minSize) {
        this(orientation, side);
        this.minSize = minSize;
    }

    

    
    public EdgeResizeHandler withMinSize(int minSize) {
        this.minSize = minSize;
        return this;
    }

    
    public EdgeResizeHandler withMaxScreenRatio(int ratio) {
        this.maxScreenRatio = ratio;
        return this;
    }

    

    public boolean isActive() { return this.active; }

    public Orientation getOrientation() { return orientation; }

    public Side getSide() { return side; }

    

    
    public boolean tryBegin(double mousePos, double mouseCross,
                            int edgeStart, int crossStart, int edgeLength,
                            int currentSize, int screenSize) {
        if (mousePos < edgeStart || mousePos >= edgeStart + EDGE_THICKNESS
                || mouseCross < crossStart || mouseCross >= crossStart + edgeLength) {
            return false;
        }
        this.active = true;
        this.startPos = (int) mousePos;
        this.startSize = currentSize;
        this.screenSize = screenSize;
        return true;
    }

    
    public int computeNewSize(double mousePos) {
        if (!this.active) return -1;
        int delta = (int) mousePos - this.startPos;
        int newSize = (this.side == Side.LEADING)
                ? this.startSize - delta
                : this.startSize + delta;
        int maxSize = this.screenSize / this.maxScreenRatio;
        return Math.max(this.minSize, Math.min(maxSize, newSize));
    }

    
    public void end() {
        this.active = false;
    }

    

    
    public boolean isOverEdge(int pos, int cross, int edgeStart, int crossStart, int edgeLength) {
        if (this.active) return true;
        return pos >= edgeStart && pos < edgeStart + EDGE_THICKNESS
                && cross >= crossStart && cross < crossStart + edgeLength;
    }
}
