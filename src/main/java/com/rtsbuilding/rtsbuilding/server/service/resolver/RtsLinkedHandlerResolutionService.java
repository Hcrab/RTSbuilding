package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.handler.RtsLinkedCapabilities;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.view.LinkedFluidHandlerView;
import com.rtsbuilding.rtsbuilding.server.storage.view.LinkedItemHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 链接处理器解析服务——将链接存储引用解析为实时的物品/流体处理器。
 *
 * <p>该服务负责：
 * <ul>
 *   <li>将 {@code LinkedStorageRef} 解析为 {@link LinkedHandler}（物品）和 {@link LinkedFluidHandler}（流体）</li>
 *   <li>集成 BD 网络存储作为额外的处理器源</li>
 *   <li>将解析后的处理器注册到 {@link RtsStorageTickService} 缓存系统</li>
 *   <li>按优先级对手柄进行排序（提取时低优先优先，存入时高优先优先）</li>
 * </ul>
 *
 * <p>从 {@link RtsLinkedStorageResolver} 提取，以将处理器解析和排序
 * 与访问检查和摘要构建关注点分离。
 */
public final class RtsLinkedHandlerResolutionService {

    private RtsLinkedHandlerResolutionService() {
    }

    // ======================================================================
    //  Item handler resolution
    // ======================================================================

    /**
     * 解析每个当前可访问的物品端点，包括 BD 网络回退，
     * 转换为已强制执行仅提取存储规则的处理器。
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> out = new ArrayList<>();

        if (!session.linkedStorageInfo.getAll().isEmpty()) {
            ResourceKey<Level> currentDimension = player.serverLevel().dimension();
            for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
                if (ref == null || ref.pos() == null) {
                    continue;
                }
                BlockPos pos = ref.pos();
                UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
                boolean backpackLink = backpackUuid != null;
                boolean sameDimension = currentDimension.equals(ref.dimension());
                IItemHandler handler = null;

                if (sameDimension && !session.linkedStorageInfo.isDetached(ref)
                        && RtsProgressionManager.canAccessHomeRadius(player, pos)
                        && player.serverLevel().hasChunkAt(pos)) {
                    Object endpointIdentity = player.serverLevel().getBlockEntity(pos);
                    handler = RtsEndpointLeaseCache.INSTANCE.resolveItem(
                            player.getUUID(), currentDimension, pos, backpackUuid, endpointIdentity,
                            () -> backpackLink
                                    ? findMatchingBackpackBlockHandler(player, pos, backpackUuid)
                                    : RtsLinkedCapabilities.findLinkedItemHandler(player, pos));
                }

                if (handler == null && backpackLink) {
                    handler = RtsEndpointLeaseCache.INSTANCE.resolveItem(
                            player.getUUID(), ref.dimension(), pos, backpackUuid, null,
                            () -> RtsBackpackCompat.openBackpack(backpackUuid,
                                    session.linkedStorageInfo.getBackpackItemId(ref), player).orElse(null));
                }

                if (handler == null) {
                    continue;
                }
                String name = session.linkedStorageInfo.computeNameIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
                out.add(new LinkedHandler(ref, name, new LinkedItemHandlerView(handler, allowStore), allowStore,
                        linkedPriority(session, ref)));
            }
        }

        if (session.sessionFlags.useBdNetwork) {
            if (session.bdCache.handlerStale || session.bdCache.handler == null) {
                if (RtsBdCompat.hasPrimaryNetwork(player)) {
                    if (session.bdCache.handler == null) {
                        session.bdCache.handler = RtsBdCompat.createNetworkItemHandler(player);
                    } else {
                        RtsBdCompat.refreshNetworkHandler(session.bdCache.handler);
                    }
                    session.bdCache.name = RtsBdCompat.getNetworkDisplayName(player);
                } else {
                    session.bdCache.handler = null;
                    session.bdCache.fluidHandler = null;
                }
                session.bdCache.handlerStale = false;
            }
        }
        if (session.bdCache.handler != null) {
            LinkedStorageRef bdRef = new LinkedStorageRef(
                    player.serverLevel().dimension(),
                    BlockPos.ZERO);
            out.add(new LinkedHandler(bdRef, session.bdCache.name, session.bdCache.handler, true, 0));
        }

        return out;
    }

    /**
     * 将解析后的链接处理器的原始（未包装）物品处理器注册到
     * {@link RtsStorageTickService} 缓存系统中，以便后续的页面构建
     * 和传输操作可以从槽位缓存中读取，而不是每次操作都在每个处理器上
     * 调用 {@code getStackInSlot()}。
     *
     * <p>在 {@link #resolveLinkedHandlers(ServerPlayer, RtsStorageSession)}
     * 之后调用此方法，以播种每玩家的聚合存储。
     */
    public static void registerStorageCaches(ServerPlayer player, List<LinkedHandler> handlers) {
        if (player == null) {
            return;
        }
        if (handlers == null || handlers.isEmpty()) {
            RtsStorageTickService.INSTANCE.unregisterPlayer(player);
            return;
        }
        List<IItemHandler> rawHandlers = new ArrayList<>(handlers.size());
        for (LinkedHandler lh : handlers) {
            IItemHandler h = lh.handler();
            if (h instanceof LinkedItemHandlerView view) {
                rawHandlers.add(view.getRawHandler());
            } else {
                rawHandlers.add(h);
            }
        }
        RtsStorageTickService.INSTANCE.registerPlayer(player, rawHandlers);
    }

    // ======================================================================
    //  Fluid handler resolution
    // ======================================================================

    /**
     * 在物品端点旁边解析流体端点，以便仅提取链接不能接受存储的流体，
     * 同时仍允许提取。
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> out = new ArrayList<>();

        if (!session.linkedStorageInfo.getAll().isEmpty()) {
            ResourceKey<Level> currentDimension = player.serverLevel().dimension();
            for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
                if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                    continue;
                }
                BlockPos pos = ref.pos();
                if (!RtsProgressionManager.canAccessHomeRadius(player, pos)) {
                    continue;
                }
                if (!player.serverLevel().hasChunkAt(pos)) {
                    continue;
                }
                IFluidHandler handler = RtsLinkedCapabilities.findFluidHandler(player, pos);
                if (handler == null) {
                    continue;
                }
                String name = session.linkedStorageInfo.computeNameIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
                out.add(new LinkedFluidHandler(ref, name, new LinkedFluidHandlerView(handler, allowStore), allowStore,
                        linkedPriority(session, ref)));
            }
        }

        if (session.sessionFlags.useBdNetwork) {
            if (session.bdCache.fluidHandlerStale || session.bdCache.fluidHandler == null) {
                if (RtsBdCompat.hasPrimaryNetwork(player)) {
                    session.bdCache.fluidHandler = RtsBdCompat.createNetworkFluidHandler(player);
                } else {
                    session.bdCache.fluidHandler = null;
                }
                session.bdCache.fluidHandlerStale = false;
            }
        }
        if (session.bdCache.fluidHandler != null) {
            String bdName = session.bdCache.name != null
                    ? session.bdCache.name
                    : RtsBdCompat.getNetworkDisplayName(player);
            LinkedStorageRef bdRef = new LinkedStorageRef(
                    player.serverLevel().dimension(),
                    BlockPos.ZERO);
            out.add(new LinkedFluidHandler(bdRef, bdName, session.bdCache.fluidHandler, true, 0));
        }

        return out;
    }

    // ======================================================================
    //  Ordering helpers
    // ======================================================================

    public static List<LinkedHandler> orderHandlersForInsert(List<LinkedHandler> handlers) {
        return orderedHandlers(handlers, Comparator.comparingInt(LinkedHandler::priority).reversed());
    }

    public static List<LinkedHandler> orderHandlersForExtract(List<LinkedHandler> handlers) {
        return orderedHandlers(handlers, Comparator.comparingInt(LinkedHandler::priority));
    }

    public static List<IItemHandler> itemHandlersForInsert(List<LinkedHandler> handlers) {
        return toItemHandlers(orderHandlersForInsert(handlers));
    }

    public static List<IItemHandler> itemHandlersForExtract(List<LinkedHandler> handlers) {
        return toItemHandlers(orderHandlersForExtract(handlers));
    }

    public static List<LinkedFluidHandler> orderFluidHandlersForInsert(List<LinkedFluidHandler> handlers) {
        return orderedFluidHandlers(handlers, Comparator.comparingInt(LinkedFluidHandler::priority).reversed());
    }

    public static List<LinkedFluidHandler> orderFluidHandlersForExtract(List<LinkedFluidHandler> handlers) {
        return orderedFluidHandlers(handlers, Comparator.comparingInt(LinkedFluidHandler::priority));
    }

    // ======================================================================
    //  Private helpers
    // ======================================================================

    private static int linkedPriority(RtsStorageSession session, LinkedStorageRef ref) {
        return session == null || ref == null
                ? 0
                : RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                        session.linkedStorageInfo.getPriority(ref));
    }

    private static IItemHandler findMatchingBackpackBlockHandler(ServerPlayer player, BlockPos pos, UUID expectedUuid) {
        if (expectedUuid == null || !expectedUuid.equals(readBackpackUuid(player.serverLevel(), pos))) {
            return null;
        }
        return RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
    }

    private static UUID readBackpackUuid(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
    }

    private static List<LinkedHandler> orderedHandlers(List<LinkedHandler> handlers, Comparator<LinkedHandler> comparator) {
        if (handlers == null || handlers.size() <= 1) {
            return handlers == null ? List.of() : handlers;
        }
        List<LinkedHandler> ordered = new ArrayList<>(handlers);
        ordered.sort(comparator);
        return ordered;
    }

    private static List<IItemHandler> toItemHandlers(List<LinkedHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return List.of();
        }
        List<IItemHandler> out = new ArrayList<>(handlers.size());
        for (LinkedHandler linked : handlers) {
            out.add(linked.handler());
        }
        return out;
    }

    private static List<LinkedFluidHandler> orderedFluidHandlers(List<LinkedFluidHandler> handlers,
            Comparator<LinkedFluidHandler> comparator) {
        if (handlers == null || handlers.size() <= 1) {
            return handlers == null ? List.of() : handlers;
        }
        List<LinkedFluidHandler> ordered = new ArrayList<>(handlers);
        ordered.sort(comparator);
        return ordered;
    }
}
