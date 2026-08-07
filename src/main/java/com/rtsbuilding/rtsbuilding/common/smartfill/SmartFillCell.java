package com.rtsbuilding.rtsbuilding.common.smartfill;

/**
 * 智能填坑规划器使用的最小世界单元分类。
 *
 * <p>它只表达方块能否填充、能否作为洞壁或是否尚未加载；具体的 Minecraft
 * 方块判断留给 {@link SmartFillCandidateClassifier}，让客户端预览与服务端重算
 * 共享同一套语义。</p>
 */
public enum SmartFillCell {
    /** 可安全作为填充目标的空气或轻量可替换方块。 */
    CANDIDATE,
    /** 具有完整碰撞体、可作为封闭洞壁的实体方块。 */
    BOUNDARY,
    /** 液体、方块实体或其他既不能填充也不能作为洞壁的单元。 */
    FORBIDDEN,
    /** 区块未加载，不能将未知边界视为已封闭。 */
    UNLOADED
}
