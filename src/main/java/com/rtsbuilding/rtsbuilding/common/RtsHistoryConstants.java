package com.rtsbuilding.rtsbuilding.common;

/**
 * 历史记录常量 —— 定义服务端撤销追踪和客户端 UI 使用的历史上限。
 * <p>
 * 这些常量故意放在公共包中而非客户端包中，
 * 以确保专用服务器在加载游戏规则历史时无需加载 UI 类。
 */
public final class RtsHistoryConstants {

    /** 每位玩家只保留最近三次完整操作。 */
    public static final int SHAPE_HISTORY_LIMIT = 3;

    /** 单次历史允许记录的最大方块数；超限时整条历史不入栈。 */
    public static final int MAX_BLOCKS_PER_ENTRY = 98_304;

    /** 创造模式快照的压缩 NBT 预算，供完整快照实现统一引用。 */
    public static final int MAX_COMPRESSED_NBT_BYTES_PER_ENTRY = 32 * 1024 * 1024;

    private RtsHistoryConstants() {
    }
}
