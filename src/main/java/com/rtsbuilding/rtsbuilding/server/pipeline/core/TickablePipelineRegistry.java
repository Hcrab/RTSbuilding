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
        INSTANCE.activePipelines.remove(playerId);
    }

    /**
     * 移除给定玩家在特定维度中的所有活跃管道。
     * 在玩家离开维度时调用。
     *
     * @param playerId  玩家的 UUID
     * @param dimension 要清理的维度
     */
    public static void removeAll(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, List<ActivePipeline>> dimMap = INSTANCE.activePipelines.get(playerId);
        if (dimMap != null) {
            dimMap.remove(dimension);
            if (dimMap.isEmpty()) {
                INSTANCE.activePipelines.remove(playerId);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Tick
    // ──────────────────────────────────────────────────────────────────

    /**
     * 查找指定工作流条目 ID 对应的活跃管道上下文。
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
        Map<ResourceKey<Level>, List<ActivePipeline>> dimMap = activePipelines.get(player.getUUID());
        if (dimMap == null) return null;
        for (List<ActivePipeline> pipelines : dimMap.values()) {
            for (ActivePipeline ap : pipelines) {
                PipelineContext ctx = ap.context();
                Integer eid = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
                if (eid != null && eid == workflowEntryId) {
                    return ctx;
                }
            }
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
        list.add(new ActivePipeline(player, ctx, pipe));
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

                // 仅 Tick 玩家当前维度的管道。
                // 来自其他维度的管道保持注册但被跳过——
                // 它们将在玩家返回时被 Tick，或在超时/维度变化时被清理。
                if (pipelines.isEmpty()) {
                    dimIt.remove();
                    continue;
                }

                // 检查此维度列表中的任何管道是否与
                // 玩家当前维度匹配（同一维度列表中的所有管道
                // 共享相同的维度键）。
                ActivePipeline first = pipelines.getFirst();
                ResourceKey<Level> playerCurrentDim = first.player().level().dimension();
                if (!pipelineDim.equals(playerCurrentDim)) {
                    continue; // skip this dimension entirely
                }

                pipelines.removeIf(ap -> {
                    if (ap.context().hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
                        int eid = Objects.requireNonNull(
                                ap.context().getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID));
                        var engine = RtsWorkflowEngine.getInstance();
                        var tokenOpt = engine.from(ap.player(), eid);
                        // 工作流已被取消或完成 → 移除管道
                        if (tokenOpt.isEmpty()) return true;
                        // 逐个条目的暂停阀：如果工作流条目已暂停则跳过此管道
                        if (tokenOpt.get().isPaused()) return false;
                        // 逐个条目的挂起阀：如果工作流条目已挂起（等待物品）则跳过此管道
                        if (engine.isEntrySuspended(ap.player().getUUID(), ap.player().level().dimension(), eid)) return false;
                    }

                    return ap.tick().isPresent();
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
