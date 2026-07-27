package com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;

public final class RightSidebarLayoutHelper {

    public static final int SIDEBAR_WIDTH = 90;

    public static final int SIDEBAR_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    public RightSidebarLayoutHelper() {}

    public Rect sidebarRect(int screenWidth, int screenHeight) {
        return sidebarRect(screenWidth, screenHeight, SIDEBAR_WIDTH);
    }

    public Rect sidebarRect(int screenWidth, int screenHeight, int sidebarWidth) {
        int x = screenWidth - sidebarWidth;
        int y = SIDEBAR_TOP_Y;
        return new Rect(x, y, sidebarWidth, screenHeight - y);
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
