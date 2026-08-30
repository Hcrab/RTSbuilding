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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 在原版已经计算完方块掉落、但掉落实体尚未进入世界时接管 RTS 挖掘掉落。
 *
 * <p>普通 RTS 挖掘仍把事件中的精确 {@code ItemStack} 交给轻量缓存；已追踪瞬时回收
 * 则使用独立策略，在同一个最终掉落事件内直接尝试链接储存和玩家背包。两条策略都不
 * 扫描世界实体，也不会改变非 RTS 破坏。瞬时回收上下文还限定了采掘等级放宽，只允许
 * 当前玩家、当前位置、当前同步调用使用内部精准采集工具。</p>
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
            if (stack.isEmpty()) ACTIVE.remove();
        }
    }

    /**
     * 为已追踪瞬时回收开启直接入库与采掘等级旁路上下文。
     *
     * <p>即使自动入库关闭也必须压栈，因为原版破坏仍需在这个严格作用域内跳过采掘
     * 等级；此时 {@link BlockDropsEvent} 的列表完全不改动。</p>
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
            if (stack.isEmpty()) ACTIVE.remove();
            // 外层 TemporaryContextSwitcher 已先恢复真实主手；这里提交掉落事件中
            // 可能写入该槽位的背包 fallback 结果，避免临时工具遮住唯一空槽。
            player.setItemInHand(InteractionHand.MAIN_HAND, context.restoredMainHand);
            finishInstantCapture(context);
        }
    }

    /**
     * 判断当前 BreakEvent 是否属于正在提交的精确瞬时回收。
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

    /** LOWEST 优先级用于接收其他模组已经修改完成的最终掉落列表。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockDrops(BlockDropsEvent event) {
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        CaptureContext context = stack.peek();
        if (context == null
                || event.getBreaker() != context.player
                || event.getLevel() != context.player.serverLevel()) {
            return;
        }
        if (context.strategy == CaptureStrategy.BUFFERED) {
            // 普通挖掘保持原有有界缓冲策略；缓存满时余量仍由 NeoForge 生成到世界。
            RtsDropAbsorber.enqueueCapturedDrops(context.player, context.session, event.getDrops());
            return;
        }
        if (!context.autoStoreDrops || !context.targetPos.equals(event.getPos())) {
            return;
        }
        storeInstantRecoveryDrops(context, event.getDrops());
    }

    /** 只在当前瞬时回收作用域内把 harvest gate 放宽，不影响普通或未追踪挖掘。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null
                || context.strategy != CaptureStrategy.INSTANT_DIRECT
                || event.getEntity() != context.player
                || event.getLevel() != context.player.serverLevel()
                || !context.targetPos.equals(event.getPos())) {
            return;
        }
        event.setCanHarvest(true);
    }

    /**
     * 逐个处理事件实体，保留每个栈的组件与数量边界；只从列表移除已实际接收的数量。
     */
    private static void storeInstantRecoveryDrops(
            CaptureContext context, List<ItemEntity> drops) {
        List<IItemHandler> handlers = instantRecoveryHandlers(context);
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemEntity entity = iterator.next();
            if (entity == null || entity.getItem().isEmpty()) continue;
            ItemStack original = entity.getItem();
            int originalCount = original.getCount();
            ItemStack afterLinked = RtsTransferInserter.storeToLinkedOnlyPreferExisting(
                    handlers, original);
            int linkedAccepted = originalCount - afterLinked.getCount();
            ItemStack remainder = moveToInventoryWithReservedMainHand(context, afterLinked);
            int accepted = originalCount - remainder.getCount();
            if (accepted <= 0) continue;

            context.acceptedAnyDrop = true;
            context.linkedStorageChanged |= linkedAccepted > 0;
            if (remainder.isEmpty()) {
                iterator.remove();
            } else {
                entity.setItem(remainder);
            }
        }
    }

    /**
     * 在掉落事件内短暂还原真实主手，使玩家物品栏容量与未换工具时完全一致。
     * 插入后的主手内容保存在上下文中；当前破坏调用结束前仍换回内部工具，随后由
     * {@link #captureInstantRecovery} 的 finally 一次性提交。
     */
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

    /** 破坏已经完成后，这些刷新失败也不能把成功结果改判为回收失败。 */
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
        if (!context.acceptedAnyDrop) return;
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
        if (ordered.isEmpty()) return List.of();
        List<IItemHandler> handlers = new ArrayList<>(ordered.size());
        for (LinkedHandler linked : ordered) {
            if (linked == null || targetRef.equals(linked.ref()) || linked.handler() == null) continue;
            handlers.add(linked.handler());
        }
        return handlers;
    }

    private enum CaptureStrategy {
        BUFFERED,
        INSTANT_DIRECT
    }

    /**
     * 一次同步破坏的事件所有权与瞬时结果暂存。
     *
     * <p>它只存活于 ThreadLocal 栈帧，不持久化、不创建任务，也不拥有工具租约。普通
     * {@link #capture} 只使用玩家和会话字段，保持既有缓冲行为；瞬时策略额外冻结精确
     * 目标、自动入库开关和真实主手，使嵌套调用结束时能恢复上一层状态。</p>
     */
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
