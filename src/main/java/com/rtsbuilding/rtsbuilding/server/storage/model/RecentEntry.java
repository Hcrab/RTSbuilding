package com.rtsbuilding.rtsbuilding.server.storage.model;

/**
 * UI 最近条目快照。
 *
 * <p>记录玩家最近查看或操作过的物品/流体摘要，用于"最近使用"列表渲染。
 * 本 record 反映的是 UI 历史记录，不是物品/流体存储的权威计数。
 *
 * @param id       物品/流体的注册 ID（如 {@code "minecraft:diamond"}）
 * @param amount   可见数量
 * @param capacity 容量（仅流体有效；物品记为 0）
 * @param kind     类别标记：由 {@code S2CRtsStoragePagePayload.RECENT_ITEM_*} 常量定义
 */
public record RecentEntry(String id, long amount, long capacity, byte kind) {
}
