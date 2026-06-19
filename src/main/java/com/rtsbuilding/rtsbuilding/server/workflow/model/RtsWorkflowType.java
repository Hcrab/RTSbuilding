package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 可由工作流系统跟踪的工作流类型。
 *
 * <p>每个枚举常量代表远程操作的一个不同类别：
 * 单次或批量、挖掘或放置。此类型用于在 UI 中标识活动工作流，
 * 并决定使用哪种进度/报告格式。</p>
 */
public enum RtsWorkflowType {

    /** 单方块远程挖掘。 */
    MINE_SINGLE,

    /** 连锁（ultimine）批量挖掘。 */
    ULTIMINE,

    /** 在定义的 3D 体积内进行区域挖掘操作。 */
    AREA_MINE,

    /** 快速建造预览中的形状摧毁操作。 */
    AREA_DESTROY,

    /** 单方块远程放置。 */
    PLACE_SINGLE,

    /** 多方块批量放置（交互式逐位置放置）。 */
    PLACE_BATCH,

    /** 快速建造（预解析状态）形状放置。 */
    QUICK_BUILD,

    /** 蓝图文件远程放置构建。 */
    BLUEPRINT_BUILD,

    /**
     * 独立的停止挖掘操作（之后不会启动新的挖掘）。
     *
     * <p>当玩家显式取消挖掘操作或禁用 RTS 模式时使用。
     * 与 {@code StopPreviousPipe} 内部的隐式停止不同，
     * 这是由用户发起的停止。</p>
     */
    STOP_MINING
}
