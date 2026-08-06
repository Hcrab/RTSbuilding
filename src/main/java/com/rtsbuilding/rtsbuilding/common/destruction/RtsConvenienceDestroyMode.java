package com.rtsbuilding.rtsbuilding.common.destruction;

/**
 * 便捷破坏请求的服务端语义。
 *
 * <p>枚举只描述玩家想做什么，不包含客户端窗口状态，也不携带任意坐标列表。
 * 服务端必须根据锚点、命中面与参数重新规划目标。</p>
 */
public enum RtsConvenienceDestroyMode {
    REPEAT_BOX,
    CHUNK_QUARRY,
    TREE_FELL
}

