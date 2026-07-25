package com.rtsbuilding.rtsbuilding.uikit.window;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiWindowInteractionModelTest {
    private static final UiRect SCREEN = new UiRect(0, 0, 500, 300);

    @Test
    void 拖拽保持按下偏移() {
        UiWindowInteractionModel model = model();
        model.beginDrag(25, 35);
        model.dragTo(125, 135);
        assertEquals(new UiRect(110, 120, 100, 80), model.getBounds());
    }

    @Test
    void 拖拽不能越出屏幕() {
        UiWindowInteractionModel model = model();
        model.beginDrag(20, 30);
        model.dragTo(-100, -100);
        assertEquals(0, model.getBounds().getX());
        assertEquals(0, model.getBounds().getY());
    }

    @Test
    void 右下缩放只移动右下边() {
        UiWindowInteractionModel model = model();
        model.beginResize(UiWindowInteractionModel.ResizeEdge.BOTTOM_RIGHT, 110, 100);
        model.resizeTo(150, 130);
        assertEquals(new UiRect(10, 20, 140, 110), model.getBounds());
    }

    @Test
    void 左边缩小遵守最小宽度并固定右边() {
        UiWindowInteractionModel model = model();
        model.beginResize(UiWindowInteractionModel.ResizeEdge.LEFT, 10, 50);
        model.resizeTo(100, 50);
        assertEquals(60, model.getBounds().getWidth());
        assertEquals(110, model.getBounds().right());
    }

    @Test
    void 结束交互清理状态() {
        UiWindowInteractionModel model = model();
        model.beginDrag(20, 30);
        assertTrue(model.isInteracting());
        model.endInteraction();
        assertFalse(model.isInteracting());
    }

    private static UiWindowInteractionModel model() {
        return new UiWindowInteractionModel(SCREEN, new UiRect(10, 20, 100, 80), 60, 50);
    }
}
