package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.FunnelService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link FunnelService} 的默认实现——处理掉落物漏斗的启用/禁用、
 * 目标更新和每 Tick 的掉落物收集逻辑。
 *
 * <p>掉落物漏斗自动扫描目标位置附近的 {@link net.minecraft.world.entity.item.ItemEntity}，
 * 将掉落物吸入链接存储。当链接存储满时，多余物品会先存入玩家背包，
 * 再存入内部缓冲区。禁用漏斗时会清空缓冲区。
 */
public final class RtsFunnelServiceImpl implements FunnelService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void enable(ServerPlayer player, RtsStorageSession session) {
        session.funnel.funnelEnabled = true;
        session.funnel.funnelTickCooldown = 0;
        registry.session().saveFunnelToPlayerNbt(player, session);
    }

    @Override
    public void disableAndFlush(ServerPlayer player, RtsStorageSession session) {
        session.funnel.funnelEnabled = false;
        session.funnel.funnelTarget = null;
        session.funnel.funnelTargetDimension = null;
        session.funnel.funnelTickCooldown = 0;
        if (session.funnel.funnelBuffer.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler h : linked) {
            handlers.add(h.handler());
        }
        for (ItemStack buffered : session.funnel.funnelBuffer) {
            if (buffered.isEmpty()) continue;
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain);
            }
        }
        session.funnel.funnelBuffer.clear();
    }

    @Override
    public void updateTarget(ServerPlayer player, RtsStorageSession session, BlockPos target) {
        if (!session.funnel.funnelEnabled || target == null) return;
        session.funnel.funnelTarget = target.immutable();
        session.funnel.funnelTargetDimension = player.serverLevel().dimension();
        registry.session().saveFunnelToPlayerNbt(player, session);
    }

    @Override
    public void tick(ServerPlayer player, RtsStorageSession session) {
        tickBudgeted(player, session,
                RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK, Long.MAX_VALUE);
    }

    @Override
    public FunnelTickResult tickBudgeted(
            ServerPlayer player, RtsStorageSession session, int maxUnits, long deadlineNanos) {
        if (!session.funnel.funnelEnabled || session.mode != BuilderMode.FUNNEL) {
            return new FunnelTickResult(0, false);
        }
        if (session.funnel.funnelTickCooldown > 0) {
            session.funnel.funnelTickCooldown--;
            return new FunnelTickResult(0, true);
        }
        session.funnel.funnelTickCooldown = RtsServiceConstants.FUNNEL_TICK_INTERVAL - 1;

        if (session.funnel.funnelTarget == null) return new FunnelTickResult(0, true);
        if (session.funnel.funnelTargetDimension == null
                || !player.serverLevel().dimension().equals(session.funnel.funnelTargetDimension)) {
            // 目标属于其他维度时只让出本轮调度，绝不解析端点或扫描当前世界的同坐标。
            return new FunnelTickResult(0, true);
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.funnel.funnelTarget)) {
            return new FunnelTickResult(0, true);
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, session.funnel.funnelTarget, Direction.UP,
                InteractionHand.MAIN_HAND, ItemStack.EMPTY)) return new FunnelTickResult(0, true);
        if (!RtsCameraManager.isWithinActionRange(player, session.funnel.funnelTarget)) {
            return new FunnelTickResult(0, true);
        }

        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler lh : linked) {
            handlers.add(lh.handler());
        }

        int limit = Math.max(1, maxUnits);
        WorkResult flushed = flushBuffer(handlers, player, session, limit, deadlineNanos);
        int remainingUnits = Math.max(0, limit - flushed.processedUnits());
        WorkResult absorbed = remainingUnits == 0 || System.nanoTime() >= deadlineNanos
                ? new WorkResult(0, false)
                : absorbDrops(player, session.funnel.funnelTarget, handlers, session,
                        remainingUnits, deadlineNanos);
        boolean changed = flushed.changed() || absorbed.changed();
        if (changed) {
            registry.session().saveFunnelToPlayerNbt(player, session);
            registry.page().markStorageViewDirty(player, session);
            QuestService.runQuestDetect(player, session, false);
        }
        return new FunnelTickResult(flushed.processedUnits() + absorbed.processedUnits(), true);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private WorkResult flushBuffer(List<IItemHandler> handlers, ServerPlayer player,
            RtsStorageSession session, int maxUnits, long deadlineNanos) {
        if (session.funnel.funnelBuffer.isEmpty()) return new WorkResult(0, false);
        boolean changed = false;
        int processed = 0;
        for (int i = 0; i < session.funnel.funnelBuffer.size()
                && processed < maxUnits && System.nanoTime() < deadlineNanos; i++) {
            processed++;
            ItemStack buffered = session.funnel.funnelBuffer.get(i);
            if (buffered.isEmpty()) {
                session.funnel.funnelBuffer.remove(i);
                i--;
                changed = true;
                continue;
            }
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.isEmpty()) {
                session.funnel.funnelBuffer.remove(i);
                i--;
                changed = true;
            } else if (remain.getCount() != buffered.getCount()) {
                session.funnel.funnelBuffer.set(i, remain);
                changed = true;
            }
        }
        return new WorkResult(processed, changed);
    }

    private WorkResult absorbDrops(ServerPlayer player, BlockPos target, List<IItemHandler> handlers,
            RtsStorageSession session, int maxUnits, long deadlineNanos) {
        AABB box = new AABB(target).inflate(RtsServiceConstants.FUNNEL_RADIUS);
        int queryLimit = Math.min(maxUnits, RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK);
        List<ItemEntity> drops = new ArrayList<>(queryLimit);
        player.serverLevel().getEntities(
                EntityTypeTest.forClass(ItemEntity.class), box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty(), drops, queryLimit);

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;

        for (ItemEntity drop : drops) {
            if (processedEntities >= RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK
                    || processedEntities >= maxUnits
                    || processedItems >= RtsServiceConstants.FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            if (System.nanoTime() >= deadlineNanos) break;
            processedEntities++;
            ItemStack worldStack = drop.getItem();
            if (worldStack.isEmpty()) continue;

            int batchSize = Math.min(worldStack.getCount(),
                    RtsServiceConstants.FUNNEL_MAX_ITEMS_PER_TICK - processedItems);
            if (batchSize <= 0) break;
            // 批量插入：一次传入整个 batch，减少存储调用次数
            ItemStack batch = worldStack.copy();
            batch.setCount(batchSize);
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, batch);
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            // A 阶段不把新掉落复制到尚未落盘的缓冲区；放不下的 remainder 继续由世界实体持有。
            int inserted = batchSize - (remain.isEmpty() ? 0 : remain.getCount());
            if (inserted > 0) {
                worldStack.shrink(inserted);
                processedItems += inserted;
                changed = true;
            }
            if (worldStack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(worldStack);
            }
        }
        return new WorkResult(processedEntities, changed);
    }

    private record WorkResult(int processedUnits, boolean changed) {
    }

}
