package com.rtsbuilding.rtsbuilding.uicore.culling;

/** 范围剔除窗口/世界编辑的纯动作。 */
public final class CullingUiAction {
    public enum Type { DELETE_SELECTED, CONFIRM_DRAFT, CANCEL_DRAFT, CLOSE,
        ADJUST_HEIGHT, WORLD_PRIMARY, RESIZE_HANDLE }

    public final Type type;
    public final int value;
    public final CullingUiDirection direction;

    private CullingUiAction(Type type, int value, CullingUiDirection direction) {
        this.type = type;
        this.value = value;
        this.direction = direction;
    }

    public static CullingUiAction simple(Type type) {
        return new CullingUiAction(type, 0, null);
    }
    public static CullingUiAction height(int delta) {
        return new CullingUiAction(Type.ADJUST_HEIGHT, delta, null);
    }
    public static CullingUiAction handle(CullingUiDirection direction, int delta) {
        return new CullingUiAction(Type.RESIZE_HANDLE, delta, direction);
    }
}
