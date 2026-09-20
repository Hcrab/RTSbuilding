package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 的精确挖掘掉落接管插头。
 *
 * <p>Forge 没有 NeoForge 的最终方块掉落列表事件，因此普通挖掘在
 * {@link EntityJoinLevelEvent} 的实体加入边界拦截最终 {@link ItemEntity}。缓存只接受得下
 * 全部物品时才取消实体生成，接受不完的余量仍按原版路径落到世界，不能吞物品。</p>
 *
 * <p>已追踪瞬时回收使用同一个事件边界，但采用独立的直接入库策略：只处理当前玩家、当前
 * 服务端世界和精确目标回收上下文中产生的 ItemEntity，不扫描附近实体。自动入库关闭时，
 * 掉落完全保留原版世界/玩家流转。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsMiningDropCapture {
    private static final ThreadLocal<ArrayDeque<CaptureContext>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RtsMiningDropCapture() {
    }

    /** 在一次同步方块破坏期间开启精确掉落接管；嵌套调用按栈恢复上一层上下文。 */
    public static <T> T capture(
            ServerPlayer player, RtsStorageSession session, Supplier<T> destruction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(destruction, "destruction");
        if (!RtsMiningValidator.canAutoStoreDrops(player, session)) {
            return destruction.get();
        }
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        stack.push(CaptureContext.buffered(player, session));
        try {
            return destruction.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    /**
     * 为已追踪瞬时回收开启直接入库与采掘等级旁路上下文。
     *
     * <p>即使自动入库关闭也必须压栈，因为原版破坏仍需在这个严格作用域内跳过采掘
     * 等级；此时加入世界事件中的掉落实体完全不改动。</p>
     */
    public static <T> T captureInstantRecovery(
            ServerPlayer player, RtsStorageSession session, BlockPos targetPos,
            Supplier<T> destruction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(targetPos, "targetPos");
        Objects.requireNonNull(destruction, "destruction");
        CaptureContext context = CaptureContext.instant(
                player, session, targetPos,
                RtsMiningValidator.canAutoStoreDrops(player, session));
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        stack.push(context);
        try {
            return destruction.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                ACTIVE.remove();
            }
            // 外层 TemporaryContextSwitcher 已先恢复真实主手；这里提交实体事件中可能写入
            // 该槽位的背包 fallback 结果，避免临时工具遮住唯一空槽。
            player.setItemInHand(InteractionHand.MAIN_HAND, context.restoredMainHand);
            finishInstantCapture(context);
        }
    }

    /**
     * 判断当前方块破坏事件是否属于正在提交的精确瞬时回收。
     *
     * <p>通用方块追踪监听器据此暂缓 tracker 与链接引用提交；真正破坏返回后由回收
     * 服务按世界最终状态一次性提交，避免 BreakEvent 尚未实际移除方块时提前解绑。</p>
     */
    public static boolean isInstantRecoveryTarget(
            ServerPlayer player, ServerLevel level, BlockPos pos) {
        CaptureContext context = ACTIVE.get().peek();
        return context != null
                && context.strategy == CaptureStrategy.INSTANT_DIRECT
                && context.player == player
                && context.player.serverLevel() == level
                && context.targetPos.equals(pos);
    }

    /**
     * LOWEST 让其他模组先完成实体替换或数量修改，再把最终物品交给 RTS 有界缓存或
     * 瞬时回收的直接入库策略。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null
                || event.isCanceled()
                || event.loadedFromDisk()
                || event.getLevel() != context.player.serverLevel()
                || !(event.getEntity() instanceof ItemEntity itemEntity)
                || itemEntity.getItem().isEmpty()) {
            return;
        }

        if (context.strategy == CaptureStrategy.BUFFERED) {
            ArrayList<ItemEntity> candidate = new ArrayList<>(1);
            candidate.add(itemEntity);
            RtsDropAbsorber.enqueueCapturedDrops(context.player, context.session, candidate);
            if (candidate.isEmpty()) {
                event.setCanceled(true);
            }
            return;
        }
        // 事件只会在当前同步 destroyBlock 作用域内抵达；位置不是可靠归属条件。
        // 容器/多方块掉落可能从目标格外加入世界，但仍属于这次破坏。严格的
        // ThreadLocal 作用域、服务端世界和 loadedFromDisk 门禁已排除旧实体/附近扫描。
        if (!context.autoStoreDrops) {
            return;
        }
        List<ItemEntity> candidate = new ArrayList<>(1);
        candidate.add(itemEntity);
        storeInstantRecoveryDrops(context, candidate);
        if (candidate.isEmpty()) {
            event.setCanceled(true);
        }
    }

    /** 只在当前瞬时回收作用域内把 Forge 的 harvest gate 放宽。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null
                || context.strategy != CaptureStrategy.INSTANT_DIRECT
                || event.getEntity() != context.player) {
            return;
        }
        BlockState targetState = context.player.serverLevel().getBlockState(context.targetPos);
        if (!targetState.equals(event.getTargetBlock())) {
            return;
        }
        event.setCanHarvest(true);
    }

    /** 逐个处理实体，保留每个栈的组件与数量边界；只从事件候选中移除已接收数量。 */
    private static void storeInstantRecoveryDrops(
            CaptureContext context, List<ItemEntity> drops) {
        List<IItemHandler> handlers = instantRecoveryHandlers(context);
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemEntity entity = iterator.next();
            if (entity == null || entity.getItem().isEmpty()) {
                continue;
            }
            ItemStack original = entity.getItem();
            int originalCount = original.getCount();
            ItemStack afterLinked = RtsTransferInserter.storeToLinkedOnlyPreferExisting(
                    handlers, original);
            int linkedAccepted = originalCount - afterLinked.getCount();
            ItemStack remainder = moveToInventoryWithReservedMainHand(context, afterLinked);
            int accepted = originalCount - remainder.getCount();
            if (accepted <= 0) {
                continue;
            }

            context.acceptedAnyDrop = true;
            context.linkedStorageChanged |= linkedAccepted > 0;
            if (remainder.isEmpty()) {
                iterator.remove();
            } else {
                entity.setItem(remainder);
            }
        }
    }

    /** 在事件内短暂还原真实主手，使背包容量与未换工具时一致。 */
    private static ItemStack moveToInventoryWithReservedMainHand(
            CaptureContext context, ItemStack stack) {
        ItemStack internalTool = context.player.getMainHandItem();
        context.player.setItemInHand(InteractionHand.MAIN_HAND, context.restoredMainHand);
        try {
            return RtsTransferInserter.moveToPlayerInventoryOnly(context.player, stack);
        } finally {
            context.restoredMainHand = context.player.getMainHandItem();
            context.player.setItemInHand(InteractionHand.MAIN_HAND, internalTool);
        }
    }

    /** 破坏完成后的刷新失败不能把已提交的世界变化改判为回收失败。 */
    private static void finishInstantCapture(CaptureContext context) {
        if (context.linkedStorageChanged) {
            try {
                RtsTransferInserter.refreshCache(context.player);
            } catch (Exception exception) {
                RtsbuildingMod.LOGGER.warn(
                        "[PlacedRecovery] 刷新链接储存缓存失败：player={}",
                        context.player.getGameProfile().getName(), exception);
            }
        }
        if (!context.acceptedAnyDrop) {
            return;
        }
        try {
            ServiceRegistry.getInstance().page().markStorageViewDirty(context.player, context.session);
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[PlacedRecovery] 标记储存页面刷新失败：player={}",
                    context.player.getGameProfile().getName(), exception);
        }
        try {
            QuestService.runQuestDetect(context.player, context.session, false);
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[PlacedRecovery] 任务进度刷新失败：player={}",
                    context.player.getGameProfile().getName(), exception);
        }
    }

    private static List<IItemHandler> instantRecoveryHandlers(CaptureContext context) {
        LinkedStorageRef targetRef = new LinkedStorageRef(
                context.player.serverLevel().dimension(), context.targetPos);
        List<LinkedHandler> ordered = RtsLinkedHandlerResolutionService.orderHandlersForInsert(
                RtsLinkedStorageResolver.resolveLinkedHandlers(context.player, context.session));
        if (ordered.isEmpty()) {
            return List.of();
        }
        List<IItemHandler> handlers = new ArrayList<>(ordered.size());
        for (LinkedHandler linked : ordered) {
            if (linked == null || targetRef.equals(linked.ref()) || linked.handler() == null) {
                continue;
            }
            handlers.add(linked.handler());
        }
        return handlers;
    }

    private enum CaptureStrategy {
        BUFFERED,
        INSTANT_DIRECT
    }

    /** 一次同步破坏的事件所有权与瞬时结果暂存，仅存活于 ThreadLocal 栈帧。 */
    private static final class CaptureContext {
        private final ServerPlayer player;
        private final RtsStorageSession session;
        private final CaptureStrategy strategy;
        private final BlockPos targetPos;
        private final boolean autoStoreDrops;
        private ItemStack restoredMainHand;
        private boolean acceptedAnyDrop;
        private boolean linkedStorageChanged;

        private CaptureContext(
                ServerPlayer player, RtsStorageSession session,
                CaptureStrategy strategy, BlockPos targetPos, boolean autoStoreDrops) {
            this.player = player;
            this.session = session;
            this.strategy = strategy;
            this.targetPos = targetPos;
            this.autoStoreDrops = autoStoreDrops;
            this.restoredMainHand = player.getMainHandItem();
        }

        private static CaptureContext buffered(ServerPlayer player, RtsStorageSession session) {
            return new CaptureContext(player, session, CaptureStrategy.BUFFERED, null, true);
        }

        private static CaptureContext instant(
                ServerPlayer player, RtsStorageSession session,
                BlockPos targetPos, boolean autoStoreDrops) {
            return new CaptureContext(
                    player, session, CaptureStrategy.INSTANT_DIRECT,
                    targetPos.immutable(), autoStoreDrops);
        }
    }
}
