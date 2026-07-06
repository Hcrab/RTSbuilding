package com.rtsbuilding.rtsbuilding.client.util.state;

/**
 * 悬浮抑制上下文——替代全局静态 {@code globallySuppressed} 的树级抑制方案。
 *
 * <p><b>解决的问题：</b></p>
 * <p>原 {@code HoverStateManager.globallySuppressed} 是一个全局静态布尔值，
 * 所有面板共享。当存在多个独立的上层面板时，谁负责设 true、谁负责恢复 false
 * 很容易混乱，导致下层悬浮效果被误抑制或遗漏抑制。</p>
 *
 * <p><b>解决方案：</b></p>
 * <p>每个上层面板（弹窗、浮动窗口）持有一个 {@link HoverSuppression} 实例，
 * 将该实例传递给受其遮挡的下层面板进行检查。只有"我的遮挡者说抑制我"时才抑制，
 * 不同面板树的抑制状态互不干扰。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 在浮动窗口中创建抑制上下文
 * HoverSuppression mySuppression = new HoverSuppression();
 *
 * // 渲染到受遮挡的下层面板时：
 * // 下层面板调用 mySuppression.isSuppressed() 判断是否抑制悬浮
 * }</pre>
 */
public final class HoverSuppression {

    private boolean suppressed;

    /**
     * 设置是否抑制悬浮效果。
     *
     * @param suppressed true=抑制悬浮效果
     */
    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    /**
     * 当前是否处于抑制状态。
     */
    public boolean isSuppressed() {
        return suppressed;
    }

    /**
     * 清除抑制状态。
     */
    public void clear() {
        this.suppressed = false;
    }
}
