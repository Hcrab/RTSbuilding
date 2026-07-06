package com.rtsbuilding.rtsbuilding.client.screen.event;

/**
 * 输入事件结果——指示事件分发器是否应继续向下传递。
 */
public enum EventResult {
    /** 事件已被消费，停止向下传递 */
    CONSUMED,
    /** 事件未被消费，继续传递给下一个处理器 */
    PASS
}
