package com.rtsbuilding.rtsbuilding.uicore.culling;

/**
 * 范围剔除窗口和世界编辑状态的纯 Java 快照。
 *
 * <p>方块坐标、射线、AABB、动画和渲染刷新不进入 Core；这里只保存面板所需计数、
 * 选择、尺寸、草稿阶段和六向手柄标识。</p>
 */
public final class CullingUiState {
    public final boolean open;
    public final boolean enabled;
    public final String disabledReason;
    public final CullingUiPhase phase;
    public final int boxCount;
    public final int selectedId;
    public final int width;
    public final int height;
    public final int depth;
    public final int previewHeight;
    public final int hoveredId;
    public final CullingUiDirection hoveredHandle;
    public final CullingUiDirection activeHandle;

    public CullingUiState(boolean open, boolean enabled, String disabledReason,
            CullingUiPhase phase, int boxCount, int selectedId,
            int width, int height, int depth, int previewHeight, int hoveredId,
            CullingUiDirection hoveredHandle, CullingUiDirection activeHandle) {
        this.open = open && enabled;
        this.enabled = enabled;
        this.disabledReason = disabledReason == null ? "" : disabledReason;
        this.phase = phase == null ? CullingUiPhase.IDLE : phase;
        this.boxCount = Math.max(0, boxCount);
        this.selectedId = selectedId;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.depth = Math.max(0, depth);
        this.previewHeight = clamp(previewHeight, -255, 255);
        this.hoveredId = hoveredId;
        this.hoveredHandle = hoveredHandle;
        this.activeHandle = activeHandle;
    }

    public boolean hasSelection() { return selectedId >= 0; }
    public boolean hasCompleteDraft() { return phase == CullingUiPhase.NEED_HEIGHT; }

    CullingUiState closed() {
        return copy(false, phase, boxCount, selectedId, width, height, depth, previewHeight);
    }

    CullingUiState withPreviewHeight(int value) {
        return copy(open, phase, boxCount, selectedId, width, height, depth, value);
    }

    CullingUiState afterDelete() {
        return copy(open, phase, Math.max(0, boxCount - 1), -1, 0, 0, 0, previewHeight);
    }

    CullingUiState cancelledDraft() {
        return copy(open, CullingUiPhase.IDLE, boxCount, selectedId,
                width, height, depth, 0);
    }

    private CullingUiState copy(boolean nextOpen, CullingUiPhase nextPhase, int nextCount,
            int nextSelected, int nextWidth, int nextHeight, int nextDepth, int nextPreviewHeight) {
        return new CullingUiState(nextOpen, enabled, disabledReason, nextPhase, nextCount,
                nextSelected, nextWidth, nextHeight, nextDepth, nextPreviewHeight,
                hoveredId, hoveredHandle, activeHandle);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
