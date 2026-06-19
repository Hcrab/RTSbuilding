package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.progression.resolver.RtsCapabilityResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 生存模式进度管理器 —— 管理玩家的科技树解锁、能力派生、家园选址和网络同步。
 *
 * <p>此类是 RTS 生存模式进度系统的门面（Facade），统一提供：</p>
 * <ul>
 *   <li><b>能力查询</b> —— 根据已解锁的科技节点，计算玩家当前的能力值
 *       （行动半径、流体容量、连锁挖掘上限等）</li>
 *   <li><b>功能门控</b> —— 判断玩家是否可以使用某功能
 *       （{@link #canUse(ServerPlayer, RtsFeature)}）</li>
 *   <li><b>家园管理</b> —— 委托给 {@link RtsHomeManager}，提供家园的
 *       创建、查询、冷却等操作</li>
 *   <li><b>节点解锁</b> —— 验证前置条件、消耗材料、解锁进度节点</li>
 *   <li><b>网络同步</b> —— 登录时及解锁后将进度状态同步到客户端</li>
 * </ul>
 *
 * <p>当 {@link Config#ENABLE_SURVIVAL_PROGRESSION} 关闭时，所有功能
 * 无限制可用，所有查询返回默认最大值。</p>
 */
public final class RtsProgressionManager {

    /**
     * 默认最大行动半径（方块），在进度系统关闭时使用。
     */
    public static final int DEFAULT_MAX_ACTION_RADIUS_BLOCKS = 128;

    /**
     * 默认流体容量（桶数），在进度系统关闭时使用。
     */
    public static final int DEFAULT_FLUID_CAPACITY_BUCKETS = 100;

    /**
     * 默认连锁挖掘上限（方块数），在进度系统关闭时使用。
     */
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;

    /**
     * 家园选择的有效半径（方块），玩家只能在此半径内选择家园坐标。
     */
    public static final int HOME_SELECTION_RADIUS_BLOCKS = 34;

    /**
     * 家园搬迁冷却时间（游戏天数）。
     */
    public static final int HOME_RELOCATION_COOLDOWN_DAYS = 20;

    /**
     * 每个游戏天的 Tick 数（1 天 = 20 分钟 × 60 秒 × 20 TPS）。
     */
    public static final long TICKS_PER_GAME_DAY = 24000L;

    /**
     * 家园搬迁冷却时间（Tick 数）。
     */
    public static final long HOME_RELOCATION_COOLDOWN_TICKS =
            HOME_RELOCATION_COOLDOWN_DAYS * TICKS_PER_GAME_DAY;

    /**
     * 能力解析器实例，依据已解锁节点推导出玩家的各项能力值。
     */
    private static final RtsCapabilityResolver RESOLVER =
            new RtsCapabilityResolver(RtsProgressionNodes.tree());

    /** 工具类，禁止实例化。 */
    private RtsProgressionManager() {
    }

    // ======================================================================
    //  启用状态
    // ======================================================================

    /**
     * 判断生存模式进度系统是否启用。
     *
     * <p>如果未启用，所有功能均无限制可用，
     * 能力查询方法将返回对应的默认最大值。</p>
     *
     * @return {@code true} 表示进度系统启用
     */
    public static boolean isEnabled() {
        return Config.ENABLE_SURVIVAL_PROGRESSION.getAsBoolean();
    }

    // ======================================================================
    //  能力派生（保持原地 — 使用 RESOLVER）
    // ======================================================================

    /**
     * 根据玩家已解锁的科技节点，推导出当前的全部能力值。
     *
     * <p>内部使用 {@link RtsCapabilityResolver} 解析已解锁节点集合，
     * 并对 {@code RADIUS_MAX} 节点做特殊处理：如果玩家拥有了该节点，
     * 行动半径取配置上限和解锁能力中的较大值。</p>
     *
     * @param player 要查询的玩家
     * @return 包含特征、半径、流体容量、连锁上限等能力的记录
     */
    private static DerivedCapabilities derive(ServerPlayer player) {
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
        RtsProgressionPersistence.ensureStarterUnlocked(unlocked);

        RtsCapabilityResolver.DerivedCapabilities caps = RESOLVER.resolve(unlocked);

        int radius = caps.actionRadius();
        if (unlocked.contains(RtsProgressionNodes.RADIUS_MAX)) {
            radius = Math.max(radius, Config.maxActionRadiusBlocks());
        }

        return new DerivedCapabilities(
                caps.features(),
                Math.max(16, radius),
                caps.fluidCapacityBuckets(),
                caps.ultimineLimit(),
                caps.bypassHomeRadius());
    }

    // ======================================================================
    //  特征 / 能力查询
    // ======================================================================

    /**
     * 判断玩家是否可以使用指定的功能。
     *
     * <p>当进度系统关闭时，所有功能均可用。
     * 需要玩家和功能参数均非空。</p>
     *
     * @param player 要查询的玩家
     * @param feature 要检查的功能
     * @return {@code true} 表示该功能可用
     */
    public static boolean canUse(ServerPlayer player, RtsFeature feature) {
        if (!isEnabled()) {
            return true;
        }
        if (player == null || feature == null) {
            return false;
        }
        return derive(player).features().contains(feature);
    }

    /**
     * 获取玩家的行动半径（方块数）。
     *
     * <p>行动半径决定玩家可以隔多远进行操作（如远程放置、挖掘）。
     * 当进度系统关闭时，返回配置中的最大行动半径。</p>
     *
     * @param player 要查询的玩家
     * @return 行动半径（方块），至少为 1
     */
    public static double getActionRadius(ServerPlayer player) {
        if (!isEnabled()) {
            return Config.maxActionRadiusBlocks();
        }
        return Math.max(1.0D, derive(player).radiusBlocks());
    }

    /**
     * 获取玩家的流体容量（桶数）。
     *
     * <p>流体容量决定玩家可以缓存多少桶流体。
     * 当进度系统关闭时，返回 {@link #DEFAULT_FLUID_CAPACITY_BUCKETS}。</p>
     *
     * @param player 要查询的玩家
     * @return 流体桶数，至少为 0
     */
    public static int getFluidCapacityBuckets(ServerPlayer player) {
        if (!isEnabled()) {
            return DEFAULT_FLUID_CAPACITY_BUCKETS;
        }
        return Math.max(0, derive(player).fluidCapacityBuckets());
    }

    /**
     * 获取玩家的连锁挖掘上限（方块数）。
     *
     * <p>连锁挖掘上限决定一次连锁挖掘最多可以破坏多少个方块。
     * 当进度系统关闭时，返回 {@link #DEFAULT_ULTIMINE_LIMIT}。</p>
     *
     * @param player 要查询的玩家
     * @return 连锁挖掘上限，至少为 0
     */
    public static int getUltimineLimit(ServerPlayer player) {
        if (!isEnabled()) {
            return DEFAULT_ULTIMINE_LIMIT;
        }
        return Math.max(0, derive(player).ultimineLimit());
    }

    /**
     * 判断玩家是否可以绕过家园半径限制进行操作。
     *
     * <p>当进度系统关闭时，始终可以绕过。</p>
     *
     * @param player 要查询的玩家
     * @return {@code true} 表示可以绕过家园半径限制
     */
    public static boolean canBypassHomeRadius(ServerPlayer player) {
        return !isEnabled() || derive(player).bypassHomeRadius();
    }

    // ======================================================================
    //  家 — 委托给 RtsHomeManager
    // ======================================================================

    /**
     * 判断玩家是否已设置家园。
     *
     * @param player 要查询的玩家
     * @return {@code true} 表示玩家已设置家园
     */
    public static boolean hasHome(ServerPlayer player) {
        return RtsHomeManager.hasHome(player);
    }

    /**
     * 获取玩家的家园锚点信息。
     *
     * @param player 要查询的玩家
     * @return 家园锚点，如果未设置则返回 {@code null}
     */
    public static HomeAnchor getHome(ServerPlayer player) {
        return RtsHomeManager.getHome(player);
    }

    /**
     * 判断玩家是否能在指定位置访问家园半径内的操作权限。
     *
     * @param player 要查询的玩家
     * @param pos    目标方块位置
     * @return {@code true} 表示该位置在家园半径内
     */
    public static boolean canAccessHomeRadius(ServerPlayer player, BlockPos pos) {
        return RtsHomeManager.canAccessHomeRadius(player, pos);
    }

    /**
     * 判断玩家是否可以开始正常的 RTS 操作。
     *
     * <p>当进度系统启用时，玩家必须先设置家园才能开始操作。
     * 当进度系统关闭时，始终可以开始。</p>
     *
     * @param player 要检查的玩家
     * @return {@code true} 表示可以开始正常操作
     */
    public static boolean canStartNormalRts(ServerPlayer player) {
        return !isEnabled() || RtsHomeManager.hasHome(player);
    }

    /**
     * 判断玩家是否应该进入家园选择流程。
     *
     * <p>触发条件：进度系统启用、玩家非空、尚未设置家园、
     * 玩家已解锁相机功能。</p>
     *
     * @param player 要检查的玩家
     * @return {@code true} 表示应引导玩家选择家园
     */
    public static boolean shouldStartHomeSelection(ServerPlayer player) {
        return isEnabled() && player != null && !RtsHomeManager.hasHome(player) && canUse(player, RtsFeature.CAMERA);
    }

    /**
     * 开始家园选择流程。
     *
     * @param player 要开始选择的玩家
     */
    public static void beginHomeSelection(ServerPlayer player) {
        RtsHomeManager.beginHomeSelection(player);
    }

    /**
     * 结束家园选择流程。
     *
     * @param player 要结束选择的玩家
     */
    public static void endHomeSelection(ServerPlayer player) {
        RtsHomeManager.endHomeSelection(player);
    }

    /**
     * 判断玩家是否正在家园选择流程中。
     *
     * @param player 要查询的玩家
     * @return {@code true} 表示正在选择家园
     */
    public static boolean isHomeSelectionActive(ServerPlayer player) {
        return RtsHomeManager.isHomeSelectionActive(player);
    }

    /**
     * 判断玩家能否在指定位置选择家园。
     *
     * @param player 要操作的玩家
     * @param pos    候选家园位置
     * @return {@code true} 表示该位置可用作家园
     */
    public static boolean canSelectHome(ServerPlayer player, BlockPos pos) {
        return RtsHomeManager.canSelectHome(player, pos);
    }

    /**
     * 判断玩家是否可以更改已设置的家园位置。
     *
     * @param player 要查询的玩家
     * @return {@code true} 表示可以更改家园
     */
    public static boolean canChangeHome(ServerPlayer player) {
        return RtsHomeManager.canChangeHome(player);
    }

    /**
     * 获取家园搬迁冷却的剩余时间（Tick 数）。
     *
     * @param player 要查询的玩家
     * @return 剩余冷却时间（Tick），0 表示无冷却
     */
    public static long remainingHomeCooldownTicks(ServerPlayer player) {
        return RtsHomeManager.remainingHomeCooldownTicks(player);
    }

    /**
     * 获取家园搬迁冷却的剩余时间（游戏天数）。
     *
     * @param player 要查询的玩家
     * @return 剩余冷却时间（天），0 表示无冷却
     */
    public static long remainingHomeCooldownDays(ServerPlayer player) {
        return RtsHomeManager.remainingHomeCooldownDays(player);
    }

    /**
     * 提交家园设置，将指定位置设为玩家的家园。
     *
     * <p>设置成功后，会同步状态给共享进度组中的其他玩家。</p>
     *
     * @param player 要操作的玩家
     * @param pos    家园位置
     * @return {@code true} 表示设置成功
     */
    public static boolean commitHome(ServerPlayer player, BlockPos pos) {
        if (RtsHomeManager.commitHome(player, pos)) {
            syncRelatedPlayers(player);
            return true;
        }
        return false;
    }

    // ======================================================================
    //  节点解锁
    // ======================================================================

    /**
     * 尝试为玩家解锁指定的科技节点。
     *
     * <p>解锁流程按顺序执行以下检查：</p>
     * <ol>
     *   <li>进度系统是否启用（未启用则返回禁用结果）</li>
     *   <li>节点是否存在（不存在则返回失败）</li>
     *   <li>已解锁集合中是否已包含该节点（重复解锁则返回失败）</li>
     *   <li>前置依赖节点是否全部已解锁（缺少前置则返回失败）</li>
     *   <li>玩家是否持有足够的材料（不足则返回失败）</li>
     * </ol>
     *
     * <p>全部检查通过后：</p>
     * <ol>
     *   <li>消耗材料（从玩家库存中扣除）</li>
     *   <li>将节点 ID 加入已解锁集合</li>
     *   <li>持久化保存到玩家的进度数据</li>
     *   <li>同步状态给共享进度组中的其他玩家</li>
     * </ol>
     *
     * @param player 要操作的玩家
     * @param nodeId 要解锁的节点 ID
     * @return 解锁结果，包含成功/失败状态和消息
     */
    public static UnlockResult unlockNode(ServerPlayer player, ResourceLocation nodeId) {
        if (!isEnabled()) {
            return UnlockResult.disabledResult();
        }
        RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
        if (node == null) {
            return UnlockResult.failure("Unknown RTS node.");
        }
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
        RtsProgressionPersistence.ensureStarterUnlocked(unlocked);
        if (unlocked.contains(nodeId)) {
            return UnlockResult.failure("Already unlocked.");
        }
        for (ResourceLocation dependency : node.dependencies()) {
            if (!unlocked.contains(dependency)) {
                return UnlockResult.failure("Missing prerequisite.");
            }
        }
        List<RtsIngredientCost> costs = RtsProgressionNodes.costsFor(node);
        if (!RtsProgressionPersistence.hasCosts(player, costs)) {
            return UnlockResult.failure("Missing materials.");
        }
        RtsProgressionPersistence.consumeCosts(player, costs);
        unlocked.add(nodeId);
        RtsProgressionPersistence.saveUnlockedNodes(player, unlocked);
        syncRelatedPlayers(player);
        return UnlockResult.ok();
    }

    // ======================================================================
    //  玩家生命周期
    // ======================================================================

    /**
     * 处理玩家登录时的进度初始化。
     *
     * <p>登录时执行以下操作：</p>
     * <ol>
     *   <li>加载玩家的已解锁节点集合，确保初始节点已解锁</li>
     *   <li>如果玩家属于某个共享进度组且尚未在该组中设置家园，
     *       将个人家园推送到共享组数据中</li>
     *   <li>如果解锁集合有变动或有共享组绑定，持久化保存</li>
     *   <li>向客户端发送完整的进度状态同步包</li>
     * </ol>
     *
     * @param player 登录的玩家
     */
    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (!sharedKey.isBlank()
                && RtsProgressionPersistence.sharedProgressionData(player).home(sharedKey) == null) {
            HomeAnchor personalHome = RtsHomeManager.personalHome(player);
            if (personalHome != null) {
                RtsProgressionPersistence.sharedProgressionData(player).setHome(
                        sharedKey,
                        personalHome.pos(),
                        personalHome.dimension(),
                        personalHome.setGameTime());
            }
        }
        if (RtsProgressionPersistence.ensureStarterUnlocked(unlocked) || !sharedKey.isBlank()) {
            RtsProgressionPersistence.saveUnlockedNodes(player, unlocked);
        }
        syncToPlayer(player);
    }

    /**
     * 处理玩家登出时的清理操作。
     *
     * <p>结束可能正在进行的家园选择流程，防止状态残留。</p>
     *
     * @param player 登出的玩家
     */
    public static void onPlayerLogout(ServerPlayer player) {
        RtsHomeManager.endHomeSelection(player);
    }

    // ======================================================================
    //  同步
    // ======================================================================

   /**
     * 将完整的进度状态同步到指定玩家的客户端。
     *
     * <p>发送 {@link S2CRtsProgressionStatePayload} 数据包，包含：</p>
     * <ul>
     *   <li>进度系统启用状态</li>
     *   <li>家园信息（是否已设置、位置、维度、冷却时间）</li>
     *   <li>能力值（行动半径、流体容量、连锁上限、家园绕过）</li>
     *   <li>已解锁节点列表</li>
     *   <li>当前可解锁的节点列表（即已满足前置条件和材料要求）</li>
     *   <li>配置中的消耗品覆盖项</li>
     * </ul>
     *
     * <p>当进度系统关闭时，发送一个"禁用"状态包，
     * 包含所有能力的默认最大值和空列表。</p>
     *
     * @param player 目标玩家
     */
    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> costOverrides = Config.progressionCostOverrides().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        if (!isEnabled()) {
            PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                    false,
                    false,
                    BlockPos.ZERO,
                    "",
                    0L,
                    Config.maxActionRadiusBlocks(),
                    DEFAULT_FLUID_CAPACITY_BUCKETS,
                    DEFAULT_ULTIMINE_LIMIT,
                    true,
                    List.of(),
                    List.of(),
                    costOverrides));
            return;
        }
        DerivedCapabilities derived = derive(player);
        HomeAnchor home = RtsHomeManager.getHome(player);
        LinkedHashSet<ResourceLocation> unlockedSet = RtsProgressionPersistence.unlockedNodes(player);
        if (RtsProgressionPersistence.ensureStarterUnlocked(unlockedSet)) {
            RtsProgressionPersistence.saveUnlockedNodes(player, unlockedSet);
        }
        List<String> unlocked = unlockedSet.stream().map(ResourceLocation::toString).toList();
        List<String> unlockable = RtsProgressionNodes.all().stream()
                .filter(node -> !unlockedSet.contains(node.id()))
                .filter(node -> RtsProgressionPersistence.dependenciesMet(unlockedSet, node))
                .filter(node -> RtsProgressionPersistence.hasCosts(player, RtsProgressionNodes.costsFor(node)))
                .map(node -> node.id().toString())
                .toList();
        PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                isEnabled(),
                home != null,
                home == null ? BlockPos.ZERO : home.pos(),
                home == null ? "" : home.dimension().location().toString(),
                RtsHomeManager.remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                derived.fluidCapacityBuckets(),
                derived.ultimineLimit(),
                derived.bypassHomeRadius(),
                unlocked,
                unlockable,
                costOverrides));
    }

    /**
     * 将进度状态同步给关联玩家（共享同一进度组的在线成员）。
     *
     * <p>如果玩家不隶属于任何共享进度组，仅同步自己。
     * 否则遍历所有在线玩家，找出共享同一进度组 key 的玩家并同步。</p>
     *
     * @param player 触发同步的玩家
     */
    private static void syncRelatedPlayers(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            syncToPlayer(player);
            return;
        }
        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            if (sharedKey.equals(RtsProgressionPersistence.sharedProgressionKey(onlinePlayer))) {
                syncToPlayer(onlinePlayer);
            }
        }
    }

    // ======================================================================
    //  内部记录
    // ======================================================================

    /**
     * 家园锚点记录 —— 包含位置、维度和设置时的游戏时间。
     *
     * @param pos        家园位置的方块坐标
     * @param dimension  家园所在的维度
     * @param setGameTime 设置家园时的游戏时间（Tick 数），用于计算搬迁冷却
     */
    public record HomeAnchor(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }

    /**
     * 内部能力推导结果记录。
     *
     * @param features            已解锁的功能集
     * @param radiusBlocks        行动半径（方块）
     * @param fluidCapacityBuckets 流体容量（桶）
     * @param ultimineLimit       连锁挖掘上限
     * @param bypassHomeRadius    是否可绕过家园半径限制
     */
    private record DerivedCapabilities(
            EnumSet<RtsFeature> features,
            int radiusBlocks,
            int fluidCapacityBuckets,
            int ultimineLimit,
            boolean bypassHomeRadius) {
    }

    /**
     * 节点解锁结果记录。
     *
     * <p>包含三个字段：</p>
     * <ul>
     *   <li>{@code success} —— 解锁是否成功</li>
     *   <li>{@code disabled} —— 进度系统是否被禁用
     *       （与 success 不同，禁用不是失败而是未启用）</li>
     *   <li>{@code message} —— 失败时的错误描述信息</li>
     * </ul>
     *
     * @param success  解锁是否成功
     * @param disabled 进度系统是否被禁用
     * @param message  错误消息（仅在失败时非空）
     */
    public record UnlockResult(boolean success, boolean disabled, String message) {
        /** 返回表示成功的解锁结果。 */
        private static UnlockResult ok() {
            return new UnlockResult(true, false, "");
        }

        /** 返回表示进度系统已禁用的解锁结果。 */
        private static UnlockResult disabledResult() {
            return new UnlockResult(false, true, "Survival progression is disabled.");
        }

        /**
         * 返回表示解锁失败的解锁结果。
         *
         * @param message 失败原因描述
         */
        private static UnlockResult failure(String message) {
            return new UnlockResult(false, false, message);
        }

        /**
         * 在客户端向玩家展示解锁结果通知。
         *
         * <p>仅在解锁失败且消息非空时，向玩家发送一条动作栏消息。</p>
         *
         * @param player 要通知的玩家
         */
        public void notifyPlayer(ServerPlayer player) {
            if (!success && player != null && message != null && !message.isBlank()) {
                player.displayClientMessage(Component.literal(message), true);
            }
        }
    }
}
