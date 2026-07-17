package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

/**
 * 智能放置子模式枚举，与 {@link BuildShape} 同级。
 *
 * <p>每个子模式对应一种自动填充策略，通过 QuickBuild 面板的
 * 形状按钮区域切换。预留扩展位以便后续添加更多子模式。</p>
 */
public enum SmartPlaceMode {
    /** 洞口填充：检测并填充地形空洞 */
    HOLE_FILL,
    /** 湖泊填充：检测并填充流体空洞 */
    LAKE_FILL
}
