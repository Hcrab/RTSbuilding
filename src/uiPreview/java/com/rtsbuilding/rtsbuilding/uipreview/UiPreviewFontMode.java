package com.rtsbuilding.rtsbuilding.uipreview;

/** 离屏审图使用的字体渲染档；默认值由预览任务设为 Modern UI。 */
enum UiPreviewFontMode {
    MODERN_UI,
    MINECRAFT;

    static UiPreviewFontMode configured() {
        String raw = System.getProperty("rts.ui.preview.fontMode", "modern_ui");
        return "minecraft".equalsIgnoreCase(raw) ? MINECRAFT : MODERN_UI;
    }

    String fileId() {
        return this == MINECRAFT ? "minecraft" : "modern_ui";
    }
}
