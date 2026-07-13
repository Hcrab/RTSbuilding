package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃 tickable pipeline 的注册表。
 *
 * <p>Forge 当前业务还没有迁进 pipeline；这个注册表先提供和 main 一致的
 * 生命周期入口，后续迁移放置/挖掘/蓝图 pipe 时可以直接挂进来。</p>
 */
public final class TickablePipelineRegistry {
    private static final TickablePipelineRegistry INSTANCE = new TickablePipelineRegistry();

    private final Map<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>> activePipelines =
            new ConcurrentHashMap<>();
    private final Map<PipelineKey, ActivePipeline> entryIdIndex = new ConcurrentHashMap<>();

    /** 工作流条目只在玩家自己的维度空间内唯一，不能只用整数 ID 建全局索引。 */
    private record PipelineKey(UUID playerId, ResourceKey<Level> dimension, int entryId) {
    }

    private TickablePipelineRegistry() {
    }

    public static void register(ServerPlayer player, PipelineContext context, TickablePipe pipe) {
        INSTANCE.doRegister(player, context, pipe);
    }

    public static void removeAll(UUID playerId) {
        INSTANCE.doRemoveAllForPlayer(playerId);
    }

    public static void removeAll(UUID playerId, ResourceKey<Level> dimension) {
        INSTANCE.doRemoveAllForDimension(playerId, dimension);
    }

    @Nullable
    public static PipelineContext findContextByWorkflowEntry(ServerPlayer player, int workflowEntryId) {
        return INSTANCE.doFindContext(player, workflowEntryId);
    }

    public static void tickAll() {
        INSTANCE.doTickAll();
    }

    public static void clearAll() {
        INSTANCE.activePipelines.clear();
        INSTANCE.entryIdIndex.clear();
    }

    private void doRegister(ServerPlayer player, PipelineContext context, TickablePipe pipe) {
        if (player == null || context == null || pipe == null) {
            return;
        }
        ActivePipeline active = new ActivePipeline(player, context, pipe);
        this.activePipelines
                .computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.level().dimension(), ignored -> new ArrayList<>())
                .add(active);
        if (active.entryId() >= 0) {
            this.entryIdIndex.put(new PipelineKey(player.getUUID(), active.dimension(), active.entryId()), active);
        }
    }

    private void doRemoveAllForPlayer(UUID playerId) {
        Map<ResourceKey<Level>, List<ActivePipeline>> dimensions = this.activePipelines.remove(playerId);
        if (dimensions != null) {
            dimensions.values().forEach(this::removeFromIndex);
        }
    }

    private void doRemoveAllForDimension(UUID playerId, ResourceKey<Level> dimension) {
        Map<ResourceKey<Level>, List<ActivePipeline>> dimensions = this.activePipelines.get(playerId);
        if (dimensions == null) {
            return;
        }
        List<ActivePipeline> removed = dimensions.remove(dimension);
        if (removed != null) {
            removeFromIndex(removed);
        }
        if (dimensions.isEmpty()) {
            this.activePipelines.remove(playerId);
        }
    }

    @Nullable
    private PipelineContext doFindContext(ServerPlayer player, int workflowEntryId) {
        if (player == null) {
            return null;
        }
        ActivePipeline active = this.entryIdIndex.get(new PipelineKey(
                player.getUUID(), player.level().dimension(), workflowEntryId));
        return active == null ? null : active.context();
    }

    private void doTickAll() {
        if (this.activePipelines.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>>> playerIt =
                this.activePipelines.entrySet().iterator();
        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<ResourceKey<Level>, List<ActivePipeline>>> playerEntry = playerIt.next();
            Iterator<Map.Entry<ResourceKey<Level>, List<ActivePipeline>>> dimensionIt =
                    playerEntry.getValue().entrySet().iterator();
            while (dimensionIt.hasNext()) {
                Map.Entry<ResourceKey<Level>, List<ActivePipeline>> dimensionEntry = dimensionIt.next();
                List<ActivePipeline> pipelines = dimensionEntry.getValue();
                if (!pipelines.isEmpty()
                        && !dimensionEntry.getKey().equals(pipelines.get(0).player().level().dimension())) {
                    continue;
                }
                pipelines.removeIf(this::shouldRemoveAfterTick);
                if (pipelines.isEmpty()) {
                    dimensionIt.remove();
                }
            }
            if (playerEntry.getValue().isEmpty()) {
                playerIt.remove();
            }
        }
    }

    private boolean shouldRemoveAfterTick(ActivePipeline active) {
        int entryId = active.entryId();
        if (entryId >= 0) {
            var entry = RtsWorkflowEngine.getInstance()
                    .findEntryByPlayer(active.player().getUUID(), active.dimension(), entryId);
            if (entry == null) {
                removeFromIndex(active);
                return true;
            }
            if (entry.paused() || entry.suspended()) {
                return false;
            }
        }
        boolean done = active.tick().isPresent();
        if (done && entryId >= 0) {
            removeFromIndex(active);
        }
        return done;
    }

    private void removeFromIndex(List<ActivePipeline> pipelines) {
        for (ActivePipeline active : pipelines) {
            if (active.entryId() >= 0) {
                removeFromIndex(active);
            }
        }
    }

    private void removeFromIndex(ActivePipeline active) {
        if (active.entryId() < 0) {
            return;
        }
        this.entryIdIndex.remove(new PipelineKey(
                active.player().getUUID(), active.dimension(), active.entryId()), active);
    }
}
