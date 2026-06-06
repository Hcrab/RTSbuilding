package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 玩家 RTS 存储会话的<strong>可变状态容器</strong>。
 *
 * <p>本类仅持有纯数据字段，按功能域分为 9 组：BD 缓存、玩家模式与链接、
 * 存储浏览器、合成浏览器、会话开关、掉落漏斗、远程挖掘、快速建造和 UI 记忆。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>纯数据容器</b>——不查询方块实体/解析能力/序列化 NBT/变更物品栏/发包</li>
 *   <li><b>职责单一</b>——所有游戏逻辑归 {@link RtsStorageManager} 及委托服务</li>
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

    // ======================================================================
    // §3  存储浏览器状态
    //      翻页、搜索、分类、排序和本地化匹配缓存。
    //      描述玩家<em>如何查看</em>存储内容，非权威物品计数。
    // ======================================================================

    /** 当前页号（0-based） */
    public int page;
    /** 每页条目数，默认从页面构建器常量读取 */
    public int pageSize = RtsStoragePageBuilder.DEFAULT_PAGE_SIZE;
    /** 搜索关键词（空串 = 无筛选） */
    public String search = "";
    /** 分类筛选："all" / "mod|namespace" / "tab|name" */
    public String category = "all";
    /** 当前排序方式：数量/名称/模组/种类 */
    public RtsStorageSort sort = RtsStorageSort.QUANTITY;
    /** true = 升序，false = 降序 */
    public boolean ascending = false;
    /** 拼音模糊搜索开关 */
    public boolean pinyinSearchEnabled;
    /** 已本地化的搜索命中 ID 集合（用于客户端高亮/快速过滤） */
    public final Set<String> localizedSearchMatches = new HashSet<>();

    // ======================================================================
    // §4  合成浏览器状态
    //      合成搜索、可用性筛选和本地化匹配缓存。
    //      请求计数默认与服务端数据包批次大小一致。
    // ======================================================================

    /** 合成搜索关键词 */
    public String craftSearch = "";
    /** 是否显示不可合成的配方 */
    public boolean craftShowUnavailable;
    /** 已请求的合成配方总数（含偏移量和限制量，至少为 CRAFTABLE_BATCH_SIZE） */
    public int craftRequestedCount = RtsStorageManager.CRAFTABLE_BATCH_SIZE;
    /** 合成搜索的拼音模糊搜索开关 */
    public boolean craftPinyinSearchEnabled;
    /** 合成搜索的本地化命中 ID 集合 */
    public final Set<String> craftLocalizedSearchMatches = new HashSet<>();

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

    // ======================================================================
    // §6  掉落物漏斗运行时状态
    //      服务端一侧的临时漏斗缓冲区和目标配置。
    //      内容为工作中间数据，不属于持久化存储。
    // ======================================================================

    /** 漏斗模式是否激活 */
    public boolean funnelEnabled;
    /** 漏斗输出目标坐标 */
    public BlockPos funnelTarget;
    /** 漏斗冷却刻数 */
    public int funnelTickCooldown;
    /** 漏斗临时缓冲区，存放待处理的掉落物 ItemStack */
    public final List<ItemStack> funnelBuffer = new ArrayList<>();

    // ======================================================================
    // §7  远程挖掘与连锁挖掘运行时状态
    //      单方块远程挖掘 + 连锁挖掘（Ultimine）的状态机数据。
    //      注意：RtsToolLease 由 RtsStorageMining 管理（涉及 NBT 工具的借用与归还）。
    // ======================================================================

    /** 当前挖掘目标坐标 */
    public BlockPos miningPos;
    /** 远程 GUI 菜单的容器 ID；-1 = 无活动远程菜单 */
    public int remoteMenuContainerId = -1;
    /** 远程 GUI 菜单对应的方块坐标 */
    public BlockPos remoteMenuPos;
    /** 连锁挖掘的待处理目标队列（先进先出） */
    public final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    /** 连锁挖掘当前正在挖掘的坐标 */
    public BlockPos ultimineProgressPos;
    /** 连锁挖掘本次任务的总目标数 */
    public int ultimineTotalTargets;
    /** 连锁挖掘已处理完成的目标数 */
    public int ultimineProcessedTargets;
    /** 连锁挖掘是否已吸收掉落物（防止重复收集，由管理器控制） */
    public boolean ultimineAbsorbedDrops;
    /** 挖掘方向（默认为下） */
    public Direction miningFace = Direction.DOWN;
    /** 当前使用的工具栏格索引 */
    public int miningToolSlot;
    /** 当前借用的远程挖掘工具租约（RtsToolLease 封装工具栈和来源信息） */
    public RtsToolLease miningToolLease = RtsToolLease.empty();
    /** 当前挖掘进度[0.0, 1.0]，服务端按 tick 递增 */
    public float miningProgress;
    /** 当前破坏阶段索引；-1 = 尚未开始 */
    public int miningStage = -1;
    /** 下次检测 RTS 任务或进度的 tick 时间 */
    public long nextQuestDetectTick;
    /** 挂起的存储刷新 tick（-1L = 无待执行的刷新） */
    public long deferredStorageRefreshTick = -1L;

    // ======================================================================
    // §8  快速建造音效与放置队列
    //      快速建造触发后的音效回放状态和尚未执行的方块放置批次。
    //      PlaceBatchJob 类型定义在 RtsStoragePlacement 中。
    // ======================================================================

    /** 本次快速建造已放置的方块计数（用于音效反馈和客户端同步） */
    public int quickBuildSoundPlacedCount;
    /** 快速建造完成音效的计划触发 tick；-1L = 未计划 */
    public long quickBuildCompletionSoundTick = -1L;
    /** 上次放置音效的 tick（防刷屏） */
    public long lastQuickBuildPlaceSoundTick = Long.MIN_VALUE;
    /** 音效播放位置 X */
    public double quickBuildSoundX;
    /** 音效播放位置 Y */
    public double quickBuildSoundY;
    /** 音效播放位置 Z */
    public double quickBuildSoundZ;
    /** 待处理的放置批次作业队列 */
    public final Deque<RtsStoragePlacement.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();

    // ======================================================================
    // §9  UI 记忆：最近条目、快捷槽与外部 GUI 绑定
    //      短时 UI 状态，用于改善玩家操作体验。
    // ======================================================================

    /** 最近访问/移动的物品或流体记录队列（上限由客户端控制） */
    public final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    /** 快捷槽物品 ID 数组；空串 = 空槽，大小由 QUICK_SLOT_COUNT 固定 */
    public final String[] quickSlotItemIds = new String[RtsStorageManager.QUICK_SLOT_COUNT];
    /** 外部方块 GUI 绑定数组（允许从 RTS 模式一键打开箱子/机器界面） */
    public final GuiBinding[] guiBindings = new GuiBinding[RtsStorageManager.GUI_BINDING_SLOT_COUNT];

    // ======================================================================
    // 构造器
    // ======================================================================

    public RtsStorageSession() {
        Arrays.fill(this.quickSlotItemIds, "");
    }
}
