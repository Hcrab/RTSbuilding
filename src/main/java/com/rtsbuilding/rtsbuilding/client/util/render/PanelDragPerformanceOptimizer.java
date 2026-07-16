package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;

/**
 * 面板拖拽性能优化器——用于跟踪拖拽状态以优化渲染性能
 * 
 * <p>这个工具类用于跟踪面板是否正在被拖拽，以便在渲染时做出相应优化。</p>
 */
public class PanelDragPerformanceOptimizer {
    
    private static RtsPanel currentlyDraggingPanel = null;
    
    /**
     * 检查指定面板是否正在被拖拽
     */
    public static boolean isPanelBeingDragged(RtsPanel panel) {
        return currentlyDraggingPanel == panel;
    }
    
    /**
     * 设置当前正在拖拽的面板
     */
    public static void setCurrentlyDraggingPanel(RtsPanel panel) {
        currentlyDraggingPanel = panel;
    }
    
    /**
     * 清除当前拖拽面板状态
     */
    public static void clearDraggingPanel() {
        currentlyDraggingPanel = null;
    }
    
    /**
     * 检查当前是否有面板正在被拖拽
     */
    public static boolean isAnyPanelBeingDragged() {
        return currentlyDraggingPanel != null;
    }
}