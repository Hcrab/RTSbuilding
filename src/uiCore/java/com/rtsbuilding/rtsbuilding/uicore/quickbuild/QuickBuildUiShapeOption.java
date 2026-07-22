package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** 快速建造窗一个正式形状按钮。 */
public final class QuickBuildUiShapeOption {
    public final QuickBuildUiShape shape;
    public final boolean selected;
    public final boolean enabled;
    public final String disabledReason;

    public QuickBuildUiShapeOption(QuickBuildUiShape shape, boolean selected,
                                   boolean enabled, String disabledReason) {
        if (shape == null) throw new IllegalArgumentException("shape");
        this.shape=shape; this.selected=selected; this.enabled=enabled;
        this.disabledReason=disabledReason == null ? "" : disabledReason;
        if (enabled && !this.disabledReason.isEmpty()) {
            throw new IllegalArgumentException("enabled shape cannot have disabled reason");
        }
    }
}
