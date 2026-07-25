package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.storage.state.*;

/**
 * 玩家 RTS 存储会话的<strong>可变状态容器</strong>（聚合根）。
 *
 * <p>本类仅持有纯数据字段，按功能域分为 4 个模块 + 6 个独立状态对象：
 * <ul>
 *   <li>{@link #linkedStorageInfo}——链接存储引用及元数据（7 个字段）</li>
 *   <li>{@link #bdCache}——BD 网络缓存（5 个字段）</li>
 *   <li>{@link #sessionFlags}——会话开关与虚拟流体（3 个字段）</li>
 *   <li>{@link #uiMemory}——UI 记忆（4 个字段）</li>
 *   <li>{@code mode}、{@code browser}、{@code mining}、{@code funnel}、
 *       {@code transfer}、{@code placement}——独立状态对象</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不查询方块实体/解析能力/序列化 NBT/变更物品栏/发包</li>
 *   <li><b>模块优先</b>——新建代码应优先使用模块的封装方法而非直接操作字段</li>
 * </ul>
 */
public class RtsStorageSession {

    // ======================================================================
    // §1  链接存储信息模块（LinkedStorageInfo）
    // ======================================================================

    /** 链接存储引用及关联元数据（linkedStorages, linkedNames, linkedModes, linkedPriorities, linkedBackpackUuids, linkedBackpackItemIds, detachedBackpackRefs） */
    public final LinkedStorageInfo linkedStorageInfo = new LinkedStorageInfo();

    // ======================================================================
    // §2  BD 网络缓存模块（BdCacheState）
    // ======================================================================

    /** BD 网络处理器、名称及脏标记缓存 */
    public final BdCacheState bdCache = new BdCacheState();

    // ======================================================================
    // §3  会话开关与虚拟流体模块（SessionFlags）
    // ======================================================================

    /** useBdNetwork, autoStoreMinedDrops, internalFluidMb */
    public final SessionFlags sessionFlags = new SessionFlags();

    // ======================================================================
    // §4  UI 记忆模块（RtsUiMemory）
    // ======================================================================

    /** 最近条目、快捷槽、GUI 绑定 */
    public final RtsUiMemory uiMemory = new RtsUiMemory();

    // ======================================================================
    // §5  建造模式
    // ======================================================================

    /** RTS 交互模式（INTERACT / FUNNEL / MINE / PLACE 等） */
    public BuilderMode mode = BuilderMode.INTERACT;

    // ======================================================================
    // §6  存储浏览器与合成浏览器状态
    // ======================================================================

    /** 存储浏览器 + 合成浏览器的状态（翻页、搜索、分类、排序、拼音等） */
    public final RtsBrowserState browser = new RtsBrowserState();

    // ======================================================================
    // §7  远程挖掘与连锁挖掘状态
    // ======================================================================

    /** 远程挖掘与连锁挖掘状态 */
    public final RtsMiningState mining = new RtsMiningState();

    /** 自动存入挖掘掉落的有界中间缓存。 */
    public final RtsMiningDropBufferState miningDropBuffer = new RtsMiningDropBufferState();

    // ======================================================================
    // §8  掉落物漏斗运行时状态
    // ======================================================================

    /** 掉落物漏斗运行时状态 */
    public final RtsFunnelState funnel = new RtsFunnelState();

    // ======================================================================
    // §9  远程 GUI 菜单状态
    // ======================================================================

    /** 远程菜单与数据版本状态 */
    public final RtsTransferState transfer = new RtsTransferState();

    // ======================================================================
    // §10  放置队列
    // ======================================================================

    /** 远程放置与回收状态 */
    public final RtsPlacementState placement = new RtsPlacementState();

    // ======================================================================
    // §11  范围破坏队列
    // ======================================================================

    /**
     * 范围破坏（AREA_DESTROY）的异步队列状态。
     *
     * <p>仅存储待处理的破坏作业和挂起的破坏作业，
     * 无业务逻辑。工具租赁仍使用 {@link #mining} 中的字段。
     */
}
