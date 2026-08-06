package com.rtsbuilding.rtsbuilding.uicore.ultimine;

/**
 * 已确认范围破坏工作区的不可逆视觉阶段。
 *
 * <p>它只锁存“第一块是否已经完成”，不读取 Minecraft、工作流或渲染缓存。
 * 第一块完成前必须保留逐方块预览；进入侵蚀后即使某一帧同步缺失，也不能退回逐方块线框。</p>
 */
public final class DestroyWorkAreaPhaseTracker {
    public enum Phase { FIRST_BLOCK, ERODING }

    private int previewKey;
    private boolean eroding;

    public Phase update(int currentPreviewKey, boolean firstBlockCompleted) {
        if (currentPreviewKey != previewKey) {
            previewKey = currentPreviewKey;
            eroding = false;
        }
        if (firstBlockCompleted) eroding = true;
        return eroding ? Phase.ERODING : Phase.FIRST_BLOCK;
    }

    public void clear() { previewKey = 0; eroding = false; }
}
