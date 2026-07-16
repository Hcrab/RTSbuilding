package com.rtsbuilding.rtsbuilding.server.service.buffer;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.BufferDrainTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferDrainSliceResult;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferEscrowEntry;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferEscrowPhase;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferEscrowState;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferRecoveryCode;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferSourceClaimPlanner;
import com.rtsbuilding.rtsbuilding.server.task.buffer.LegacyBufferHandoffState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Buffer escrow 的主线程运行时适配器。
 *
 * <p>本类只负责在单个有界 slice 内解析世界实体、玩家 Session 和 AE/RS handler；它不保存跨 tick
 * 状态，也不决定 durable revision 是否已经 ACK。阶段选择必须先经过 BufferEscrowDurabilityGate。</p>
 */
public final class RtsBufferEscrowExecutor {
    private RtsBufferEscrowExecutor() {
    }

    public static BufferDrainTaskPayload prepareEscrowTask(
            ServerPlayer player, List<BlockPos> positions, UUID escrowId) {
        return prepareEscrowTask(player, positions, escrowId, Set.of());
    }

    /**
     * 为新 escrow 冻结尚未被其它 durable task 声明的世界实体。
     * excludedSourceIds 来自 Task Engine 的进程内来源租约索引；过滤发生在 root admission 前。
     */
    public static BufferDrainTaskPayload prepareEscrowTask(
            ServerPlayer player, List<BlockPos> positions, UUID escrowId,
            Set<UUID> excludedSourceIds) {
        if (player == null || positions == null || escrowId == null) {
            throw new IllegalArgumentException("player、positions 与 escrowId 不能为空");
        }
        if (excludedSourceIds == null) throw new IllegalArgumentException("excludedSourceIds 不能为空");
        List<ItemEntity> drops = new ArrayList<>(collectDrops(player, positions));
        // 同一个 escrowId 重试 admission 时必须生成字节级相同的 claim 顺序。
        drops.sort(Comparator.comparing(ItemEntity::getUUID));
        List<BufferEscrowEntry> entries = new ArrayList<>();
        int items = 0;
        int ordinal = 0;
        for (ItemEntity entity : drops) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) continue;
            if (excludedSourceIds.contains(entity.getUUID())) continue;
            ItemStack source = entity.getItem().copy();
            if (entries.size() >= BufferEscrowState.MAX_STACKS
                    || items + source.getCount() > BufferEscrowState.MAX_BUFFERED_ITEMS) break;
            UUID claimId = deterministicClaimId(escrowId, entity.getUUID(), ordinal);
            entries.add(BufferEscrowEntry.prepared(claimId, ordinal, entity.getUUID(), source));
            items += source.getCount();
            ordinal++;
        }
        BufferEscrowState state = entries.isEmpty()
                ? BufferEscrowState.empty()
                : new BufferEscrowState(player.serverLevel().getGameTime(), entries);
        return new BufferDrainTaskPayload(
                player.getUUID(), player.level().dimension(), escrowId, null, state);
    }

    public static BufferDrainSliceResult claimPreparedEscrowSlice(
            ServerPlayer player,
            BufferDrainTaskPayload payload,
            int maxStacks,
            long deadlineNanos) {
        validateExecutionIdentity(player, payload);
        if (maxStacks <= 0 || payload.state().isEmpty()) return idleResult(payload.state());
        BufferEscrowState state = payload.state();
        Map<UUID, ItemEntity> liveEntities = new LinkedHashMap<>();
        Map<UUID, ItemStack> observedSources = new LinkedHashMap<>();
        for (BufferEscrowEntry entry : payload.state().entries()) {
            if (entry.phase() != BufferEscrowPhase.SOURCE_PREPARED) continue;
            var entity = player.serverLevel().getEntity(entry.sourceEntityId());
            if (entity instanceof ItemEntity itemEntity
                    && itemEntity.isAlive() && !itemEntity.getItem().isEmpty()) {
                liveEntities.put(entry.sourceEntityId(), itemEntity);
                observedSources.put(entry.sourceEntityId(), itemEntity.getItem().copy());
            }
        }
        BufferSourceClaimPlanner.Plan plan = BufferSourceClaimPlanner.plan(
                payload.state().entries(), observedSources, maxStacks);
        for (UUID sourceId : plan.sourceEntityIdsToDiscard()) {
            ItemEntity entity = liveEntities.get(sourceId);
            if (entity != null && entity.isAlive()) entity.discard();
        }
        for (UUID claimId : plan.claimedClaimIds()) state = state.sourceClaimed(claimId);
        for (UUID claimId : plan.recoveryClaimIds()) {
            state = state.recoveryRequired(claimId, BufferRecoveryCode.SOURCE_MISSING_OR_CHANGED);
        }
        int processed = plan.processedStacks();
        BufferDrainSliceResult.Outcome outcome = state.requiresRecovery()
                ? BufferDrainSliceResult.Outcome.RECOVERY_REQUIRED
                : BufferDrainSliceResult.Outcome.CONTINUE;
        return new BufferDrainSliceResult(state, processed, 0, 0, false, outcome);
    }

    public static BufferDrainSliceResult executeReservedDrainSlice(
            ServerPlayer player,
            RtsStorageSession transientSession,
            BufferDrainTaskPayload payload,
            int maxStacks,
            long deadlineNanos) {
        validateExecutionIdentity(player, payload);
        if (transientSession == null) throw new IllegalArgumentException("transientSession 不能为空");
        BufferEscrowState state = payload.state();
        if (state.requiresRecovery()) {
            return new BufferDrainSliceResult(state, 0, 0, 0, false,
                    BufferDrainSliceResult.Outcome.RECOVERY_REQUIRED);
        }
        if (maxStacks <= 0 || state.isEmpty()) return idleResult(state);

        boolean timeout = state.firstQueuedGameTime() >= 0L
                && player.serverLevel().getGameTime() - state.firstQueuedGameTime() >= 60L;
        DropInsertContext insertContext = timeout ? null : createInsertContext(player, transientSession);
        int processed = 0;
        int storedItems = 0;
        int fallbackItems = 0;
        boolean storageChanged = false;
        Set<String> changedItemIds = new LinkedHashSet<>();
        for (BufferEscrowEntry entry : payload.state().entries()) {
            if (entry.phase() != BufferEscrowPhase.DRAIN_RESERVED) continue;
            if (processed >= maxStacks || System.nanoTime() >= deadlineNanos) break;
            ItemStack original = entry.ownedStack();
            ItemStack remainder;
            if (timeout) {
                remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, original.copy());
                fallbackItems += original.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) {
                    fallbackItems += remainder.getCount();
                    player.drop(remainder.copy(), false);
                }
                remainder = ItemStack.EMPTY;
            } else {
                remainder = insertContext.store(original.copy());
                int stored = original.getCount() - remainder.getCount();
                storedItems += stored;
                storageChanged |= stored > 0;
                if (stored > 0) {
                    var itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
                    if (itemId != null) changedItemIds.add(itemId.toString());
                }
            }
            state = state.drainApplied(entry.claimId(), remainder);
            processed++;
            if (!timeout && remainder.getCount() == original.getCount()) break;
        }

        if (insertContext != null && storageChanged && insertContext.usesAggregate()) {
            RtsStorageTickService.INSTANCE.alert(player.getUUID());
        }
        if (storageChanged) {
            QuestService.runQuestDetect(player, transientSession, false);
            RtsPendingPlacementService.tryResumeAfterStorageChange(player, changedItemIds);
        }
        if (timeout && processed > 0) {
            RtsDeveloperMetrics.recordBufferFallback(player);
            player.displayClientMessage(Component.translatable("message.rtsbuilding.drop_buffer.fallback"), false);
        }
        BufferDrainSliceResult.Outcome outcome = processed > 0
                ? BufferDrainSliceResult.Outcome.WAITING_APPLIED_ACK
                : idleOutcome(state);
        return new BufferDrainSliceResult(
                state, processed, storedItems, fallbackItems, storageChanged, outcome);
    }

    public static BufferDrainTaskPayload snapshotLegacyBuffer(
            ServerPlayer player, RtsStorageSession session, LegacyBufferHandoffState handoff) {
        if (player == null || session == null || handoff == null) {
            throw new IllegalArgumentException("legacy buffer 迁移参数不能为空");
        }
        if (!handoff.matchesSource(player.registryAccess(), List.copyOf(session.miningDropBuffer.stacks))) {
            throw new IllegalStateException("legacy buffer handoff 与 Session shadow 不一致");
        }
        UUID escrowId = handoff.migrationIdentity().value();
        List<BufferEscrowEntry> entries = new ArrayList<>();
        int ordinal = 0;
        int items = 0;
        for (ItemStack stack : session.miningDropBuffer.stacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (entries.size() >= BufferEscrowState.MAX_STACKS
                    || items + stack.getCount() > BufferEscrowState.MAX_BUFFERED_ITEMS) break;
            UUID claimId = deterministicClaimId(escrowId, player.getUUID(), ordinal);
            entries.add(BufferEscrowEntry.alreadyEscrowed(claimId, ordinal, stack));
            items += stack.getCount();
            ordinal++;
        }
        long queuedAt = entries.isEmpty()
                ? -1L
                : Math.max(0L, session.miningDropBuffer.firstQueuedGameTime);
        return new BufferDrainTaskPayload(
                player.getUUID(), player.level().dimension(), escrowId,
                handoff.sourceFingerprint(),
                new BufferEscrowState(queuedAt, entries));
    }

    private static List<ItemEntity> collectDrops(ServerPlayer player, List<BlockPos> positions) {
        Set<ItemEntity> uniqueDrops = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            uniqueDrops.addAll(player.serverLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(pos).inflate(Config.dropScanRadius()),
                    entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty()));
        }
        return List.copyOf(uniqueDrops);
    }

    private static DropInsertContext createInsertContext(
            ServerPlayer player, RtsStorageSession transientSession) {
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            return new DropInsertContext(aggregate, List.of());
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, transientSession);
        return new DropInsertContext(
                null, RtsLinkedStorageResolver.itemHandlersForInsert(linked));
    }

    private static UUID deterministicClaimId(UUID escrowId, UUID sourceId, int ordinal) {
        String material = escrowId + ":" + sourceId + ":" + ordinal;
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateExecutionIdentity(ServerPlayer player, BufferDrainTaskPayload payload) {
        if (player == null || payload == null) throw new IllegalArgumentException("执行上下文不能为空");
        if (!player.getUUID().equals(payload.ownerId())) {
            throw new IllegalArgumentException("buffer escrow 玩家不匹配");
        }
        boolean needsSourceWorld = payload.state().entries().stream()
                .anyMatch(entry -> entry.phase() == BufferEscrowPhase.SOURCE_PREPARED);
        if (!player.level().dimension().equals(payload.dimension())
                && (!payload.migratedFromLegacySession() || needsSourceWorld)) {
            throw new IllegalArgumentException("buffer escrow 维度不匹配");
        }
    }

    private static BufferDrainSliceResult idleResult(BufferEscrowState state) {
        return new BufferDrainSliceResult(state, 0, 0, 0, false, idleOutcome(state));
    }

    private static BufferDrainSliceResult.Outcome idleOutcome(BufferEscrowState state) {
        if (state.requiresRecovery()) return BufferDrainSliceResult.Outcome.RECOVERY_REQUIRED;
        if (state.isEmpty()) return BufferDrainSliceResult.Outcome.COMPLETE;
        if (state.phaseCount(BufferEscrowPhase.DRAIN_APPLIED) > 0) {
            return BufferDrainSliceResult.Outcome.WAITING_APPLIED_ACK;
        }
        return BufferDrainSliceResult.Outcome.WAITING_RESERVATION_ACK;
    }

    private record DropInsertContext(RtsAggregateStorage aggregate, List<IItemHandler> handlers) {
        ItemStack store(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            if (aggregate != null && !aggregate.isEmpty()) return aggregate.insert(stack, false);
            return handlers.isEmpty()
                    ? stack.copy()
                    : RtsTransferInserter.storeToLinkedOnly(handlers, stack);
        }

        boolean usesAggregate() {
            return aggregate != null && !aggregate.isEmpty();
        }
    }
}
