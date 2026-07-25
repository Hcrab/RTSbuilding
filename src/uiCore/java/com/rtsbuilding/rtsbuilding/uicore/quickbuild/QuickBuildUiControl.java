package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** 右栏的填充、朝向、高级框和连接按钮。 */
public final class QuickBuildUiControl {
    public enum Id { FILL, HOLLOW, SKELETON, VERTICAL, ADVANCED, CONNECT }
    public final Id id;
    public final String label;
    public final boolean selected;
    public final boolean enabled;

    public QuickBuildUiControl(Id id, String label, boolean selected, boolean enabled) {
        if (id == null) throw new IllegalArgumentException("id");
        this.id=id; this.label=label == null ? "" : label;
        this.selected=selected; this.enabled=enabled;
    }

    public QuickBuildUiControl withSelected(boolean value) {
        return new QuickBuildUiControl(id, label, value, enabled);
    }
}
