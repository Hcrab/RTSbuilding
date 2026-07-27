package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;

public class PanelDragPerformanceOptimizer {
    
    private static RtsPanel currentlyDraggingPanel = null;
    
    
    public static boolean isPanelBeingDragged(RtsPanel panel) {
        return currentlyDraggingPanel == panel;
    }
    
    
    public static void setCurrentlyDraggingPanel(RtsPanel panel) {
        currentlyDraggingPanel = panel;
    }
    
    
    public static void clearDraggingPanel() {
        currentlyDraggingPanel = null;
    }
    
    
    public static boolean isAnyPanelBeingDragged() {
        return currentlyDraggingPanel != null;
    }
}