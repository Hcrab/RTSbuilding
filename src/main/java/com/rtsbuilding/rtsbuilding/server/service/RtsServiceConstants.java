package com.rtsbuilding.rtsbuilding.server.service;

/**
 * 服务端 RTS 服务的集中调优常量类。
 *
 * <p>此类整合了以前分散在各个服务实现和 tick 循环中的魔法数字，
 * 使所有性能相关参数集中在一处，简化全局调优和维护。
 * 类本身为不可实例化的 final 工具类。
 *
 * <p><b>设计原则：</b>这些是<b>内部引擎参数</b>，不是面向用户的配置——
 * 它们有意保持在 {@code Config.java}（使用 NeoForge 的 {@code ModConfigSpec}）之外，
 * 避免用对服务器管理员无实际用途的调优旋钮污染服务器的配置文件。
 *
 * <p><b>参数分组：</b>
 * <ul>
 *   <li><b>漏斗服务（FUNNEL_*）：</b>
 *     <ul>
 *       <li>{@link #FUNNEL_RADIUS} = {@value #FUNNEL_RADIUS}D — 漏斗拾取物品实体的半径</li>
 *       <li>{@link #FUNNEL_MAX_ENTITIES_PER_TICK} = {@value #FUNNEL_MAX_ENTITIES_PER_TICK} — 每 tick 最大处理实体数</li>
 *       <li>{@link #FUNNEL_MAX_ITEMS_PER_TICK} = {@value #FUNNEL_MAX_ITEMS_PER_TICK} — 每 tick 最大处理物品数</li>
 *       <li>{@link #FUNNEL_BUFFER_MAX_STACKS} = {@value #FUNNEL_BUFFER_MAX_STACKS} — 缓冲物品栈上限</li>
 *       <li>{@link #FUNNEL_TICK_INTERVAL} = {@value #FUNNEL_TICK_INTERVAL} — 处理周期间隔</li>
 *     </ul>
 *   </li>
 *   <li><b>已放置方块恢复服务（PLACED_RECOVERY_*）：</b>
 *     <ul>
 *       <li>{@link #PLACED_RECOVERY_MAX_JOBS_PER_TICK} = {@value #PLACED_RECOVERY_MAX_JOBS_PER_TICK} — 每 tick 最大恢复作业数</li>
 *       <li>{@link #PLACED_RECOVERY_MAX_STACKS_PER_TICK} = {@value #PLACED_RECOVERY_MAX_STACKS_PER_TICK} — 每 tick 最大恢复物品栈数</li>
 *     </ul>
 *   </li>
 *   <li><b>存储缓存刷新服务（自适应调度）：</b>
 *     <ul>
 *       <li>{@link #MIN_TICK_RATE} = {@value #MIN_TICK_RATE} — 最快刷新率（每 tick）</li>
 *       <li>{@link #MAX_TICK_RATE} = {@value #MAX_TICK_RATE} — 最慢刷新率（每 60 tick）</li>
 *       <li>{@link #DEFAULT_TICK_RATE} = {@value #DEFAULT_TICK_RATE} — 注册后的起始刷新率</li>
 *       <li>{@link #MAX_INITIAL_RATE} = {@value #MAX_INITIAL_RATE} — 基于槽位数的最大初始刷新率上限</li>
 *       <li>{@link #IDLE_THRESHOLD} = {@value #IDLE_THRESHOLD} — 减速前需要的连续空闲周期数</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class RtsServiceConstants {

    private RtsServiceConstants() {
    }

    // ======================================================================
    //  Funnel service
    // ======================================================================

    /** 漏斗拾取物品实体的半径（方块）。 */
    public static final double FUNNEL_RADIUS = 2.0D;

    /** 每 tick 处理的最大物品实体数。 */
    public static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;

    /** 每 tick 处理的最大单个物品数。 */
    public static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;

    /** 缓冲物品栈在被丢弃前的最大数量。 */
    public static final int FUNNEL_BUFFER_MAX_STACKS = 16;

    /** 漏斗处理周期之间的 tick 间隔。 */
    public static final int FUNNEL_TICK_INTERVAL = 2;

    // ======================================================================
    //  Placed-block recovery service
    // ======================================================================

    /** 每 tick 处理的最大恢复作业数。 */
    public static final int PLACED_RECOVERY_MAX_JOBS_PER_TICK = 4;

    /** 每 tick 恢复的最大单个物品栈数。 */
    public static final int PLACED_RECOVERY_MAX_STACKS_PER_TICK = 8;

    // ======================================================================
    //  Storage tick service (adaptive cache refresh)
    // ======================================================================

    /** 最快刷新率：每个 tick（20 TPS 时为 50ms）。 */
    public static final int MIN_TICK_RATE = 1;

    /** 最慢刷新率：完全空闲时每 60 tick（20 TPS 时为 3s）。 */
    public static final int MAX_TICK_RATE = 60;

    /** 注册或警报后的起始刷新率。 */
    public static final int DEFAULT_TICK_RATE = 8;

    /**
     * 基于总槽位数允许的最大初始刷新率。
     * 即使是庞大的 AE2 系统也以最多此速率启动；自适应机制
     * 在检测到变化时迅速加速。
     */
    public static final int MAX_INITIAL_RATE = 8;

    /**
     * 自适应调度器在减速前需要多少个连续空闲周期。
     * 在默认 8 tick 速率下，这是 15 × 8 = 120 tick（6s）
     * 的无活动时间，之后间隔开始增加。
     */
    public static final int IDLE_THRESHOLD = 15;
}
