package com.rtsbuilding.rtsbuilding.uicore.blueprint;

/**
 * BlueprintWindowPanel、命名窗和材料窗共同使用的不可变视图状态。
 * 它不读取文件、不查询世界，也不发送网络包；这些副作用仍由生产适配器拥有。
 */
public final class BlueprintUiState {
    public enum Mode { HIDDEN, CAPTURE_WAITING_FIRST, CAPTURE_WAITING_SECOND, CAPTURE_READY, CAPTURE_SAVING,
        PLACEMENT_SELECTED, PLACEMENT_PINNED }

    public final Mode mode;
    public final String blueprintName;
    public final String blueprintSize;
    public final int selectedIndex;
    public final int blueprintCount;
    public final BlueprintInt3 captureSize;
    public final BlueprintInt3 capturePointA;
    public final BlueprintInt3 capturePointB;
    public final long captureBlockCount;
    public final String status;
    public final int statusColor;
    public final BlueprintInt3 anchor;
    public final int yRotationSteps;
    public final int xRotationSteps;
    public final int zRotationSteps;
    public final boolean materialWindowOpen;
    public final int materialScroll;
    public final boolean nameWindowOpen;
    public final boolean captureNameMode;
    public final String nameDraft;
    public final boolean nameReplaceOnType;
    public final BlueprintMaterialUiState materials;

    public BlueprintUiState(Mode mode, String blueprintName, String blueprintSize,
                            int selectedIndex, int blueprintCount, BlueprintInt3 captureSize,
                            BlueprintInt3 capturePointA, BlueprintInt3 capturePointB,
                            long captureBlockCount, String status, int statusColor, BlueprintInt3 anchor,
                            int yRotationSteps, int xRotationSteps, int zRotationSteps,
                            boolean materialWindowOpen, int materialScroll,
                            boolean nameWindowOpen, boolean captureNameMode, String nameDraft,
                            boolean nameReplaceOnType,
                            BlueprintMaterialUiState materials) {
        this.mode = mode == null ? Mode.HIDDEN : mode;
        this.blueprintName = safe(blueprintName);
        this.blueprintSize = safe(blueprintSize);
        this.selectedIndex = selectedIndex;
        this.blueprintCount = Math.max(0, blueprintCount);
        this.captureSize = captureSize == null ? new BlueprintInt3(0, 0, 0) : captureSize;
        this.capturePointA = capturePointA;
        this.capturePointB = capturePointB;
        this.captureBlockCount = Math.max(0L, captureBlockCount);
        this.status = safe(status);
        this.statusColor = statusColor;
        this.anchor = anchor;
        this.yRotationSteps = normalize(yRotationSteps);
        this.xRotationSteps = normalize(xRotationSteps);
        this.zRotationSteps = normalize(zRotationSteps);
        this.materialWindowOpen = materialWindowOpen;
        this.materialScroll = Math.max(0, materialScroll);
        this.nameWindowOpen = nameWindowOpen;
        this.captureNameMode = captureNameMode;
        this.nameDraft = safe(nameDraft);
        this.nameReplaceOnType = nameReplaceOnType;
        this.materials = materials == null ? BlueprintMaterialUiState.EMPTY : materials;
    }

    public boolean isCapture() {
        return mode == Mode.CAPTURE_WAITING_FIRST || mode == Mode.CAPTURE_WAITING_SECOND
                || mode == Mode.CAPTURE_READY
                || mode == Mode.CAPTURE_SAVING;
    }

    public boolean isPinned() {
        return mode == Mode.PLACEMENT_PINNED && anchor != null;
    }

    public boolean canSaveCapture() {
        return mode == Mode.CAPTURE_READY;
    }

    public boolean canBuild() {
        return isPinned();
    }

    public BlueprintUiState withMode(Mode next) {
        return copy(next, anchor, captureSize, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, materialScroll, nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withAnchor(BlueprintInt3 next) {
        return copy(next == null ? Mode.PLACEMENT_SELECTED : Mode.PLACEMENT_PINNED,
                next, captureSize, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, materialScroll, nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withCaptureSize(BlueprintInt3 next) {
        return copy(mode, anchor, next, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, materialScroll, nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withRotations(int y, int x, int z) {
        return copy(mode, anchor, captureSize, y, x, z,
                materialWindowOpen, materialScroll, nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withMaterialWindow(boolean open) {
        return copy(mode, anchor, captureSize, yRotationSteps, xRotationSteps, zRotationSteps,
                open, materialScroll, nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withMaterialScroll(int scroll) {
        return copy(mode, anchor, captureSize, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, Math.max(0, scroll), nameWindowOpen, captureNameMode, nameDraft);
    }

    public BlueprintUiState withNameWindow(boolean open, boolean captureMode, String draft) {
        return withNameWindowState(open, captureMode, draft, open && !captureMode);
    }

    public BlueprintUiState withNameDraft(String draft) {
        return withNameDraft(draft, false);
    }

    public BlueprintUiState withNameDraft(String draft, boolean replaceOnType) {
        return new BlueprintUiState(mode, blueprintName, blueprintSize, selectedIndex,
                blueprintCount, captureSize, capturePointA, capturePointB,
                captureBlockCount, status, statusColor, anchor, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, materialScroll, nameWindowOpen, captureNameMode, draft,
                replaceOnType, materials);
    }

    public BlueprintUiState appendName(String text) {
        String safeText = safe(text);
        String base = nameReplaceOnType ? "" : nameDraft;
        String combined = base + safeText;
        return withNameDraft(combined.substring(0, Math.min(80, combined.length())), false);
    }

    public BlueprintUiState backspaceName() {
        if (nameReplaceOnType) return withNameDraft("", false);
        if (nameDraft.isEmpty()) return withNameDraft("", false);
        return withNameDraft(nameDraft.substring(0, nameDraft.length() - 1), false);
    }

    private BlueprintUiState withNameWindowState(boolean open, boolean captureMode, String draft,
                                                 boolean replaceOnType) {
        return new BlueprintUiState(mode, blueprintName, blueprintSize, selectedIndex,
                blueprintCount, captureSize, capturePointA, capturePointB,
                captureBlockCount, status, statusColor, anchor, yRotationSteps, xRotationSteps, zRotationSteps,
                materialWindowOpen, materialScroll, open, captureMode, draft, replaceOnType, materials);
    }

    public BlueprintUiState openNameWindow(boolean captureMode, String draft, boolean replaceOnType) {
        return withNameWindowState(true, captureMode, draft, replaceOnType);
    }

    public BlueprintUiState closeNameWindow() {
        return withNameWindowState(false, captureNameMode, "", false);
    }

    private BlueprintUiState copy(Mode nextMode, BlueprintInt3 nextAnchor, BlueprintInt3 nextSize,
                                  int y, int x, int z, boolean materials, int scroll,
                                  boolean nameOpen, boolean nameCapture, String draft) {
        return new BlueprintUiState(nextMode, blueprintName, blueprintSize, selectedIndex,
                blueprintCount, nextSize, capturePointA, capturePointB,
                captureBlockCount, status, statusColor, nextAnchor, y, x, z,
                materials, scroll, nameOpen, nameCapture, draft, nameReplaceOnType, this.materials);
    }

    private static int normalize(int steps) {
        int value = steps % 4;
        return value < 0 ? value + 4 : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
