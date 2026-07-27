package com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar;

public final class DownSidebarLayoutHelper {

    
    public static final int DOWN_BAR_HEIGHT = 81;

    public DownSidebarLayoutHelper() {}

    

    
    public Rect downBarRect(int screenWidth, int screenHeight, int rightSidebarWidth) {
        return downBarRect(screenWidth, screenHeight, rightSidebarWidth, DOWN_BAR_HEIGHT);
    }

    
    public Rect downBarRect(int screenWidth, int screenHeight, int rightSidebarWidth, int barHeight) {
        int x = 0;
        int y = screenHeight - barHeight;
        int w = screenWidth - rightSidebarWidth;
        return new Rect(x, y, w, barHeight);
    }

    

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
