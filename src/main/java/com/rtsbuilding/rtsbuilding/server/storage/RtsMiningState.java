package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * 远程挖掘与连锁挖掘（Ultimine）的可变状态容器。
 *
 * <p>从 {@link RtsStorageSession} 提取，按 "玩家如何执行远程挖掘操作"
 * 的职责聚合。包含单方块挖掘、连锁挖掘、工具借用/归还等运行时状态。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不包含业务逻辑，仅持有 public mutable 字段</li>
 *   <li><b>可独立实例化</b>——便于测试挖掘状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsMiningState {

    // ======================================================================
    // 单方块远程挖掘
    // ======================================================================

    /** 当前挖掘目标坐标，null = 未在挖掘 */
    public BlockPos miningPos;
    /** 挖掘方向（默认为下） */
    public Direction miningFace = Direction.DOWN;
    /** 当前使用的工具栏格索引 */
    public int miningToolSlot;
    /** 当前借用的远程挖掘工具租约 */
    public RtsToolLease miningToolLease = RtsToolLease.empty();
    /** True when a non-block RTS selected item must be used instead of silently falling back to the hotbar. */
    public boolean miningSelectedToolRequested;
    /** True when active batch mining should stop before a damageable tool reaches its last 5% durability. */
    public boolean miningToolProtectionEnabled = true;
    /** 当前挖掘进度[0.0, 1.0]，服务端按 tick 递增 */
    public float miningProgress;
    /** 当前破坏阶段索引；-1 = 尚未开始 */
    public int miningStage = -1;

    // ======================================================================
    // 连锁挖掘（Ultimine）
    // ======================================================================

    /** 连锁挖掘的待处理目标队列（先进先出） */
    public final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    /** 连锁挖掘当前正在挖掘的坐标 */
    public BlockPos ultimineProgressPos;
    /** 连锁挖掘本次任务的总目标数 */
    public int ultimineTotalTargets;
    /** 连锁挖掘已处理完成的目标数 */
    public int ultimineProcessedTargets;
    /** 连锁挖掘已成功破坏的位置记录（预捕获的 HistoryBlockRecord，用于批量记录历史） */
    public final List<HistoryBlockRecord> ultimineProcessedPositions = new ArrayList<>();
    /** 连锁挖掘是否已吸收掉落物（防止重复收集，由管理器控制） */
    public boolean ultimineAbsorbedDrops;
}
