package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * RTSBuilding 可以追踪的玩家工作流类型。
 *
 * <p>这里保持和 1.21.1 main 的枚举顺序一致，因为网络包用 ordinal 传输类型。
 * 后续如果要新增类型，必须同时确认 NeoForge 与 Forge 两条线的顺序。</p>
 */
public enum RtsWorkflowType {
    MINE_SINGLE,
    ULTIMINE,
    AREA_MINE,
    AREA_DESTROY,
    PLACE_SINGLE,
    PLACE_BATCH,
    QUICK_BUILD,
    STOP_MINING
}
