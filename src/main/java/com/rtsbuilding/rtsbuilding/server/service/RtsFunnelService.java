package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物漏斗服务——自动收集地面掉落物并存入链接存???
 *
 * <p>职责范围??
 * <ul>
 *   <li>漏斗开关管??/li>
 *   <li>漏斗缓冲区管??/li>
 *   <li>Tick 处理：吸收掉落物 ??存入缓冲????刷入链接存储</li>
 * </ul>
 */
public final class RtsFunnelService {

    public static final RtsFunnelService INSTANCE = new RtsFunnelService();

    private static final double FUNNEL_RADIUS = 2.0D;
    private static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;
    private static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;
    private static final int FUNNEL_BUFFER_MAX_STACKS = 16;
    private static final int FUNNEL_TICK_INTERVAL = 2;

    private RtsFunnelService() {
    }

    /**
     * 启用漏斗??
     */
    public static void enable(ServerPlayer player, RtsStorageSession session) {
        session.funnel.funnelEnabled = true;
        session.funnel.funnelTickCooldown = 0;
    }

    /**
     * 禁用漏斗并清空缓冲区??
     */
    public static void disableAndFlush(ServerPlayer player, RtsStorageSession session) {
        session.funnel.funnelEnabled = false;
        session.funnel.funnelTarget = null;
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

    /**
     * 更新漏斗目标位置??
     */
    public static void updateTarget(ServerPlayer player, RtsStorageSession session, BlockPos target) {
        if (!session.funnel.funnelEnabled || target == null) return;
        session.funnel.funnelTarget = target.immutable();
    }

    /**
     * Tick 处理漏斗逻辑??
     */
    public static void tick(ServerPlayer player, RtsStorageSession session) {
        if (!session.funnel.funnelEnabled || session.mode != BuilderMode.FUNNEL) return;
        if (session.funnel.funnelTickCooldown > 0) {
            session.funnel.funnelTickCooldown--;
            return;
        }
        session.funnel.funnelTickCooldown = FUNNEL_TICK_INTERVAL - 1;

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (session.funnel.funnelTarget == null) return;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.funnel.funnelTarget)) return;
        if (!RtsClaimProtectionService.canInteractBlock(
                player, session.funnel.funnelTarget, Direction.UP, InteractionHand.MAIN_HAND, ItemStack.EMPTY)) return;
        if (!RtsCameraManager.isWithinActionRadius(player, session.funnel.funnelTarget)) return;

        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler lh : linked) {
            handlers.add(lh.handler());
        }

        boolean changed = flushBuffer(handlers, player, session);
        changed |= absorbDrops(player, session.funnel.funnelTarget, handlers, session);
        if (changed) {
            RtsSessionService.markStorageViewDirty(player, session);
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- 内部方法 ----

    private static boolean flushBuffer(List<IItemHandler> handlers, ServerPlayer player, RtsStorageSession session) {
        if (session.funnel.funnelBuffer.isEmpty()) return false;
        boolean changed = false;
        for (int i = 0; i < session.funnel.funnelBuffer.size(); i++) {
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
        return changed;
    }

    private static boolean absorbDrops(ServerPlayer player, BlockPos target, List<IItemHandler> handlers, RtsStorageSession session) {
        AABB box = new AABB(target).inflate(FUNNEL_RADIUS);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class, box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty());

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;

        for (ItemEntity drop : drops) {
            if (processedEntities >= FUNNEL_MAX_ENTITIES_PER_TICK || processedItems >= FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            processedEntities++;
            ItemStack worldStack = drop.getItem();
            if (worldStack.isEmpty()) continue;

            int remainingBudget = FUNNEL_MAX_ITEMS_PER_TICK - processedItems;
            int iterations = Math.min(worldStack.getCount(), remainingBudget);
            for (int i = 0; i < iterations; i++) {
                ItemStack one = worldStack.copy();
                one.setCount(1);
                ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, one);
                if (!remain.isEmpty()) {
                    remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
                }
                if (!remain.isEmpty()) {
                    remain = addToBuffer(session, remain);
                }
                if (!remain.isEmpty()) break;
                worldStack.shrink(1);
                processedItems++;
                changed = true;
                if (worldStack.isEmpty()) break;
            }
            if (worldStack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(worldStack);
            }
        }
        return changed;
    }

    private static ItemStack addToBuffer(RtsStorageSession session, ItemStack stack) {
        ItemStack remain = stack.copy();
        if (remain.isEmpty()) return ItemStack.EMPTY;

        for (ItemStack buffered : session.funnel.funnelBuffer) {
            if (remain.isEmpty()) break;
            if (buffered.isEmpty() || !ItemStack.isSameItemSameTags(buffered, remain)) continue;
            int free = Math.max(0, buffered.getMaxStackSize() - buffered.getCount());
            if (free <= 0) continue;
            int move = Math.min(free, remain.getCount());
            buffered.grow(move);
            remain.shrink(move);
        }

        while (!remain.isEmpty() && session.funnel.funnelBuffer.size() < FUNNEL_BUFFER_MAX_STACKS) {
            int move = Math.min(remain.getCount(), remain.getMaxStackSize());
            ItemStack chunk = remain.copy();
            chunk.setCount(move);
            session.funnel.funnelBuffer.add(chunk);
            remain.shrink(move);
        }
        return remain;
    }
}
