package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 智能放置模式的客户端参数存储，封装滑块数值和子模式选择。
 * <p>字段通过 {@link QuickBuildPanel} 持久化到 RTS UI 状态。</p>
 */
public final class SmartPlaceOptions {

    /** 当前选中的智能放置子模式 */
    public SmartPlaceMode mode = SmartPlaceMode.HOLE_FILL;
    /** 填充方块数量（客户端滑条值） */
    public int fillCount = SMART_PLACE_DEFAULT_FILL_COUNT;
    /** 检测直径（客户端滑条值） */
    public int detectionDiameter = SMART_PLACE_DEFAULT_DIAMETER;

    public int clampFillCount(int value) {
        return Math.max(SMART_PLACE_MIN_FILL_COUNT, Math.min(SMART_PLACE_MAX_FILL_COUNT, value));
    }

    public int clampDiameter(int value) {
        return Math.max(SMART_PLACE_MIN_DIAMETER, Math.min(SMART_PLACE_MAX_DIAMETER, value));
    }

    public void resetToDefaults() {
        this.fillCount = SMART_PLACE_DEFAULT_FILL_COUNT;
        this.detectionDiameter = SMART_PLACE_DEFAULT_DIAMETER;
    }
}
