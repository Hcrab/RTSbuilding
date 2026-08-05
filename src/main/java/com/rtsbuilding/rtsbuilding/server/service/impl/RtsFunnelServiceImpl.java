package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsBoundedItemEntityQuery;
import com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.FunnelService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link FunnelService} 的默认实现——处理掉落物漏斗的启用/禁用、
 * 目标更新和每 Tick 的掉落物收集逻辑。
 *
 * <p>掉落物漏斗自动扫描目标位置附近的 {@link EntityItem}，
 * 将掉落物吸入链接存储。当链接存储满时，多余物品会先存入玩家背包，
 * 再存入内部缓冲区。禁用漏斗时会清空缓冲区。
 */
public final class RtsFunnelServiceImpl implements FunnelService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void enable(EntityPlayerMP player, RtsStorageSession session) {
        session.funnel.funnelEnabled = true;
        session.funnel.funnelTickCooldown = 0;
        registry.session().saveFunnelToPlayerNbt(player, session);
    }

    @Override
    public void disableAndFlush(EntityPlayerMP player, RtsStorageSession session) {
        session.funnel.funnelEnabled = false;
        session.funnel.funnelTarget = null;
        session.funnel.funnelTargetDimension = null;
        session.funnel.funnelTickCooldown = 0;
        if (session.funnel.funnelBuffer.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<IItemHandler>(linked.size());
        for (LinkedHandler h : linked) {
            handlers.add(h.handler());
        }
        for (ItemStack buffered : session.funnel.funnelBuffer) {
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(buffered)) continue;
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) {
                RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain);
            }
        }
        session.funnel.funnelBuffer.clear();
    }

    @Override
    public void updateTarget(EntityPlayerMP player, RtsStorageSession session, BlockPos target) {
        if (!session.funnel.funnelEnabled || target == null) return;
        session.funnel.funnelTarget = target.toImmutable();
        session.funnel.funnelTargetDimension = player.dimension;
        registry.session().saveFunnelToPlayerNbt(player, session);
    }

    @Override
    public void tick(EntityPlayerMP player, RtsStorageSession session) {
        tickBudgeted(player, session,
                RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK, Long.MAX_VALUE);
    }

    @Override
    public FunnelTickResult tickBudgeted(
            EntityPlayerMP player, RtsStorageSession session, int maxUnits, long deadlineNanos) {
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
                || player.dimension != session.funnel.funnelTargetDimension.intValue()) {
            // 目标属于其他维度时只让出本轮调度，绝不解析端点或扫描当前世界的同坐标。
            return new FunnelTickResult(0, true);
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.funnel.funnelTarget)) {
            return new FunnelTickResult(0, true);
        }
        if (!RtsClaimProtectionService.canInteractBlock(
                player, session.funnel.funnelTarget, EnumFacing.UP,
                EnumHand.MAIN_HAND, null)) return new FunnelTickResult(0, true);
        if (!RtsCameraManager.isWithinActionRange(player, session.funnel.funnelTarget)) {
            return new FunnelTickResult(0, true);
        }

        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<IItemHandler>(linked.size());
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

    private WorkResult flushBuffer(List<IItemHandler> handlers, EntityPlayerMP player,
            RtsStorageSession session, int maxUnits, long deadlineNanos) {
        if (session.funnel.funnelBuffer.isEmpty()) return new WorkResult(0, false);
        boolean changed = false;
        int processed = 0;
        for (int i = 0; i < session.funnel.funnelBuffer.size()
                && processed < maxUnits && System.nanoTime() < deadlineNanos; i++) {
            processed++;
            ItemStack buffered = session.funnel.funnelBuffer.get(i);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(buffered)) {
                session.funnel.funnelBuffer.remove(i);
                i--;
                changed = true;
                continue;
            }
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) {
                session.funnel.funnelBuffer.remove(i);
                i--;
                changed = true;
            } else if (!ItemStack.areItemStacksEqual(remain, buffered)) {
                session.funnel.funnelBuffer.set(i, remain);
                changed = true;
            }
        }
        return new WorkResult(processed, changed);
    }

    private WorkResult absorbDrops(EntityPlayerMP player, BlockPos target, List<IItemHandler> handlers,
            RtsStorageSession session, int maxUnits, long deadlineNanos) {
        AxisAlignedBB box = new AxisAlignedBB(target).grow(RtsServiceConstants.FUNNEL_RADIUS);
        int queryLimit = Math.min(RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK, Math.max(0, maxUnits));
        if (queryLimit == 0) return new WorkResult(0, false);
        List<EntityItem> drops = RtsBoundedItemEntityQuery.query(
                player.getServerForPlayer(), box, queryLimit,
                entity -> entity != null && entity.isEntityAlive() && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(entity.getEntityItem())).entities();

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;

        for (EntityItem drop : drops) {
            if (processedEntities >= RtsServiceConstants.FUNNEL_MAX_ENTITIES_PER_TICK
                    || processedEntities >= maxUnits
                    || processedItems >= RtsServiceConstants.FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            if (System.nanoTime() >= deadlineNanos) break;
            if (drop == null || !drop.isEntityAlive()) continue;
            ItemStack worldStack = drop.getEntityItem();
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(worldStack)) continue;
            processedEntities++;

            int batchSize = Math.min(worldStack.stackSize,
                    RtsServiceConstants.FUNNEL_MAX_ITEMS_PER_TICK - processedItems);
            if (batchSize <= 0) break;
            // 批量插入：一次传入整个 batch，减少存储调用次数
            ItemStack batch = worldStack.copy();
            batch.stackSize = batchSize;
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, batch);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            // A 阶段不把新掉落复制到尚未落盘的缓冲区；放不下的 remainder 继续由世界实体持有。
            int inserted = batchSize - (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain) ? 0 : remain.stackSize);
            if (inserted > 0) {
                com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.shrink(worldStack, inserted);
                processedItems += inserted;
                changed = true;
            }
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(worldStack)) {
                drop.setDead();
            } else {
                drop.setEntityItemStack(worldStack);
            }
        }
        return new WorkResult(processedEntities, changed);
    }

    private static final class WorkResult {
        private final int processedUnits;
        private final boolean changed;

        private WorkResult(int processedUnits, boolean changed) {
            this.processedUnits = processedUnits;
            this.changed = changed;
        }

        private int processedUnits() {
            return processedUnits;
        }

        private boolean changed() {
            return changed;
        }
    }

}
