package com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar;

public final class TopBarLayoutHelper {

    

    
    public static final int TOP_BAR_HEIGHT = 24;
    
    public static final int TOP_BAR_GAP = 3;
    
    public static final int BOTTOM_SRC_H = 16;

    
    public static final int BTN_SIZE = 14;
    
    public static final int BTN_MARGIN_R = 4;

    
    public static final int LOGO_SIZE = 24;

    
    public static final int SCREEN_BORDER = 2;

    

    
    public static final int INNER_GAP = 0;
    
    public static final int GROUP_GAP = 4;

    public TopBarLayoutHelper() {}

    

    
    public static final class ButtonGroup {
        private final int groupGap;
        private final Rect[] rects;

        private ButtonGroup(int groupGap, Rect[] rects) {
            this.groupGap = groupGap;
            this.rects = rects;
        }

        
        public static ButtonGroup fromRight(int anchorRight, int anchorY, int size, int count, int groupGap, int innerGap) {
            Rect[] r = new Rect[count];
            int x = anchorRight;
            for (int i = 0; i < count; i++) {
                x -= size;
                r[i] = new Rect(x, anchorY, size, size);
                x -= innerGap;
            }
            return new ButtonGroup(groupGap, r);
        }

        public Rect rect(int index) { return rects[index]; }
        public int leftEdge() { return rects[rects.length - 1].x(); }
        public int rightEdge() { return rects[0].x() + rects[0].width(); }
        public int groupGap() { return groupGap; }
    }

    

    
    public record GroupLayout(ButtonGroup modeGroup, ButtonGroup utilityGroup) {

        
        public static GroupLayout create(int screenWidth, int rightSidebarWidth) {
            int anchorRight = effectiveRightEdge(screenWidth, rightSidebarWidth) - BTN_MARGIN_R;
            int anchorY = TOP_BAR_HEIGHT + SCREEN_BORDER + (BOTTOM_SRC_H - BTN_SIZE) / 2;

            var utility = ButtonGroup.fromRight(anchorRight, anchorY, BTN_SIZE, 2, 0, INNER_GAP);
            var mode = ButtonGroup.fromRight(utility.leftEdge() - GROUP_GAP, anchorY, BTN_SIZE, 2, GROUP_GAP, INNER_GAP);

            return new GroupLayout(mode, utility);
        }
    }

    

    
    public Rect logoRect() {
        return new Rect(0, 0, LOGO_SIZE, LOGO_SIZE);
    }

    

    
    private static int effectiveRightEdge(int screenWidth, int rightSidebarWidth) {
        return screenWidth - rightSidebarWidth;
    }

    

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
