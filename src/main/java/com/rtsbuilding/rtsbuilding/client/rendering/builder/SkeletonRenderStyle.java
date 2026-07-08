package com.rtsbuilding.rtsbuilding.client.rendering.builder;

/**
 * 批量破坏合并骨架的视觉策略常量。
 *
 * <p>这里不依赖 Minecraft client 渲染类，方便单元测试在未 bootstrap
 * 的环境里验证“远距离仍可读”的策略边界。</p>
 */
final class SkeletonRenderStyle {
    static final float CONFIRMED_NO_DEPTH_ALPHA = 0.52F;
    static final double NO_DEPTH_LINE_WIDTH = 3.0D;

    private SkeletonRenderStyle() {
    }
}
