package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

public final class ContainerInputForwarder {

    @Nullable
    private AbstractContainerScreen<?> screen;

    /** 上次 init 的尺寸：同一尺寸不重复重建（AbstractContainerScreen.init 会重置滚动/搜索框等状态）。 */
    private int lastInitW = -1;
    private int lastInitH = -1;

    public ContainerInputForwarder(AbstractContainerScreen<?> screen) {
        this.screen = screen;
    }

    /**
     * 释放当前容器屏幕并清空引用。
     */
    public void clear() {
        if (screen != null) {
            screen.removed();
            screen = null;
        }
        lastInitW = -1;
        lastInitH = -1;
    }

    public boolean hasScreen() {
        return screen != null;
    }

    
    @Nullable
    public AbstractContainerScreen<?> getScreen() {
        return screen;
    }

    
    public void init(int width, int height) {
        // 尺寸未变且屏幕未更换时跳过重建，避免每帧/每次同步都重置容器 UI 状态
        if (screen != null && (width != lastInitW || height != lastInitH)) {
            screen.init(net.minecraft.client.Minecraft.getInstance(), width, height);
            lastInitW = width;
            lastInitH = height;
        }
    }

    
    public void tick() {
        if (screen != null) screen.tick();
    }

    

    public void mouseClicked(double mx, double my, int button) {
        if (screen != null) screen.mouseClicked(mx, my, button);
    }

    /**
     * 命中检测的索引版：返回局部坐标命中的激活槽位在菜单中的索引，未命中返回 -1。
     * 供容器槽位 Shift+点击快速导入网络使用（需要精确槽位索引，不能仅判断命中）。
     */
    public int findSlotIndexAt(double mx, double my) {
        if (screen == null) return -1;
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();
        var slots = screen.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            net.minecraft.world.inventory.Slot s = slots.get(i);
            if (!s.isActive()) continue;
            int sx = guiLeft + s.x;
            int sy = guiTop + s.y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) return i;
        }
        return -1;
    }

    /**
     * 转发鼠标拖拽事件，返回被转发屏幕的处理结果。
     */
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return screen != null && screen.mouseDragged(mx, my, button, dx, dy);
    }

    /**
     * 转发鼠标释放事件，返回被转发屏幕的处理结果。
     */
    public boolean mouseReleased(double mx, double my, int button) {
        return screen != null && screen.mouseReleased(mx, my, button);
    }

    
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (screen != null) return screen.mouseScrolled(mx, my, sx, sy);
        return false;
    }

    public void mouseMoved(double mx, double my) {
        if (screen != null) screen.mouseMoved(mx, my);
    }

    

    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (screen != null) return screen.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    
    public boolean charTyped(char codePoint, int modifiers) {
        if (screen != null) return screen.charTyped(codePoint, modifiers);
        return false;
    }
}
