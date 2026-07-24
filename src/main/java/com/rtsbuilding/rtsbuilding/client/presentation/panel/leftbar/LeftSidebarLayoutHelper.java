package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;


public final class LeftSidebarLayoutHelper {

    
    public static final int SIDEBAR_WIDTH = 0;

    
    public static final int SIDEBAR_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    public LeftSidebarLayoutHelper() {}

    

    
    public Rect sidebarRect(int screenWidth, int screenHeight) {
        return sidebarRect(screenWidth, screenHeight, SIDEBAR_WIDTH);
    }

    
    public Rect sidebarRect(int screenWidth, int screenHeight, int sidebarWidth) {
        int x = 0;
        int y = SIDEBAR_TOP_Y;
        return new Rect(x, y, sidebarWidth, screenHeight - y);
    }

    

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
