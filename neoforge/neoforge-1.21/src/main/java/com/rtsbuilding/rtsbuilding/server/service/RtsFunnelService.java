package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
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
 * 服务端按 ID 查找对应 {@link ItemEntity} 并一次性吸取。
 *
 * <p><b>存放机制：</b>遵循储存空间的存入机制，优先合并存入链接存储（偏好已有堆叠），
 * 剩余放入玩家背包，仍有剩余则留在世界中（不丢弃物品）。
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

    /** 玩家 UUID → 持续吸取的球心目标方块。 */
    private final Map<UUID, BlockPos> sphereTargets = new ConcurrentHashMap<>();

    /** 已开启物品拾取（漏斗）的玩家：由客户端 SET_FUNNEL 同步，未开启时拒绝漏斗请求。 */
    private final Set<UUID> funnelEnabledPlayers = ConcurrentHashMap.newKeySet();

    /** 玩家 UUID → 上次失败提示时间（节流，避免每 tick 刷屏）。 */
    private final Map<UUID, Long> lastHintAt = new ConcurrentHashMap<>();

    /** 失败提示节流间隔（毫秒）。 */
    private static final long HINT_INTERVAL_MS = 10_000L;

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
            hint(player, "§c[RTS] 请先开启物品拾取（漏斗）");
            return;
        }
        // 逐级校验并提示失败原因（节流），避免“球体可见但静默不吸收”的困惑
        if (!RtsCameraManager.isActive(player)) {
            hint(player, "§c[RTS] 漏斗需要先开启 RTS 相机模式（使用终端开启）");
            return;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null
                || (session.mode != BuilderMode.INTERACT && session.mode != BuilderMode.BLUEPRINT)) {
            hint(player, "§c[RTS] 漏斗仅在交互/蓝图模式下生效");
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, center)) {
            hint(player, "§c[RTS] 目标超出可操作范围或被保护插件拒绝");
            return;
        }
        sphereTargets.put(player.getUUID(), center.immutable());
        tickPlayer(player);
    }

    /**
     * 节流失败提示：同一玩家在 {@link #HINT_INTERVAL_MS} 内只提示一次。
     */
    private void hint(ServerPlayer player, String text) {
        long now = System.currentTimeMillis();
        Long last = lastHintAt.get(player.getUUID());
        if (last != null && now - last < HINT_INTERVAL_MS) {
            return;
        }
        lastHintAt.put(player.getUUID(), now);
        player.displayClientMessage(Component.literal(text), true);
    }

    // ── 入口：框选模式（按实体 ID 一次性吸取） ──────────────────────────────

    /**
     * 客户端同步框选区域内的掉落物实体 ID，服务端按 ID 查找并一次性吸取到储存空间。
     */
    public void onFunnelBoxPickupRequest(ServerPlayer player, List<Integer> entityIds) {
        if (!funnelEnabledPlayers.contains(player.getUUID())) {
            return;
        }
        if (!validate(player) || entityIds == null || entityIds.isEmpty()) {
            return;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) {
            return;
        }
        List<ItemEntity> drops = new ArrayList<>(entityIds.size());
        for (int id : entityIds) {
            if (player.serverLevel().getEntity(id) instanceof ItemEntity ie
                    && ie.isAlive() && !ie.getItem().isEmpty()) {
                drops.add(ie);
            }
        }
        if (!drops.isEmpty()) {
            absorbDrops(player, session, drops, Integer.MAX_VALUE);
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
        for (Map.Entry<UUID, BlockPos> entry : sphereTargets.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                sphereTargets.remove(entry.getKey());
                continue;
            }
            if (!tickPlayer(player)) {
                sphereTargets.remove(entry.getKey());
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
     * 处理一个玩家的一个 tick：球形扫描并吸取最多 {@link #MAX_ITEMS_PER_TICK} 个物品。
     *
     * @return {@code true} 表示区域仍可能有掉落物（或本次吸取未完成），任务应继续；
     *         {@code false} 表示区域已清空或任务失效，应移除任务。
     */
    private boolean tickPlayer(ServerPlayer player) {
        if (!validate(player)) {
            return false;
        }
        BlockPos center = sphereTargets.get(player.getUUID());
        if (center == null) {
            return false;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, center)) {
            return false;
        }

        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) {
            return false;
        }

        Vec3 centerPos = Vec3.atCenterOf(center);
        AABB box = new AABB(center).inflate(SPHERE_RADIUS);
        double radiusSqr = SPHERE_RADIUS * SPHERE_RADIUS;
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty()
                        && entity.position().distanceToSqr(centerPos) <= radiusSqr);
        if (drops.isEmpty()) {
            // 区域已清空 → 吸取完毕，结束任务
            return false;
        }

        // 若本次 tick 没有任何物品被成功存入（存储/背包均满），继续空转无意义 → 结束任务
        return absorbDrops(player, session, drops, MAX_ITEMS_PER_TICK);
    }

    /**
     * 将掉落物实体列表吸取到储存空间（遵循储存空间存入机制）：
     * 链接存储（偏好已有堆叠）→ 玩家背包 → 剩余留在世界。
     */
    private boolean absorbDrops(ServerPlayer player, RtsStorageSession session,
                                List<ItemEntity> drops, int budget) {
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        boolean changed = false;
        for (ItemEntity drop : drops) {
            if (budget <= 0) {
                break;
            }
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            int take = Math.min(original.getCount(), budget);
            ItemStack toStore = original.copyWithCount(take);
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
            // 实体保留“未处理部分”（count - take）与“已取但未存进的部分”（remain），
            // 不能直接 discard/setItem(remain)，否则堆叠数量超过预算时会吞掉未取部分
            ItemStack left = original.copy();
            left.shrink(stored);
            if (left.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(left);
            }
            changed = true;
        }
        if (changed) {
            // 让存储页面缓存尽快刷新
            RtsTransferInserter.refreshCache(player);
        }
        return changed;
    }

    /**
     * 校验漏斗触发条件：RTS 相机激活、会话存在、处于交互或蓝图模式。
     */
    private boolean validate(ServerPlayer player) {
        if (player == null || !RtsCameraManager.isActive(player)) {
            return false;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session == null) {
            return false;
        }
        if (session.mode != BuilderMode.INTERACT && session.mode != BuilderMode.BLUEPRINT) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        return true;
    }
}
