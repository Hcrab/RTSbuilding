package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * 快速建造面板布局参数。
 */
public record QuickBuildPanelLayout(int x, int y, int w, int h) {

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX <= this.x + this.w
                && mouseY >= this.y && mouseY <= this.y + this.h;
    }
}
