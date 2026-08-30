package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物漏斗运行时状态的可变状态容器。
 *
 * <p>从 RtsStorageSession 提取，按 "掉落物漏斗的自动收集和缓冲"
 * 的职责聚合。包含漏斗开关、目标坐标、冷却刻数和临时缓冲区。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不包含业务逻辑，仅持有 public mutable 字段</li>
 *   <li><b>可独立实例化</b>——便于测试漏斗状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsFunnelState {

    // ======================================================================
    // 掉落物漏斗运行时状态
    // ======================================================================

    /** 漏斗模式是否激活 */
    public boolean funnelEnabled;

    /** 漏斗输出目标坐标 */
    public BlockPos funnelTarget;

    /**
     * 漏斗目标所属维度。目标坐标与维度必须同时存在；旧存档缺少维度时会保守清除目标，
     * 防止玩家切维后在另一个世界的同坐标误吸物品。
     */
    public ResourceKey<Level> funnelTargetDimension;

    /** 漏斗冷却刻数 */
    public int funnelTickCooldown;

    /**
     * 旧版漏斗临时缓冲区。A 阶段只按预算排空其中已有的 ItemStack；
     * 新掉落无法存入储存或背包时继续留在世界中，
     * 不再复制进尚未落盘的缓冲区。
     */
    public final List<ItemStack> funnelBuffer = new ArrayList<>();
}
