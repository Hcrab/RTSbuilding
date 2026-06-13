package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 远程 GUI 菜单状态与会话数据版本的可变状态容器。
 *
 * <p>从 {@link RtsStorageSession} 提取，按 "远程菜单交互和数据版本追踪"
 * 的职责聚合。包含远程 GUI 菜单的容器 ID 和方块坐标、任务检测时间、
 * 存储视图过期标志以及页面缓存数据版本号。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不包含业务逻辑，仅持有 public mutable 字段</li>
 *   <li><b>可独立实例化</b>——便于测试传输状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsTransferState {

    // ======================================================================
    // 远程 GUI 菜单状态
    // ======================================================================

    /** 远程 GUI 菜单的容器 ID；-1 = 无活动远程菜单 */
    public int remoteMenuContainerId = -1;

    /** 远程 GUI 菜单对应的方块坐标 */
    public BlockPos remoteMenuPos;

    /** 下次检测 RTS 任务或进度的 tick 时间 */
    public long nextQuestDetectTick;

    /** True when the client's storage browser page no longer matches storage contents. */
    public boolean storageViewDirty;

    /**
     * 存储数据版本号——缓存数据变更时递增。
     * <p>用于 {@code RtsPageCore} 的页面缓存过期检测。
     * 纯翻页操作（search/sort/category 不变）时，
     * 若版本号未变则跳过 O(n log n) 的排序过滤重构。
     */
    public final AtomicLong pageDataVersion = new AtomicLong();
}
