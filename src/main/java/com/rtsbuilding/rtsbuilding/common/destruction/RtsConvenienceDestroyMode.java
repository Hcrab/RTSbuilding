package com.rtsbuilding.rtsbuilding.common.destruction;

/**
 * 快速建造“便捷破坏”请求的服务端语义。
 *
 * <p>枚举只描述玩家要执行的操作，不包含客户端窗口状态，也不携带客户端规划出的
 * 坐标列表。服务端必须基于锚点、命中面和受限参数重新规划目标。</p>
 */
public enum RtsConvenienceDestroyMode {
    REPEAT_BOX,
    CHUNK_QUARRY,
    TREE_FELL
}
