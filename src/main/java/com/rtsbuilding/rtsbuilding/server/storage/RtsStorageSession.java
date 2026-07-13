package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家 RTS 存储会话的顶层可变状态容器。
 *
 * <p>本类只保留真正跨功能域共享的会话身份、链接信息、BD 缓存和 UI 记忆。
 * 浏览器、挖掘、放置、漏斗和远程菜单状态分别下沉到 {@link #browser}、
 * {@link #mining}、{@link #placement}、{@link #funnel}、{@link #transfer}。
 * 这样后续改某一条业务链时，不需要继续把所有临时字段都塞回总 session。</p>
 *
 * <p>约束：这里仍然是纯数据容器，不解析方块实体、不修改物品栏、不发包、
 * 不序列化 NBT。业务动作应留在 service / pipeline 层。</p>
 */
public class RtsStorageSession {

    /** 兼容旧调用点的常量入口；实际职责属于 {@link RtsBrowserState}。 */
    public static final int CRAFTABLE_BATCH_SIZE = RtsBrowserState.CRAFTABLE_BATCH_SIZE;

    // ======================================================================
    // BD 网络缓存
    // ======================================================================

    /** BD 网络物品处理器缓存，null 表示未缓存。 */
    public IItemHandler cachedBdHandler;
    /** BD 网络流体处理器缓存，null 表示未缓存。 */
    public IFluidHandler cachedBdFluidHandler;
    /** BD 网络显示名称缓存。 */
    public String cachedBdName;
    /** BD 物品 handler 是否需要刷新快照。 */
    public boolean bdHandlerStale;
    /** BD 流体 handler 是否需要重新解析。 */
    public boolean bdFluidHandlerStale;

    // ======================================================================
    // 玩家模式与存储链接
    // ======================================================================

    /** RTS 交互模式。 */
    public BuilderMode mode = BuilderMode.INTERACT;
    /** 已链接存储方块的稳定引用列表。 */
    public final List<LinkedStorageRef> linkedStorages = new ArrayList<>();
    /** 链接显示名称缓存。 */
    public final Map<LinkedStorageRef, String> linkedNames = new HashMap<>();
    /** 链接权限位缓存。 */
    public final Map<LinkedStorageRef, Byte> linkedModes = new HashMap<>();
    /** AE-style linked storage priority. Default 0 keeps old saves and old links neutral. */
    public final Map<LinkedStorageRef, Integer> linkedPriorities = new HashMap<>();
    /** Sophisticated Backpacks content UUID for linked backpack blocks. */
    public final Map<LinkedStorageRef, UUID> linkedBackpackUuids = new HashMap<>();
    /** Backpack item id used to reopen UUID-backed contents when the block was moved. */
    public final Map<LinkedStorageRef, String> linkedBackpackItemIds = new HashMap<>();
    /** UUID-backed backpack refs that were just broken and should not render at their old position. */
    public final Set<LinkedStorageRef> detachedBackpackRefs = new HashSet<>();

    // ======================================================================
    // 按功能域拆分的运行时状态
    // ======================================================================

    /** 存储浏览器与合成浏览器状态。 */
    public final RtsBrowserState browser = new RtsBrowserState();
    /** 远程挖掘与连锁挖掘状态。 */
    public final RtsMiningState mining = new RtsMiningState();
    /** 自动存入挖掘掉落的有界中间缓存。 */
    public final RtsMiningDropBufferState miningDropBuffer = new RtsMiningDropBufferState();
    /** 掉落物漏斗状态。 */
    public final RtsFunnelState funnel = new RtsFunnelState();
    /** 远程菜单与页面版本状态。 */
    public final RtsTransferState transfer = new RtsTransferState();
    /** 远程放置与放置回收状态。 */
    public final RtsPlacementState placement = new RtsPlacementState();

    // ======================================================================
    // 会话开关与虚拟流体存储
    // ======================================================================

    /** 是否将 BD 网络作为统一存储后端参与链接解析。 */
    public boolean useBdNetwork = true;
    /** 远程挖掘掉落物是否自动存入已链接存储。 */
    public boolean autoStoreMinedDrops = true;
    /** 虚拟流体容量，流体注册名 -> 容量(mB)。 */
    public final Map<String, Long> internalFluidMb = new HashMap<>();

    // ======================================================================
    // UI 记忆
    // ======================================================================

    /** 最近访问/移动的物品或流体记录队列。 */
    public final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    /** 快捷槽物品 ID 数组；空串表示空槽。 */
    public final String[] quickSlotItemIds = new String[RtsStorageBindings.QUICK_SLOT_COUNT];
    /** 快捷槽完整预览堆栈，用于保留带组件工具图标。 */
    public final ItemStack[] quickSlotPreviews = new ItemStack[RtsStorageBindings.QUICK_SLOT_COUNT];
    /** 外部方块 GUI 绑定数组。 */
    public final GuiBinding[] guiBindings = new GuiBinding[RtsStorageBindings.GUI_BINDING_SLOT_COUNT];

    public RtsStorageSession() {
        Arrays.fill(this.quickSlotItemIds, "");
        Arrays.fill(this.quickSlotPreviews, ItemStack.EMPTY);
    }
}
