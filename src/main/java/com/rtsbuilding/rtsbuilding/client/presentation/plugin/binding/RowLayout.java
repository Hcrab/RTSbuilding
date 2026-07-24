package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import net.minecraft.client.Minecraft;

public final class RowLayout {
    int y;
    int arrowBtnX;
    int priorityX;
    int priorityW;
    int unbindX;
    int toggleX;
    int unbindW;
    int toggleW;
    int locateBtnX;
    int locateBtnW;
    int originalIndex;

    record ButtonBar(int unbindW, int toggleW, int locateW, int btnAreaRight) {

        private static final int BTN_PAD_H = 4;
        private static final int BTN_GAP = 2;
        private static final int LEFT_PAD = 5;
        private static final int SCROLLBAR_W = 7;
        private static final int RIGHT_MARGIN = 4;

        int toggleX()  { return btnAreaRight - toggleW; }

        int unbindX()  { return toggleX() - BTN_GAP - unbindW; }

        int locateX()  { return unbindX() - BTN_GAP - locateW; }

        ButtonBar(Minecraft mc, boolean scrollBarVisible, int parentX, int parentW) {
            this(
                    mc.font.width("解绑") + BTN_PAD_H * 2,
                    Math.max(mc.font.width("双向"), mc.font.width("仅提取")) + BTN_PAD_H * 2,
                    Math.max(mc.font.width("开启位置"), mc.font.width("关闭显示")) + BTN_PAD_H * 2,
                    parentX + LEFT_PAD + (parentW - LEFT_PAD - SCROLLBAR_W - RIGHT_MARGIN)
                            - (scrollBarVisible ? 2 : 0) - 1
            );
        }
    }
}
