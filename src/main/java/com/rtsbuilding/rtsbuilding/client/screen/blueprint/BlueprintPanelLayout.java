package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

/**
 * 仅保留旧命名弹窗仍在使用的稳定几何。
 *
 * <p>蓝图库的顶栏、列表和详情区由 {@code BlueprintLibraryLayout} 统一计算，
 * 不能在此重建第二套命中矩形。</p>
 */
final class BlueprintPanelLayout {
    private BlueprintPanelLayout() {
    }

    static NameDialogLayout nameDialogLayout(int screenWidth, int screenHeight, boolean captureDialog) {
        int width = Math.min(420, Math.max(300, screenWidth - RtsMainlineLayout.D48));
        int height = captureDialog ? 136 : 118;
        int x = (screenWidth - width) / 2;
        int y = Math.max(24, (screenHeight - height) / 2);
        int inputX = x + RtsMainlineLayout.D10;
        int inputY = y + (captureDialog ? 76 : 62);
        int inputWidth = width - RtsMainlineLayout.D20;
        int cancelWidth = 58;
        int confirmWidth = 70;
        int buttonY = y + height - RtsMainlineLayout.D24;
        int cancelX = x + width - cancelWidth - RtsMainlineLayout.D10;
        int confirmX = cancelX - confirmWidth - RtsMainlineLayout.D6;
        return new NameDialogLayout(
                x,
                y,
                width,
                height,
                inputX,
                inputY,
                inputWidth,
                confirmX,
                confirmWidth,
                cancelX,
                cancelWidth,
                buttonY);
    }

    record NameDialogLayout(
            int x,
            int y,
            int w,
            int h,
            int inputX,
            int inputY,
            int inputW,
            int confirmX,
            int confirmW,
            int cancelX,
            int cancelW,
            int buttonY) {
    }

}
