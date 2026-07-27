package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃（正在 Tick）管道实例的线程安全注册表。
 *
 * <p>当具有可 Tick Pipe 的 {@link WorkflowPipeline} 的同步阶段成功完成后，
 * 管道执行会在此注册。服务器 Tick 循环调用 {@link #tickAll()}
 * 来推进所有活跃管道一个 Tick。</p>
 *
 * <p>当可 Tick Pipe 发出完成信号（正常或错误）时，
 * 活跃管道会自动移除。玩家退出时也会进行清理。</p>
 *
 * <p>这是一个单例——通过 {@link #getInstance()} 获取实例。</p>
 */
public final class TickablePipelineRegistry {

    private static final TickablePipelineRegistry INSTANCE = new TickablePipelineRegistry();

    /** 每位玩家、每个维度的活跃可 Tick 管道列表。 */
    private final Map<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>> activePipelines = new ConcurrentHashMap<>();

    /**
     * 工作流条目 ID → ActivePipeline 的 O(1) 索引。
     * 用于 {@link #doFindContext} 的快速查找，消除线性扫描。
     * 在管道注册/完成/移除时同步更新。
     */
    private final Map<Integer, ActivePipeline> entryIdIndex = new ConcurrentHashMap<>();

    private TickablePipelineRegistry() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  单例
    // ──────────────────────────────────────────────────────────────────

    /** 返回单例注册表实例。 */
    public static TickablePipelineRegistry getInstance() {
        return INSTANCE;
    }

    // ──────────────────────────────────────────────────────────────────
    //  注册
    // ──────────────────────────────────────────────────────────────────

    /**
     * 注册一个用于逐 Tick 执行的可 Tick Pipe。
     *
     * @param player 服务器端玩家
     * @param ctx    管道上下文（共享数据中必须包含工作流条目 ID）
     * @param pipe   每个服务器 Tick 调用的可 Tick Pipe
     */
    public static void register(ServerPlayer player, PipelineContext ctx, TickablePipe pipe) {
        INSTANCE.doRegister(player, ctx, pipe);
    }

    /**
     * 移除给定玩家在所有维度中的所有活跃管道。
     * 在玩家退出时调用。
     *
     * @param playerId 玩家的 UUID
     */
    public static void removeAll(UUID playerId) {
        INSTANCE.doRemoveAllForPlayer(playerId);
    }

    /**
     * 移除给定玩家在特定维度中的所有活跃管道。
     * 在玩家离开维度时调用。
     *
     * @param playerId  玩家的 UUID
     * @param dimension 要清理的维度
     */
    public static void removeAll(UUID playerId, ResourceKey<Level> dimension) {
        INSTANCE.doRemoveAllForDimension(playerId, dimension);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Tick
    // ──────────────────────────────────────────────────────────────────

    /**
     * 查找指定工作流条目 ID 对应的活跃管道上下文。
     * 使用 entryIdIndex 实现 O(1) 查找，替代原有的线性扫描。
     *
     * @param player         服务器端玩家
     * @param workflowEntryId 目标工作流条目 ID
     * @return 匹配的管道上下文，未找到则返回 null
     */
    @javax.annotation.Nullable
    public static PipelineContext findContextByWorkflowEntry(ServerPlayer player, int workflowEntryId) {
        return INSTANCE.doFindContext(player, workflowEntryId);
    }

    @javax.annotation.Nullable
    private PipelineContext doFindContext(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;
        ActivePipeline ap = entryIdIndex.get(workflowEntryId);
        if (ap != null && ap.player().getUUID().equals(player.getUUID())) {
            return ap.context();
        }
        return null;
    }

    /**
     * 将所有活跃管道实例 Tick 一次。完成/失败的实例
     * 会自动移除。
     *
     * <p>在服务器 Tick 事件处理程序中调用，
     * 在挖掘状态机已经 Tick 之后。</p>
     */
    public static void tickAll() {
        INSTANCE.doTickAll();
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    private void doRegister(ServerPlayer player, PipelineContext ctx, TickablePipe pipe) {
        ResourceKey<Level> dimension = player.level().dimension();
        Map<ResourceKey<Level>, List<ActivePipeline>> dimMap = activePipelines.computeIfAbsent(
                player.getUUID(), k -> new ConcurrentHashMap<>());
        List<ActivePipeline> list = dimMap.computeIfAbsent(dimension, k -> new ArrayList<>());
        ActivePipeline ap = new ActivePipeline(player, ctx, pipe);
        list.add(ap);
        // 同步更新 entryIdIndex
        if (ap.entryId() >= 0) {
            entryIdIndex.put(ap.entryId(), ap);
        }
    }

    private void doRemoveAllForPlayer(UUID playerId) {
        Map<ResourceKey<Level>, List<ActivePipeline>> dimMap = activePipelines.get(playerId);
        if (dimMap != null) {
            for (List<ActivePipeline> pipelines : dimMap.values()) {
                for (ActivePipeline ap : pipelines) {
                    if (ap.entryId() >= 0) {
                        entryIdIndex.remove(ap.entryId());
                    }
                }
            }
        }
        activePipelines.remove(playerId);
    }

    private void doRemoveAllForDimension(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, List<ActivePipeline>> dimMap = activePipelines.get(playerId);
        if (dimMap != null) {
            List<ActivePipeline> pipelines = dimMap.remove(dimension);
            if (pipelines != null) {
                for (ActivePipeline ap : pipelines) {
                    if (ap.entryId() >= 0) {
                        entryIdIndex.remove(ap.entryId());
                    }
                }
            }
            if (dimMap.isEmpty()) {
                activePipelines.remove(playerId);
            }
        }
    }

    /** 从 entryIdIndex 中移除指定条目 ID（由 doTickAll 在管道完成/移除时调用）。 */
    private void removeFromIndex(int entryId) {
        if (entryId >= 0) {
            entryIdIndex.remove(entryId);
        }
    }

    private void doTickAll() {
        if (activePipelines.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>>> playerIt =
                activePipelines.entrySet().iterator();

        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>> playerEntry = playerIt.next();
            UUID playerId = playerEntry.getKey();
            Map<ResourceKey<Level>, List<ActivePipeline>> dimPipelines = playerEntry.getValue();

            Iterator<Map.Entry<ResourceKey<Level>, List<ActivePipeline>>> dimIt =
                    dimPipelines.entrySet().iterator();

            while (dimIt.hasNext()) {
                Map.Entry<ResourceKey<Level>, List<ActivePipeline>> dimEntry = dimIt.next();
                ResourceKey<Level> pipelineDim = dimEntry.getKey();
                List<ActivePipeline> pipelines = dimEntry.getValue();

                if (pipelines.isEmpty()) {
                    dimIt.remove();
                    continue;
                }

                // 仅 Tick 玩家当前维度的管道
                ActivePipeline first = pipelines.getFirst();
                ResourceKey<Level> playerCurrentDim = first.player().level().dimension();
                if (!pipelineDim.equals(playerCurrentDim)) {
                    continue;
                }

                // 使用缓存的 entryId 和单次引擎查询替代原来的 3 次独立 lookup
                var engine = RtsWorkflowEngine.getInstance();
                ResourceKey<Level> playerDim = first.player().level().dimension();
                UUID firstPlayerId = first.player().getUUID();
                pipelines.removeIf(ap -> {
                    int eid = ap.entryId();
                    if (eid >= 0) {
                        // 单次 lookup：同时获取 paused + suspended 状态
                        // 使用已缓存的 dimension 避免重复 player.level().dimension() 调用
                        var entry = engine.findEntryByPlayer(firstPlayerId, playerDim, eid);
                        if (entry == null) {
                            // 工作流已被取消或完成 → 移除管道
                            removeFromIndex(eid);
                            return true;
                        }
                        if (entry.paused() || entry.suspended()) {
                            // 暂停或挂起 → 跳过此 tick
                            return false;
                        }
                    }

                    boolean done = ap.tick().isPresent();
                    if (done && eid >= 0) {
                        removeFromIndex(eid);
                    }
                    return done;
                });

                if (pipelines.isEmpty()) {
                    dimIt.remove();
                }
            }

            if (dimPipelines.isEmpty()) {
                playerIt.remove();
            }
        }
    }
}
