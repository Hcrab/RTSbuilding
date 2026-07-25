package com.rtsbuilding.rtsbuilding.common;

/**
 * 历史记录常量 —— 定义服务端撤销追踪和客户端 UI 使用的历史上限。
 * <p>
 * 这些常量故意放在公共包中而非客户端包中，
 * 以确保专用服务器在加载游戏规则历史时无需加载 UI 类。
 */
public final class RtsHistoryConstants {

    /** 每位玩家形状/建造历史栈的最大条目数 */
    public static final int SHAPE_HISTORY_LIMIT = 1000;

    private RtsHistoryConstants() {
    }
}
