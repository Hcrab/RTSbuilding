package com.rtsbuilding.rtsbuilding.common.persist;

import java.util.HashMap;
import java.util.Map;

public final class UiSnapshot {

    public Global global = new Global();
    public Session session = new Session();

    public static UiSnapshot defaults() {
        return new UiSnapshot();
    }

    public static final class Global {
        public String mode = "INTERACTIVE";
        public boolean lightMode;
        public float cameraSensitivity = 1.0f;
        public boolean flowAnimationEnabled = true;
        public boolean smoothAnimationEnabled = true;
        public boolean uiSmoothAnimationEnabled = true;
        public boolean depthTestEnabled = true;
        public float noDepthAlpha = 0.10f;
        public int barrierColor = 0xFFFFCC00;
        public int blockTargetColor = 0xFFF69C31;
        public int entityTargetColor = 0xFF4D99FF;
        public int selectionColor = 0xFFFFFFFF;
        public int previewOverlayColor = 0xFF4D80FF;
        public int selectionGapColor = 0xFF000000;
        public int entitySelectionColor = 0xFF4CAF50;
        public boolean debugOverlayEnabled;
        public boolean chunkBorderVisible = true;
        public boolean collisionBoxVisible = true;
    }

    public static final class Session {
        public final Map<String, Bounds> windowBounds = new HashMap<>();
        public final Map<String, Boolean> panelOpen = new HashMap<>();
        public final Map<String, Integer> panelScroll = new HashMap<>();
        public final Map<String, Boolean> sectionExpanded = new HashMap<>();
        public int rightSidebarWidth;
        public int downSidebarHeight;
        public int rightSidebarOverlayH = -1;
        public int downSidebarOverlayW = -1;
        public boolean playerOrbitMode;
        public boolean orbitMode;
        public double orbitTargetX, orbitTargetY, orbitTargetZ;
    }

    public static final class Bounds {
        public int x, y, w, h;

        public Bounds() {}

        public Bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
}
