package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端掉落物漏斗（Funnel）服务，为“物品拾取”按钮提供实际功能。
 *
 * <p><b>点击模式（球心吸取）：</b>客户端左键指定目标方块后，服务端以目标方块位置为球心、
 * 半径 2 格的球形区域为范围，每 tick 最多吸取 16 个物品到储存空间，直到区域内掉落物全部
 * 吸取完毕（或区域失效）后自动结束。
 *
 * <p><b>框选模式（实体吸取）：</b>客户端将框选区域内收集到的掉落物实体 ID 同步到服务端，
 * 服务端按 ID 查找对应 {@link ItemEntity} 并一次性吸取（附带世界目标安全校验，防止隔空吸物）。
 *
 * <p><b>存放机制：</b>遵循储存空间的存入机制，优先合并存入链接存储（偏好已有堆叠），
 * 剩余放入玩家背包，仍有剩余则留在世界中（不丢弃物品）。
 *
 * <p><b>性能设计：</b>
 * <ul>
 *   <li>持续任务对象化（{@link FunnelTask}），缓存球心/AABB/半径，避免每 tick 重建几何对象。</li>
 *   <li>链接 handler 解析结果缓存：每 tick 仅以轻量签名（引用列表 + 元数据）对比失效，
 *       不再每 tick 执行 capability 探测；每 {@link #RESOLVE_INTERVAL} tick 强制重解析兜底
 *       “方块原位替换”等不经过会话变更的场景。</li>
 *   <li>同类物品聚合后一次插入：将“每实体两次全槽扫描”降为“每物品类型一次”，
 *       并以不可变 {@link DataComponentPatch} 共享构造 + 原地 {@code split()} 扣减，
 *       插入路径零 NBT 深拷贝。</li>
 *   <li>完整世界校验（含第三方保护插件）节流到每 {@link #FULL_CHECK_INTERVAL} tick，
 *       每 tick 仅做轻量校验（区块/高度边界/交互权限/动作范围）。</li>
 *   <li>存储缓存刷新（{@link RtsTransferInserter#refreshCache}）节流到每
 *       {@link #ALERT_INTERVAL} tick，避免持续任务逼使 UI 缓存每 tick 全量重建。</li>
 * </ul>
 *
 * <p>触发条件：RTS 相机激活、会话处于交互（INTERACT）或蓝图（BLUEPRINT）模式、
 * 目标在动作范围内。
 */
public final class RtsFunnelService {

    public static final RtsFunnelService INSTANCE = new RtsFunnelService();

    /** 球心吸取半径（格）。 */
    private static final double SPHERE_RADIUS = 2.0D;

    /** 每 tick 最多提取的物品数量。 */
    private static final int MAX_ITEMS_PER_TICK = 16;

    /** 完整世界校验（含第三方保护插件）的间隔 tick（1 秒）。 */
    private static final int FULL_CHECK_INTERVAL = 20;

    /** 强制重新解析链接 handler 的间隔 tick（5 秒），兜底不经过会话变更的方块替换场景。 */
    private static final int RESOLVE_INTERVAL = 100;

    /** 存储缓存刷新（alert）的节流间隔 tick（0.5 秒）。 */
    private static final int ALERT_INTERVAL = 10;

    /** 玩家 UUID → 持续吸取任务。 */
    private final Map<UUID, FunnelTask> sphereTargets = new ConcurrentHashMap<>();

    /** 已开启物品拾取（漏斗）的玩家：由客户端 SET_FUNNEL 同步，未开启时拒绝漏斗请求。 */
    private final Set<UUID> funnelEnabledPlayers = ConcurrentHashMap.newKeySet();

    private RtsFunnelService() {
    }

    // ── 入口：点击模式（球心持续吸取） ──────────────────────────────────────

    /**
     * 客户端同步物品拾取（漏斗）开关：开启后才允许处理漏斗请求。
     */
    public void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return;
        }
        if (enabled) {
            funnelEnabledPlayers.add(player.getUUID());
        } else {
            funnelEnabledPlayers.remove(player.getUUID());
            sphereTargets.remove(player.getUUID());
        }
    }

    /**
     * 客户端左键请求：以目标方块为球心注册持续吸取任务，并立即处理一个 tick 以获得即时反馈。
     */
    public void onFunnelPickupRequest(ServerPlayer player, BlockPos center) {
        if (player == null || center == null) {
            return;
        }
        if (!funnelEnabledPlayers.contains(player.getUUID())) {
            return;
        }
        // 逐级校验并提示失败原因（节流），避免“球体可见但静默不吸收”的困惑
        if (validate(player) == null) {
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, center)) {
            return;
        }
        sphereTargets.put(player.getUUID(), new FunnelTask(center.immutable()));
        tickPlayer(player);
    }

    // ── 入口：框选模式（按实体 ID 一次性吸取） ──────────────────────────────

    /**
     * 客户端同步框选区域内的掉落物实体 ID，服务端按 ID 查找并一次性吸取到储存空间。
     *
     * <p>每个目标实体都经过 {@link RtsLinkedStorageResolver#canAccessWorldTarget} 校验
     * （相机激活/动作范围/保护插件/维度边界），防止恶意客户端隔空吸取任意实体。
     */
    public void onFunnelBoxPickupRequest(ServerPlayer player, List<Integer> entityIds) {
        if (!funnelEnabledPlayers.contains(player.getUUID())) {
            return;
        }
        RtsStorageSession session = validate(player);
        if (session == null || entityIds == null || entityIds.isEmpty()) {
            return;
        }
        List<ItemEntity> drops = new ArrayList<>(entityIds.size());
        for (int id : entityIds) {
            if (player.serverLevel().getEntity(id) instanceof ItemEntity ie
                    && ie.isAlive() && !ie.getItem().isEmpty()
                    && RtsLinkedStorageResolver.canAccessWorldTarget(player, BlockPos.containing(ie.position()))) {
                drops.add(ie);
            }
        }
        if (drops.isEmpty()) {
            return;
        }
        // 一次性请求：解析一次 handlers，聚合后批量吸收（无节流需求）
        List<IItemHandler> handlers = resolveHandlers(player, session);
        if (absorbAggs(player, handlers, aggregate(drops), Integer.MAX_VALUE, null)) {
            RtsTransferInserter.refreshCache(player);
        }
    }

    // ── 每 tick 驱动 ────────────────────────────────────────────────────────

    /**
     * 服务端每 tick 调用：持续推进所有玩家的球心吸取任务，直到区域清空或任务失效。
     */
    public void onServerTick(MinecraftServer server) {
        if (sphereTargets.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, FunnelTask>> it = sphereTargets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, FunnelTask> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !tickPlayer(player, entry.getValue())) {
                it.remove();
            }
        }
    }

    /**
     * 玩家断开时清理其持续吸取任务与漏斗开关状态。
     */
    public void onPlayerDisconnect(ServerPlayer player) {
        if (player != null) {
            sphereTargets.remove(player.getUUID());
            funnelEnabledPlayers.remove(player.getUUID());
        }
    }

    // ── 核心逻辑 ─────────────────────────────────────────────────────────────

    /**
     * 处理请求时立即执行一个 tick（复用任务推进逻辑）。
     */
    private void tickPlayer(ServerPlayer player) {
        FunnelTask task = sphereTargets.get(player.getUUID());
        if (task != null) {
            tickPlayer(player, task);
        }
    }

    /**
     * 处理一个玩家的一个 tick：球形扫描并吸取最多 {@link #MAX_ITEMS_PER_TICK} 个物品。
     *
     * @return {@code true} 表示区域仍可能有掉落物（或本次吸取未完成），任务应继续；
     *         {@code false} 表示区域已清空或任务失效，应移除任务。
     */
    private boolean tickPlayer(ServerPlayer player, FunnelTask task) {
        RtsStorageSession session = validate(player);
        if (session == null) {
            return false;
        }
        // 轻量世界校验（每 tick）：区块加载 / 高度边界 / 交互权限 / 动作范围
        if (!lightWorldCheck(player, task)) {
            return false;
        }
        // 完整世界校验（含第三方保护插件查询）节流
        if (++task.ticksSinceFullCheck >= FULL_CHECK_INTERVAL) {
            task.ticksSinceFullCheck = 0;
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, task.center)) {
                return false;
            }
        }
        // 链接 handler 缓存：达到兜底间隔时强制重解析，否则按签名对比失效
        if (++task.ticksSinceResolve >= RESOLVE_INTERVAL) {
            task.ticksSinceResolve = 0;
            task.handlersSignature = Long.MIN_VALUE;
        }
        long signature = computeHandlersSignature(session);
        if (signature != task.handlersSignature) {
            task.handlers = resolveHandlers(player, session);
            task.handlersSignature = signature;
        }

        // 球形扫描（几何数据已随任务缓存，此处仅一次实体查询）
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                task.searchBox,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty()
                        && entity.position().distanceToSqr(task.centerPos) <= task.radiusSqr);
        if (drops.isEmpty()) {
            // 区域已清空 → 吸取完毕，结束任务
            return false;
        }
        // 若本次 tick 没有任何物品被成功存入（存储/背包均满），继续空转无意义 → 结束任务
        return absorbAggs(player, task.handlers, aggregate(drops), MAX_ITEMS_PER_TICK, task);
    }

    /**
     * 轻量世界校验（每 tick 执行，避免调用第三方保护插件）。
     */
    private static boolean lightWorldCheck(ServerPlayer player, FunnelTask task) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(task.center)) {
            return false;
        }
        if (task.center.getY() < level.getMinBuildHeight() || task.center.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.mayInteract(player, task.center)) {
            return false;
        }
        return RtsCameraManager.isWithinActionRange(player, task.center);
    }

    /**
     * 将掉落物按（物品, 组件补丁）聚合成组，每组只保留物品类型、组件补丁与成员实体，
     * 构建过程零 NBT 深拷贝（组件补丁为不可变共享引用）。
     */
    private static List<Agg> aggregate(List<ItemEntity> drops) {
        List<Agg> aggs = new ArrayList<>(Math.min(drops.size(), 8));
        outer:
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            for (Agg agg : aggs) {
                if (agg.matches(stack)) {
                    agg.total += stack.getCount();
                    agg.members.add(drop);
                    continue outer;
                }
            }
            aggs.add(new Agg(drop));
        }
        return aggs;
    }

    /**
     * 将聚合组列表依次存入储存空间（链接存储优先，回退玩家背包），按组扣减实体物品。
     *
     * <p>预算按“物品数”扣减（与逐实体版本语义一致）；某组全部存不进时不消耗预算，
     * 继续尝试下一组。任务模式下存储刷新（alert）受 {@link #ALERT_INTERVAL} 节流。
     *
     * @return 是否有任何物品被成功存入
     */
    private static boolean absorbAggs(ServerPlayer player, List<IItemHandler> handlers,
                                      List<Agg> aggs, int budget, FunnelTask task) {
        boolean changed = false;
        for (Agg agg : aggs) {
            if (budget <= 0) {
                break;
            }
            int take = Math.min(agg.total, budget);
            if (take <= 0) {
                continue;
            }
            // 零拷贝构造：共享不可变组件补丁，避免 per-group NBT 深拷贝
            ItemStack toStore = new ItemStack(agg.item.builtInRegistryHolder(), take, agg.patch);
            ItemStack remain = handlers.isEmpty()
                    ? toStore
                    : RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, toStore);
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            int stored = take - remain.getCount();
            if (stored <= 0) {
                continue;
            }
            budget -= stored;
            consume(agg, stored);
            changed = true;
        }
        if (changed) {
            if (task != null) {
                // 节流：持续任务期间最多每 ALERT_INTERVAL tick 通知一次存储缓存刷新
                if (++task.ticksSinceAlert >= ALERT_INTERVAL) {
                    task.ticksSinceAlert = 0;
                    RtsTransferInserter.refreshCache(player);
                }
            } else {
                // 一次性请求：直接刷新，无节流需求
                RtsTransferInserter.refreshCache(player);
            }
        }
        return changed;
    }

    /**
     * 从聚合组的成员实体中按顺序扣减指定数量：原地 {@code split()} 切分（零拷贝），
     * 取空的实体丢弃，其余实体仅做一次 {@code setItem} 触发同步。
     */
    private static void consume(Agg agg, int amount) {
        for (ItemEntity member : agg.members) {
            if (amount <= 0) {
                break;
            }
            ItemStack stack = member.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int take = Math.min(stack.getCount(), amount);
            stack.split(take);
            amount -= take;
            if (stack.isEmpty()) {
                member.discard();
            } else {
                member.setItem(stack);
            }
        }
    }

    /**
     * 解析并排序链接存储 handler（插入优先序）。仅在本 tick 签名变化或到达兜底间隔时调用。
     */
    private static List<IItemHandler> resolveHandlers(ServerPlayer player, RtsStorageSession session) {
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        return RtsLinkedStorageResolver.itemHandlersForInsert(linked);
    }

    /**
     * 计算链接存储的轻量签名：引用列表（维度/坐标/模式/优先级/背包元数据/脱离状态）
     * 与 BD 网络缓存状态。签名变化即表示需要重新解析 handler。
     *
     * <p>签名冲突（哈希碰撞）时最多延迟到 {@link #RESOLVE_INTERVAL} 兜底重解析，无正确性风险。
     */
    private static long computeHandlersSignature(RtsStorageSession session) {
        long hash = 1L;
        hash = hash * 31L + (session.sessionFlags.useBdNetwork ? 1L : 0L);
        hash = hash * 31L + (session.bdCache.handlerStale ? 1L : 0L);
        hash = hash * 31L + (session.bdCache.handler != null ? 1L : 0L);
        for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
            if (ref == null || ref.pos() == null) {
                continue;
            }
            hash = hash * 31L + ref.hashCode();
            hash = hash * 31L + session.linkedStorageInfo.getMode(ref);
            hash = hash * 31L + session.linkedStorageInfo.getPriority(ref);
            UUID uuid = session.linkedStorageInfo.getBackpackUuid(ref);
            hash = hash * 31L + (uuid == null ? 0L : uuid.hashCode());
            String itemId = session.linkedStorageInfo.getBackpackItemId(ref);
            hash = hash * 31L + (itemId == null ? 0L : itemId.hashCode());
            hash = hash * 31L + (session.linkedStorageInfo.isDetached(ref) ? 1L : 0L);
        }
        return hash;
    }

    /**
     * 校验漏斗触发条件：RTS 相机激活、会话存在、处于交互或蓝图模式。
     *
     * @return 校验通过时返回会话对象（供调用方复用，避免重复查询），否则 {@code null}
     */
    private static RtsStorageSession validate(ServerPlayer player) {
        if (player == null || !RtsCameraManager.isActive(player)) {
            return null;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) {
            return null;
        }
        if (session.mode != BuilderMode.INTERACT && session.mode != BuilderMode.BLUEPRINT) {
            return null;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        return session;
    }

    // ── 内部结构 ─────────────────────────────────────────────────────────────

    /**
     * 单个玩家的持续球心吸取任务：缓存几何数据、handler 解析结果与节流状态。
     * 仅在 server tick 线程与网络包处理线程访问（并发容器兜底），字段无需 volatile。
     */
    private static final class FunnelTask {
        final BlockPos center;
        final Vec3 centerPos;
        final AABB searchBox;
        final double radiusSqr;

        /** 缓存的插入序 handler 列表（由链接签名决定失效）。 */
        List<IItemHandler> handlers = List.of();
        long handlersSignature = Long.MIN_VALUE;
        int ticksSinceResolve;
        int ticksSinceFullCheck;
        int ticksSinceAlert;

        FunnelTask(BlockPos center) {
            this.center = center;
            this.centerPos = Vec3.atCenterOf(center);
            this.searchBox = new AABB(center).inflate(SPHERE_RADIUS);
            this.radiusSqr = SPHERE_RADIUS * SPHERE_RADIUS;
        }
    }

    /**
     * 同物品（含组件补丁）掉落物聚合组：以不可变 {@link DataComponentPatch} 标识物品变体，
     * 构建与吸收全程零 NBT 深拷贝。
     */
    private static final class Agg {
        final Item item;
        final DataComponentPatch patch;
        int total;
        final List<ItemEntity> members = new ArrayList<>(4);

        Agg(ItemEntity first) {
            ItemStack stack = first.getItem();
            this.item = stack.getItem();
            this.patch = stack.getComponentsPatch();
            this.total = stack.getCount();
            this.members.add(first);
        }

        /** 与 {@link ItemStack#isSameItemSameComponents} 等价的可合并判断。 */
        boolean matches(ItemStack stack) {
            return stack.getItem() == this.item && stack.getComponentsPatch().equals(this.patch);
        }
    }
}
