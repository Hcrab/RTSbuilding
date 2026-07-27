package com.rtsbuilding.rtsbuilding.server.loadout;

/**
 * 挖掘装备栏的角色枚举，定义玩家可以绑定的工具类型。
 * <p>
 * 每种角色对应一种工具类别，玩家可以将指定槽位的工具绑定到对应角色上，
 * 在挖掘对应类型的方块时自动切换到绑定的工具。
 */
public enum MiningLoadoutRole {
    /** 镐 - 用于挖掘石头、矿石等方块 */
    PICK,
    /** 锹 - 用于挖掘泥土、沙子等方块 */
    SHOVEL,
    /** 斧 - 用于挖掘木头类方块 */
    AXE,
    /** 锄 - 用于挖掘与锄相关的方块 */
    HOE
}

