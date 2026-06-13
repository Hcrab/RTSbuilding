package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * 玩家 RTS 存储会话的<strong>可变状态容器</strong>。
 *
 * <p>本类仅持有纯数据字段，按功能域分为 9 组：BD 缓存、玩家模式与链接、
 * 存储浏览器、合成浏览器、会话开关、掉落漏斗、远程挖掘、快速建造和 UI 记忆。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不查询方块实体/解析能力/序列化 NBT/变更物品栏/发包</li>
 *   <li><b>保持兼容</b>——字段名称和类型已定型，外部引用依赖它们精确匹配</li>
 * </ul>
 *
 * <h3>未来演进</h3>
 * 如需从本类提取独立模块，按字段组整体迁移即可：
 * <pre>
 *   // 迁移前：session.search, session.page
 *   // 迁移后：session.browser().search(), session.browser().page()
 * </pre>
 */
public class RtsStorageSession {

    // ======================================================================
    // §1  BD 网络缓存（NeoForge only）
    //     Better Description 网络处理器和名称缓存。
    //     由 RtsLinkedStorageResolver 按需填充和清除。
    // ======================================================================

    /** BD 网络物品处理器（IItemHandler 包装），null = 未缓存 */
    public IItemHandler cachedBdHandler;
    /** BD 网络流体处理器（IFluidHandler 包装），null = 未缓存 */
    public IFluidHandler cachedBdFluidHandler;
    /** BD 网络显示名称 */
    public String cachedBdName;

    /**
     * Stale flag for BD network caches. Set to {@code true} by the page
     * service before resolving handlers so the resolver refreshes the
     * handler's internal cache instead of re-creating it (which would
     * trigger an unnecessary unmount/mount cycle).
     */
    public boolean bdHandlerStale;

    /**
     * Stale flag for BD network fluid cache. Set to {@code true} by the page
     * service before resolving handlers so the resolver re-creates the fluid
     * handler instead of skipping a stale reference.
     */
    public boolean bdFluidHandlerStale;

    // ======================================================================
    // §2  玩家模式与存储链接
    //      当前 RTS 交互模式以及玩家已链接的所有存储方块。
    //      LinkedStorageRef 提供稳定身份，names/modes 是管理器派生的缓存数据。
    // ======================================================================

    /** RTS 交互模式（INTERACT / FUNNEL / MINE / PLACE 等） */
    public BuilderMode mode = BuilderMode.INTERACT;

    /** 已链接存储方块的稳定引用列表；
      * 以 (维度, 坐标) 为唯一标识，不依赖方块实体存活状态 */
    public final List<LinkedStorageRef> linkedStorages = new ArrayList<>();

    /** 缓存：{@code ref -> 显示名称}，由管理器按需扫描更新 */
    public final Map<LinkedStorageRef, String> linkedNames = new HashMap<>();

    /** 缓存：{@code ref -> 操作权限位掩码}，由管理器按需扫描更新 */
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
    // §3 + §4  存储浏览器与合成浏览器状态
    // ======================================================================

    /** 存储浏览器 + 合成浏览器的状态（翻页、搜索、分类、排序、拼音等） */
    public final RtsBrowserState browser = new RtsBrowserState();

    // ======================================================================
    // §5  会话开关与虚拟流体存储
    //      影响全局行为的二元开关和客户端侧虚拟流体容量。
    // ======================================================================

    /** 是否将 BD 网络作为统一存储后端参与链接解析 */
    public boolean useBdNetwork = true;
    /** 远程挖掘的掉落物是否自动存入已链接存储（避免手动拾取） */
    public boolean autoStoreMinedDrops = true;
    /** 虚拟流体容量，{@code 流体注册名 -> 容量(mB)}；
      * 用于在没有任何真实流体处理器时展示虚拟流体槽 */
    public final Map<String, Long> internalFluidMb = new HashMap<>();

    /** 远程挖掘与连锁挖掘状态 */
    public final RtsMiningState mining = new RtsMiningState();

    // ======================================================================
    // §6  掉落物漏斗运行时状态
    // ======================================================================

    /** 掉落物漏斗运行时状态 */
    public final RtsFunnelState funnel = new RtsFunnelState();

    // ======================================================================
    // §7  远程 GUI 菜单状态
    // ======================================================================

    /** 远程菜单与数据版本状态 */
    public final RtsTransferState transfer = new RtsTransferState();

    // ======================================================================
    // §8  放置队列
    // ======================================================================

    /** 远程放置与回收状态 */
    public final RtsPlacementState placement = new RtsPlacementState();

    // ======================================================================
    // §9  UI 记忆：最近条目、快捷槽与外部 GUI 绑定
    //      短时 UI 状态，用于改善玩家操作体验。
    // ======================================================================

    /** 最近访问/移动的物品或流体记录队列（上限由客户端控制） */
    public final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    /** 快捷槽物品 ID 数组；空串 = 空槽，大小由 QUICK_SLOT_COUNT 固定 */
    public final String[] quickSlotItemIds = new String[RtsStorageBindings.QUICK_SLOT_COUNT];
    /** Full client-facing preview stacks for pinned quick slots. Keeps component-heavy tool icons intact. */
    public final ItemStack[] quickSlotPreviews = new ItemStack[RtsStorageBindings.QUICK_SLOT_COUNT];
    /** 外部方块 GUI 绑定数组（允许从 RTS 模式一键打开箱子/机器界面） */
    public final GuiBinding[] guiBindings = new GuiBinding[RtsStorageBindings.GUI_BINDING_SLOT_COUNT];

    // ======================================================================
    // 构造器
    // ======================================================================

    public RtsStorageSession() {
        Arrays.fill(this.quickSlotItemIds, "");
        Arrays.fill(this.quickSlotPreviews, ItemStack.EMPTY);
    }
}
