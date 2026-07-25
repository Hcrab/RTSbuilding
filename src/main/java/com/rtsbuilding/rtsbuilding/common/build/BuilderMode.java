package com.rtsbuilding.rtsbuilding.common.build;

/**
 * RTS 模式下玩家当前拥有的操作输入权。
 *
 * <p>这个枚举只描述模式，不负责客户端按键、界面绘制或服务端权限判定。
 * 客户端和服务端共用同一组值，避免切换模式时维护两份编号映射。
 */
public enum BuilderMode {
    /** 关闭 RTS 操作。 */
    OFF,
    /** 选择目标或平移镜头。 */
    SELECT_PAN,
    /** 绑定储存。 */
    LINK_STORAGE,
    /** 漏斗收集。 */
    FUNNEL,
    /** 交互、放置与普通挖掘。 */
    INTERACT,
    /** 旋转已放置方块。 */
    ROTATE
}
