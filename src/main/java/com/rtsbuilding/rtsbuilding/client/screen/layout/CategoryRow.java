package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * 分类行数据。
 */
public record CategoryRow(
        String token,
        String label,
        int depth,
        boolean expandable,
        boolean expanded,
        String modNamespace) {
}
