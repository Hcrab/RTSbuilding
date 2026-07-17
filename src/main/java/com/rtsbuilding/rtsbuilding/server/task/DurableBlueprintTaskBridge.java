package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.common.blueprint.io.VanillaStructureNbtReader;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceRuntime;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 把蓝图 command gateway 接到 durable asset/root 协议，并在精确 ACK 后才创建执行器和 Workflow。
 *
 * <p>本类拥有的 pending 仅是尚未 ACK 的冻结请求，不是第二套执行状态。ACK 后的权威状态始终是
 * {@link TaskSnapshot}；{@link TaskRecord} 只是在线执行绑定，Workflow 只是玩家可见投影。</p>
 */
public final class DurableBlueprintTaskBridge {
    private static final int MAX_COMPLETIONS_PER_TICK = 8;
    private static final int MAX_ACTIVATIONS_PER_TICK = 4;
    private static final long CHECKPOINT_INTERVAL_TICKS = 20L;
    private static final String PAYLOAD_SCHEMA = "schema";
    private static final String PAYLOAD_ASSET_ID = "asset_id";
    private static final String PAYLOAD_ANCHOR = "anchor";
    private static final String PAYLOAD_CENTER = "center";
    private static final String PAYLOAD_Y = "y_steps";
    private static final String PAYLOAD_X = "x_steps";
    private static final String PAYLOAD_Z = "z_steps";
    private static final String PAYLOAD_PREPARING = "preparing";
    private static final String PAYLOAD_REMAINING = "remaining";
    private static final String WORKFLOW_TASK_ID = "durable_task_id";

    private final RtsTaskEngine taskEngine;
    private final TaskPersistenceRuntime persistence;
    private final Map<TaskId, FrozenSubmission> pending = new LinkedHashMap<>();
    private final LinkedHashSet<TaskId> activationQueue = new LinkedHashSet<>();
    private final Map<TaskId, Long> nextActivationTick = new LinkedHashMap<>();
    private final Map<TaskId, ActiveBinding> active = new LinkedHashMap<>();

    DurableBlueprintTaskBridge(RtsTaskEngine taskEngine, TaskPersistenceRuntime persistence) {
        this.taskEngine = Objects.requireNonNull(taskEngine, "taskEngine");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    QueueResult queue(BlueprintContext context) {
        Objects.requireNonNull(context, "context");
        ServerPlayer player = context.player();
        SubmissionId submissionId = new SubmissionId(context.getSubmissionId());
        TaskId taskId = TaskId.fromSubmission(player.getUUID(), submissionId);
        ResourceKey<Level> dimension = player.level().dimension();
        FrozenSubmission frozen = FrozenSubmission.from(taskId, submissionId, context, dimension);

        FrozenSubmission existingPending = pending.get(taskId);
        if (existingPending != null) {
            if (!existingPending.sameRequest(frozen)) {
                throw new IllegalStateException("同一 submissionId 被用于不同蓝图请求: " + submissionId);
            }
            return QueueResult.ALREADY_PENDING;
        }
        TaskSnapshot existingRoot = persistence.coordinator().query().get(taskId).orElse(null);
        if (existingRoot != null) {
            requireSameIdentity(existingRoot, frozen);
            var durableBlob = persistence.loadDurableBlueprint(taskId);
            if (!durableBlob.structure().equals(frozen.structure())
                    || existingRoot.payload().getLongOr(PAYLOAD_ANCHOR, 0L) != frozen.anchor().asLong()
                    || existingRoot.payload().getLongOr(PAYLOAD_CENTER, 0L) != frozen.center().asLong()
                    || existingRoot.payload().getIntOr(PAYLOAD_Y, 0) != frozen.ySteps()
                    || existingRoot.payload().getIntOr(PAYLOAD_X, 0) != frozen.xSteps()
                    || existingRoot.payload().getIntOr(PAYLOAD_Z, 0) != frozen.zSteps()) {
                throw new IllegalStateException("同一 submissionId 已绑定不同蓝图内容");
            }
            // root ACK 可能先于 legacy heavy→thin 投影。保留冻结请求，activator 才能认领原槽而不新开槽。
            pending.put(taskId, frozen);
            activationQueue.add(taskId);
            return QueueResult.ALREADY_DURABLE;
        }
        if (persistence.coordinator().query().receipt(taskId).isPresent()) {
            return QueueResult.ALREADY_FINISHED;
        }

        TaskPersistenceRuntime.BlueprintQueueOutcome outcome = persistence.enqueueDurableBlueprint(
                frozen.initialSnapshot(), frozen.blueprint().name(), frozen.blueprint().sourceName(),
                frozen.blueprint().format().name(), frozen.structure());
        return switch (outcome) {
            case ENQUEUED -> {
                pending.put(taskId, frozen);
                yield QueueResult.ENQUEUED;
            }
            case ALREADY_PENDING -> {
                // 本桥持有所有生产请求；缺少本地冻结项说明调用方绕过了生产 gateway。
                throw new IllegalStateException("Runtime 存在未知的同 TaskId 蓝图请求: " + taskId);
            }
            case QUEUE_FULL -> QueueResult.QUEUE_FULL;
            case MEMORY_BUDGET_FULL -> QueueResult.MEMORY_BUDGET_FULL;
        };
    }

    /** 旧 heavy Workflow 的确定性迁移入口；ACK 前绝不改写原 extraData。 */
    QueueResult queueLegacy(BlueprintContext context) {
        if (!context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            throw new IllegalArgumentException("legacy 蓝图缺少 workflowEntryId");
        }
        return queue(context);
    }

    void beforeTaskTick(MinecraftServer server) {
        for (TaskPersistenceRuntime.BlueprintAdmissionCompletion completion
                : persistence.drainBlueprintAdmissionCompletions(MAX_COMPLETIONS_PER_TICK)) {
            if (completion.outcome() == TaskPersistenceRuntime.BlueprintAdmissionOutcome.ROOT_DURABLE) {
                activationQueue.add(completion.taskId());
            } else {
                FrozenSubmission failed = pending.remove(completion.taskId());
                if (failed != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(failed.ownerId());
                    if (player != null) {
                        BlueprintNetworkHandlers.send(player, S2CBlueprintStatusPayload.ERROR,
                                "screen.rtsbuilding.blueprints.status.admission_failed", "");
                    }
                }
            }
        }
        int attempts = 0;
        int inspected = 0;
        long gameTime = server.overworld().getGameTime();
        List<TaskId> retry = new ArrayList<>();
        var iterator = activationQueue.iterator();
        while (iterator.hasNext() && attempts < MAX_ACTIVATIONS_PER_TICK && inspected++ < 8) {
            TaskId taskId = iterator.next();
            iterator.remove();
            if (nextActivationTick.getOrDefault(taskId, 0L) > gameTime) {
                retry.add(taskId);
                continue;
            }
            attempts++;
            try {
                if (!activate(server, taskId)) {
                    nextActivationTick.put(taskId, gameTime + 20L);
                    retry.add(taskId);
                } else {
                    nextActivationTick.remove(taskId);
                }
            } catch (RuntimeException failure) {
                RtsbuildingMod.LOGGER.error("激活 durable 蓝图任务失败: {}", taskId, failure);
                TaskSnapshot snapshot = persistence.coordinator().query().get(taskId).orElse(null);
                if (snapshot != null && !snapshot.state().terminal()) failSnapshot(snapshot, gameTime);
                FrozenSubmission failed = pending.remove(taskId);
                UUID ownerId = failed == null && snapshot != null ? snapshot.ownerId()
                        : failed == null ? null : failed.ownerId();
                ServerPlayer player = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
                if (player != null) {
                    if (snapshot != null && snapshot.workflowEntryId() >= 0) {
                        RtsWorkflowEngine.getInstance().from(player, snapshot.workflowEntryId())
                                .ifPresent(existing -> existing.cancel());
                    }
                    BlueprintNetworkHandlers.send(player, S2CBlueprintStatusPayload.ERROR,
                            "screen.rtsbuilding.blueprints.status.restore_failed", "");
                }
                nextActivationTick.remove(taskId);
            }
        }
        activationQueue.addAll(retry);
    }

    void afterTaskTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        for (var iterator = active.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<TaskId, ActiveBinding> entry = iterator.next();
            ActiveBinding binding = entry.getValue();
            TaskRecord record = binding.record();
            TaskLifecycleState state = durableState(record.status());
            boolean terminal = state.terminal();
            boolean statusChanged = binding.snapshot().state() != state;
            boolean countersChanged = binding.snapshot().cursorUnits() != record.cursorUnits()
                    || binding.snapshot().succeededUnits() != record.succeededUnits()
                    || binding.snapshot().failedUnits() != record.failedUnits();
            boolean preparationFinished = binding.snapshot().payload().getBooleanOr(PAYLOAD_PREPARING, false)
                    && !binding.context().isPreparing();
            if (!terminal && !statusChanged && !countersChanged && !preparationFinished) {
                continue;
            }
            if (!terminal && !statusChanged && !preparationFinished
                    && gameTime - binding.lastCheckpointTick() < CHECKPOINT_INTERVAL_TICKS) {
                continue;
            }
            TaskSnapshot next = nextSnapshot(binding.snapshot(), binding.context(), record, state, gameTime);
            persistence.coordinator().replace(next);
            if (terminal) persistence.coordinator().requestTombstone(entry.getKey(), gameTime);
            binding = new ActiveBinding(binding.record(), binding.context(), next, gameTime);
            entry.setValue(binding);
            if (terminal) iterator.remove();
        }
    }

    boolean isDurableContext(BlueprintContext context) {
        return context != null && context.hasData(BlueprintContext.KEY_DURABLE_TASK_ID);
    }

    /** 登出前由 Task Engine 调用：先冻结最新进度，再释放所有 ServerPlayer/Context 强引用。 */
    void detachOwner(UUID ownerId) {
        List<TaskId> detached = new ArrayList<>();
        for (Map.Entry<TaskId, ActiveBinding> entry : active.entrySet()) {
            if (!entry.getValue().record().ownerId().equals(ownerId)) continue;
            ActiveBinding binding = entry.getValue();
            long gameTime = binding.context().player().level().getGameTime();
            TaskLifecycleState state = durableState(binding.record().status());
            TaskSnapshot next = nextSnapshot(binding.snapshot(), binding.context(), binding.record(), state, gameTime);
            persistence.coordinator().replace(next);
            if (state.terminal()) persistence.coordinator().requestTombstone(entry.getKey(), gameTime);
            detached.add(entry.getKey());
        }
        detached.forEach(active::remove);
        detached.forEach(activationQueue::add);
    }

    /** 停服 writer 冲刷前强制合并在线执行状态，不等待 20 tick 节流。 */
    void checkpointAll(long gameTime) {
        for (Map.Entry<TaskId, ActiveBinding> entry : active.entrySet()) {
            ActiveBinding binding = entry.getValue();
            TaskLifecycleState state = durableState(binding.record().status());
            TaskSnapshot next = nextSnapshot(binding.snapshot(), binding.context(), binding.record(), state, gameTime);
            persistence.coordinator().replace(next);
            if (state.terminal()) persistence.coordinator().requestTombstone(entry.getKey(), gameTime);
            entry.setValue(new ActiveBinding(binding.record(), binding.context(), next, gameTime));
        }
    }

    /** persistence.stop 成功后清除跨世界内存，禁止旧 Context 泄漏到下一世界。 */
    void resetAfterServerStop() {
        pending.clear();
        activationQueue.clear();
        nextActivationTick.clear();
        active.clear();
    }

    private boolean activate(MinecraftServer server, TaskId taskId) {
        if (active.containsKey(taskId)) return true;
        TaskSnapshot snapshot = persistence.coordinator().query().get(taskId).orElse(null);
        if (snapshot == null || snapshot.state().terminal()) {
            pending.remove(taskId);
            return true;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(snapshot.ownerId());
        if (player == null) return false;
        ResourceKey<Level> dimension = parseDimension(snapshot.dimensionId());
        if (!player.level().dimension().equals(dimension)) return false;

        BlueprintContext context;
        FrozenSubmission frozen = pending.get(taskId);
        if (frozen == null && snapshot.workflowEntryId() >= 0) {
            var claimed = RtsWorkflowEngine.getInstance()
                    .findEntryByPlayer(player, snapshot.workflowEntryId());
            if (claimed != null) {
                if (claimed.type() != RtsWorkflowType.BLUEPRINT_BUILD) {
                    throw new IllegalStateException("durable root 的 workflowEntryId 已被其他投影占用");
                }
                ProjectionClaimDecision preflight = decideProjectionClaim(
                        claimed.getExtraData(), taskId.value(), false);
                if (preflight == ProjectionClaimDecision.DEFER_UNCLAIMED_HEAVY) return false;
                if (preflight == ProjectionClaimDecision.FAIL_CONFLICT) {
                    throw new IllegalStateException("durable root 的 workflowEntryId 已绑定另一 TaskId");
                }
            }
        }
        try {
            context = frozen != null
                    ? frozen.materialize(player, snapshot)
                    : materializeFromDurableRoot(player, snapshot);
        } catch (RuntimeException corrupt) {
            RtsbuildingMod.LOGGER.error("恢复 durable 蓝图任务失败: {}", taskId, corrupt);
            failSnapshot(snapshot, player.level().getGameTime());
            BlueprintNetworkHandlers.send(player, S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.restore_failed", "");
            pending.remove(taskId);
            return true;
        }

        RtsWorkflowEngine workflowEngine = RtsWorkflowEngine.getInstance();
        var token = workflowEngine.findDurableBlueprintProjection(player, taskId.value()).orElse(null);
        if (token == null && frozen != null && frozen.preferredWorkflowEntryId() >= 0) {
            var legacyEntry = workflowEngine.findEntryByPlayer(player, frozen.preferredWorkflowEntryId());
            if (legacyEntry != null) {
                if (legacyEntry.type() != RtsWorkflowType.BLUEPRINT_BUILD) {
                    throw new IllegalStateException("legacy 蓝图槽已经被其他 Workflow 占用");
                }
                CompoundTag extra = legacyEntry.getExtraData();
                ProjectionClaimDecision decision = decideProjectionClaim(extra, taskId.value(), true);
                if (decision == ProjectionClaimDecision.FAIL_CONFLICT) {
                    throw new IllegalStateException("legacy 蓝图槽已绑定另一 durable TaskId");
                }
                token = workflowEngine.from(player, legacyEntry.id()).orElse(null);
            }
        }
        if (token == null && snapshot.workflowEntryId() >= 0) {
            var claimed = workflowEngine.findEntryByPlayer(player, snapshot.workflowEntryId());
            if (claimed != null) {
                CompoundTag extra = claimed.getExtraData();
                if (claimed.type() != RtsWorkflowType.BLUEPRINT_BUILD) {
                    throw new IllegalStateException("durable root 的 workflowEntryId 已被其他投影占用");
                }
                ProjectionClaimDecision decision = decideProjectionClaim(
                        extra, taskId.value(), frozen != null);
                if (decision == ProjectionClaimDecision.DEFER_UNCLAIMED_HEAVY) return false;
                if (decision == ProjectionClaimDecision.FAIL_CONFLICT) {
                    throw new IllegalStateException("durable root 的 workflowEntryId 已绑定另一 TaskId");
                }
                token = workflowEngine.from(player, snapshot.workflowEntryId()).orElse(null);
            }
        }
        if (token == null) {
            token = workflowEngine.start(
                    player, RtsWorkflowType.BLUEPRINT_BUILD, RtsWorkflowPriority.NORMAL, snapshot.totalUnits())
                    .orElse(null);
        }
        if (token == null) return false;
        try {
            context.setData(PipelineContext.KEY_WORKFLOW_ENTRY_ID, token.entryId());
            context.setData(BlueprintContext.KEY_DURABLE_TASK_ID, taskId.value());
            CompoundTag projection = new CompoundTag();
            com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(projection, WORKFLOW_TASK_ID, taskId.value());
            workflowEngine.setWorkflowExtraData(player, token.entryId(), projection);
            TaskRecord record = taskEngine.activateDurableBlueprint(taskId, snapshot, context, dimension);
            TaskSnapshot projected = snapshot.nextRevision(snapshot.state(), snapshot.waitKey(),
                    player.level().getGameTime(), snapshot.cursorUnits(), snapshot.succeededUnits(),
                    snapshot.failedUnits(), snapshot.payload());
            persistence.coordinator().replace(projected);
            active.put(taskId, new ActiveBinding(record, context, projected,
                    player.level().getGameTime()));
            pending.remove(taskId);
            return true;
        } catch (RuntimeException failure) {
            token.cancel();
            throw failure;
        }
    }

    private BlueprintContext materializeFromDurableRoot(ServerPlayer player, TaskSnapshot snapshot) {
        var blob = persistence.loadDurableBlueprint(snapshot.id());
        RtsBlueprint blueprint = VanillaStructureNbtReader.parse(
                blob.structure(), blob.name(), blob.sourceName(), player.registryAccess());
        if (blueprint.blockCount() != snapshot.totalUnits()) {
            throw new IllegalStateException("恢复蓝图方块数与 durable root 不一致");
        }
        return materialize(player, snapshot, blueprint);
    }

    private static BlueprintContext materialize(
            ServerPlayer player, TaskSnapshot snapshot, RtsBlueprint blueprint) {
        CompoundTag payload = snapshot.payload();
        requirePayload(payload, snapshot.id());
        BlueprintContext context = BlueprintContext.builder(player)
                .submissionId(snapshot.submissionId().value())
                .blueprint(blueprint)
                .anchor(BlockPos.of(payload.getLongOr(PAYLOAD_ANCHOR, 0L)))
                .yRotationSteps(payload.getIntOr(PAYLOAD_Y, 0))
                .xRotationSteps(payload.getIntOr(PAYLOAD_X, 0))
                .zRotationSteps(payload.getIntOr(PAYLOAD_Z, 0))
                .totalBlocks(snapshot.totalUnits())
                .build();
        context.setData(BlueprintContext.KEY_CENTER_OFFSET, BlockPos.of(payload.getLongOr(PAYLOAD_CENTER, 0L)));
        context.setData(BlueprintContext.KEY_SOURCE_DIMENSION, parseDimension(snapshot.dimensionId()));
        context.setData(SessionValidatePipe.KEY_SESSION,
                ServiceRegistry.getInstance().session().getOrCreate(player));
        context.setPlacedCount(snapshot.succeededUnits());
        context.setSkippedBlocked(snapshot.failedUnits());
        boolean preparing = payload.getBooleanOr(PAYLOAD_PREPARING, false);
        context.setPreparing(preparing);
        if (!preparing) {
            LinkedList<Integer> remaining = new LinkedList<>();
            for (int index : payload.getIntArray(PAYLOAD_REMAINING).orElseGet(() -> new int[0])) remaining.add(index);
            context.setRemainingQueue(remaining);
        }
        return context;
    }

    private void failSnapshot(TaskSnapshot snapshot, long gameTime) {
        if (snapshot.state().terminal()) return;
        persistence.coordinator().replace(snapshot.nextRevision(
                TaskLifecycleState.FAILED, null, gameTime,
                snapshot.cursorUnits(), snapshot.succeededUnits(), snapshot.failedUnits(), snapshot.payload()));
        persistence.coordinator().requestTombstone(snapshot.id(), gameTime);
    }

    private static TaskSnapshot nextSnapshot(TaskSnapshot before, BlueprintContext context,
            TaskRecord record, TaskLifecycleState state, long gameTime) {
        TaskWaitKey waitKey = state == TaskLifecycleState.WAITING_RESOURCE
                ? new TaskWaitKey("blueprint_material", "any") : null;
        return before.nextRevision(state, waitKey, gameTime,
                record.cursorUnits(), record.succeededUnits(), record.failedUnits(),
                runtimePayload(before.payload(), context));
    }

    private static CompoundTag runtimePayload(CompoundTag base, BlueprintContext context) {
        CompoundTag payload = base.copy();
        payload.putBoolean(PAYLOAD_PREPARING, context.isPreparing());
        LinkedList<Integer> remaining = context.getRemainingQueue();
        if (remaining == null || remaining.isEmpty()) {
            payload.putIntArray(PAYLOAD_REMAINING, new int[0]);
        } else {
            int[] indices = new int[remaining.size()];
            int cursor = 0;
            for (int index : remaining) indices[cursor++] = index;
            payload.putIntArray(PAYLOAD_REMAINING, indices);
        }
        return payload;
    }

    private static TaskLifecycleState durableState(TaskStatus status) {
        return switch (status) {
            case QUEUED -> TaskLifecycleState.QUEUED;
            case RUNNING -> TaskLifecycleState.RUNNING;
            case PAUSED -> TaskLifecycleState.PAUSED;
            case WAITING_RESOURCE -> TaskLifecycleState.WAITING_RESOURCE;
            case COMPLETED -> TaskLifecycleState.COMPLETED;
            case FAILED -> TaskLifecycleState.FAILED;
            case CANCELLED -> TaskLifecycleState.CANCELLED;
        };
    }

    static TaskStatus runtimeState(TaskLifecycleState state) {
        return switch (state) {
            case QUEUED, RUNNING, WAITING_PERSISTENCE, WAITING_CHUNK -> TaskStatus.QUEUED;
            case PAUSED -> TaskStatus.PAUSED;
            case WAITING_RESOURCE -> TaskStatus.WAITING_RESOURCE;
            case COMPLETED -> TaskStatus.COMPLETED;
            case FAILED -> TaskStatus.FAILED;
            case CANCELLED -> TaskStatus.CANCELLED;
        };
    }

    /** 将“先 recovery completion、后 legacy restore”的顺序反转压缩成可执行纯状态门。 */
    static ProjectionClaimDecision decideProjectionClaim(
            CompoundTag extra, UUID taskId, boolean hasFrozenLegacyRequest) {
        if (extra == null || !com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(extra, WORKFLOW_TASK_ID)) {
            return hasFrozenLegacyRequest
                    ? ProjectionClaimDecision.CLAIM_HEAVY
                    : ProjectionClaimDecision.DEFER_UNCLAIMED_HEAVY;
        }
        return taskId.equals(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(extra, WORKFLOW_TASK_ID))
                ? ProjectionClaimDecision.REUSE_MATCHING_THIN
                : ProjectionClaimDecision.FAIL_CONFLICT;
    }

    enum ProjectionClaimDecision {
        CLAIM_HEAVY,
        DEFER_UNCLAIMED_HEAVY,
        REUSE_MATCHING_THIN,
        FAIL_CONFLICT
    }

    static InitialProgress initialProgress(
            boolean preparing, int total, int remaining, int succeeded, int failed) {
        int boundedSucceeded = Math.max(0, succeeded);
        int boundedFailed = Math.max(0, failed);
        int cursor = preparing ? 0 : Math.max(
                boundedSucceeded + boundedFailed, total - Math.max(0, remaining));
        cursor = Math.max(0, Math.min(total, cursor));
        boundedSucceeded = Math.min(cursor, boundedSucceeded);
        boundedFailed = Math.min(cursor - boundedSucceeded, boundedFailed);
        return new InitialProgress(cursor, boundedSucceeded, boundedFailed);
    }

    record InitialProgress(int cursor, int succeeded, int failed) {
    }

    private static ResourceKey<Level> parseDimension(String dimensionId) {
        Identifier parsed = Identifier.tryParse(dimensionId);
        if (parsed == null) throw new IllegalStateException("durable 蓝图维度无效: " + dimensionId);
        return ResourceKey.create(Registries.DIMENSION, parsed);
    }

    private static void requirePayload(CompoundTag payload, TaskId taskId) {
        if (payload.getIntOr(PAYLOAD_SCHEMA, 0) != 1
                || !com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(payload, PAYLOAD_ASSET_ID)
                || !payload.contains(PAYLOAD_ANCHOR)
                || !payload.contains(PAYLOAD_CENTER)) {
            throw new IllegalStateException("durable 蓝图 payload 缺失或 schema 不兼容");
        }
        UUID expected = TaskAssetId.forTask(taskId, "blueprint").value();
        if (!expected.equals(com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(payload, PAYLOAD_ASSET_ID))) {
            throw new IllegalStateException("durable 蓝图 asset_id 与 TaskId 不一致");
        }
    }

    private static void requireSameIdentity(TaskSnapshot root, FrozenSubmission request) {
        if (!root.ownerId().equals(request.ownerId())
                || !root.submissionId().equals(request.submissionId())
                || !root.dimensionId().equals(request.dimension().identifier().toString())
                || root.type() != TaskType.BLUEPRINT) {
            throw new IllegalStateException("稳定 TaskId 已绑定到另一 durable root");
        }
    }

    public enum QueueResult {
        ENQUEUED,
        ALREADY_PENDING,
        ALREADY_DURABLE,
        ALREADY_FINISHED,
        QUEUE_FULL,
        MEMORY_BUDGET_FULL
    }

    private record ActiveBinding(TaskRecord record, BlueprintContext context,
            TaskSnapshot snapshot, long lastCheckpointTick) {
    }

    private record FrozenSubmission(TaskId taskId, SubmissionId submissionId, UUID ownerId,
            ResourceKey<Level> dimension, RtsBlueprint blueprint, BlockPos anchor,
            BlockPos center, int ySteps, int xSteps, int zSteps,
            int preferredWorkflowEntryId, CompoundTag structure, TaskSnapshot initialSnapshot) {
        static FrozenSubmission from(TaskId taskId, SubmissionId submissionId,
                BlueprintContext context, ResourceKey<Level> dimension) {
            RtsBlueprint blueprint = context.getBlueprint();
            CompoundTag structure = BlueprintWriters.toVanillaStructureTag(blueprint);
            CompoundTag payload = new CompoundTag();
            payload.putInt(PAYLOAD_SCHEMA, 1);
            com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(payload, PAYLOAD_ASSET_ID, TaskAssetId.forTask(taskId, "blueprint").value());
            payload.putLong(PAYLOAD_ANCHOR, context.getAnchor().asLong());
            BlockPos center = context.getData(BlueprintContext.KEY_CENTER_OFFSET);
            payload.putLong(PAYLOAD_CENTER, center.asLong());
            payload.putInt(PAYLOAD_Y, context.getYRotationSteps());
            payload.putInt(PAYLOAD_X, context.getXRotationSteps());
            payload.putInt(PAYLOAD_Z, context.getZRotationSteps());
            payload.putBoolean(PAYLOAD_PREPARING, context.isPreparing());
            LinkedList<Integer> remaining = context.getRemainingQueue();
            int[] remainingIndices = remaining == null ? new int[0] : remaining.stream()
                    .mapToInt(Integer::intValue).toArray();
            payload.putIntArray(PAYLOAD_REMAINING, remainingIndices);
            long gameTime = context.player().level().getGameTime();
            Integer preferred = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            int preferredWorkflowEntryId = preferred == null ? -1 : preferred;
            TaskLifecycleState initialState = TaskLifecycleState.QUEUED;
            TaskWaitKey waitKey = null;
            if (preferredWorkflowEntryId >= 0) {
                var legacyEntry = RtsWorkflowEngine.getInstance()
                        .findEntryByPlayer(context.player(), preferredWorkflowEntryId);
                if (legacyEntry != null && legacyEntry.paused()) initialState = TaskLifecycleState.PAUSED;
                else if (legacyEntry != null && legacyEntry.suspended()) {
                    initialState = TaskLifecycleState.WAITING_RESOURCE;
                    waitKey = new TaskWaitKey("blueprint_material", "any");
                }
            }
            int succeeded = Math.max(0, context.getPlacedCount());
            int failed = Math.max(0, context.getSkippedBlocked()
                    + context.getSkippedMissingBlocks() + context.getSkippedUnsupported());
            InitialProgress progress = initialProgress(
                    context.isPreparing(), blueprint.blockCount(), remainingIndices.length, succeeded, failed);
            TaskSnapshot snapshot = new TaskSnapshot(
                    taskId, submissionId, context.player().getUUID(), dimension.identifier().toString(),
                    TaskType.BLUEPRINT, initialState, preferredWorkflowEntryId, waitKey, 1L,
                    gameTime, gameTime, blueprint.blockCount(), progress.cursor(),
                    progress.succeeded(), progress.failed(), payload);
            return new FrozenSubmission(taskId, submissionId, context.player().getUUID(), dimension,
                    blueprint, context.getAnchor(), center, context.getYRotationSteps(),
                    context.getXRotationSteps(), context.getZRotationSteps(), preferredWorkflowEntryId,
                    structure, snapshot);
        }

        FrozenSubmission {
            structure = structure.copy();
        }

        @Override
        public CompoundTag structure() {
            return structure.copy();
        }

        boolean sameRequest(FrozenSubmission other) {
            return ownerId.equals(other.ownerId) && dimension.equals(other.dimension)
                    && structure.equals(other.structure) && anchor.equals(other.anchor)
                    && center.equals(other.center) && ySteps == other.ySteps
                    && xSteps == other.xSteps && zSteps == other.zSteps
                    && preferredWorkflowEntryId == other.preferredWorkflowEntryId;
        }

        BlueprintContext materialize(ServerPlayer player, TaskSnapshot snapshot) {
            return DurableBlueprintTaskBridge.materialize(player, snapshot, blueprint);
        }
    }
}
