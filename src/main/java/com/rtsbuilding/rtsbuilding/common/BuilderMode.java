package com.rtsbuilding.rtsbuilding.common;

/**
 * 建造者模式枚举，定义 RTS 模式下玩家的操作模式。
 * <p>
 * 玩家通过切换不同模式来执行不同类型的操作，每种模式对应一种交互行为。
 * 模式切换通常通过快捷键或 UI 按钮完成。
 */
public enum BuilderMode {
    /** 关闭模式 —— 不执行任何 RTS 操作，恢复原版交互行为 */
    OFF,
    /** 平移选择模式 —— 用于移动相机视口或选择区域 */
    SELECT_PAN,
    /** 链接存储模式 —— 将容器方块链接到远程存储网络 */
    LINK_STORAGE,
    /** 漏斗模式 —— 配置物品输入/输出通道 */
    FUNNEL,
    /** 互动模式 —— 远程交互（如打开容器 GUI） */
    INTERACT,
    /** 旋转模式 —— 旋转已放置的方块或蓝图 */
    ROTATE
}
