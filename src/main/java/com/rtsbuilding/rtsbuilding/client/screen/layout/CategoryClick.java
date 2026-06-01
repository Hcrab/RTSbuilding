package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * 分类点击结果。
 */
public record CategoryClick(
        String categoryToken,
        String modNamespace,
        boolean toggleExpandOnly) {
}
