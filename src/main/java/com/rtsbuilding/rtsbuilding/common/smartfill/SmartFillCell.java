package com.rtsbuilding.rtsbuilding.common.smartfill;

/**
 * 智能填洞规划器看到的最小世界单元分类。
 *
 * <p>本枚举只表达“能否填”和“能否作为洞壁”，不拥有 Minecraft 方块类型判断。
 * 客户端预览与服务端提交必须通过同一个分类器生成这些值，避免两侧对可替换方块的
 * 理解悄悄漂移。</p>
 */
public enum SmartFillCell {
    /** 空气或明确允许被填充替换的轻量方块。 */
    CANDIDATE,
    /** 具有完整碰撞边界、可以参与洞穴包围判定的实体方块。 */
    BOUNDARY,
    /** 流体、方块实体或其他不能填充、也不能冒充洞壁的单元。 */
    FORBIDDEN,
    /** 区块未加载；规划必须立即失败，不能把未知边界当成完整洞穴。 */
    UNLOADED
}

