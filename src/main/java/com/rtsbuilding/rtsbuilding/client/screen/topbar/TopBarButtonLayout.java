package com.rtsbuilding.rtsbuilding.client.screen.topbar;

/**
 * 顶部栏按钮布局参数。
 */
public record TopBarButtonLayout(
        TopBarButtonId id,
        int x,
        int width,
        String label,
        boolean iconOnly,
        boolean active) {
}
