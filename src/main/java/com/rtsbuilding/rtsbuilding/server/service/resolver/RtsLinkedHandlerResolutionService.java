package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

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
    public static List<LinkedHandler> resolveLinkedHandlers(EntityPlayerMP player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> out = new ArrayList<>();

        if (!session.linkedStorageInfo.getAll().isEmpty()) {
            int currentDimension = player.dimension;
            for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
                if (ref == null || ref.pos() == null) {
                    continue;
                }
                BlockPos pos = ref.pos();
                UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
                boolean backpackLink = backpackUuid != null;
                boolean sameDimension = currentDimension == ref.dimension();
                WorldServer targetLevel = player.getServer() == null
                        ? null : player.getServer().getWorld(ref.dimension());
                IItemHandler handler = null;

                if (RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref)) {
                    Object endpointIdentity = targetLevel.getTileEntity(pos);
                    handler = RtsEndpointLeaseCache.INSTANCE.resolveItem(
                            player.getUniqueID(), ref.dimension(), pos, backpackUuid, endpointIdentity,
                            () -> backpackLink
                                    ? findMatchingBackpackBlockHandler(player, targetLevel, pos, backpackUuid, sameDimension)
                                    : resolveItemHandler(player, targetLevel, pos, sameDimension));
                }

                if (handler == null && backpackLink) {
                    if (!sameDimension && !RtsLinkedStorageResolver.isCrossDimensionStorageAllowed(player)) {
                        continue;
                    }
                    handler = RtsEndpointLeaseCache.INSTANCE.resolveItem(
                            player.getUniqueID(), ref.dimension(), pos, backpackUuid, null,
                            () -> RtsBackpackCompat.openBackpack(backpackUuid,
                                    session.linkedStorageInfo.getBackpackItemId(ref), player).orElse(null));
                }

                if (handler == null) {
                    continue;
                }
                String name = session.linkedStorageInfo.computeNameIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(targetLevel, pos));
                BooleanSupplier storePermission = () -> RtsLinkedStorageResolver.isStoreAllowed(session, ref);
                boolean allowStore = storePermission.getAsBoolean();
                out.add(new LinkedHandler(ref, name, new LinkedItemHandlerView(handler, storePermission), allowStore,
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
                    player.dimension,
                    new BlockPos(0, 0, 0));
            out.add(new LinkedHandler(bdRef, session.bdCache.name, session.bdCache.handler, true, 0));
        }

        return out;
    }

    /**
     * 将解析后的带权限视图注册到 {@link RtsStorageTickService} 缓存系统中，
     * 以便后续的页面构建
     * 和传输操作可以从槽位缓存中读取，而不是每次操作都在每个处理器上
     * 调用 {@code getStackInSlot()}。
     *
     * <p>不能在这里剥离 {@link LinkedItemHandlerView}：聚合缓存也承担远程掉落
     * 快速入库，若挂载原始 capability，Extract-only 会被绕过。Tick 服务会用
     * 视图的原始端点身份复用快照，同时让所有写入继续经过权限视图。
     */
    public static void registerStorageCaches(EntityPlayerMP player, List<LinkedHandler> handlers) {
        if (player == null) {
            return;
        }
        if (handlers == null || handlers.isEmpty()) {
            RtsStorageTickService.INSTANCE.unregisterPlayer(player);
            return;
        }
        List<IItemHandler> policyHandlers = new ArrayList<>(handlers.size());
        for (LinkedHandler lh : handlers) {
            if (lh != null && lh.handler() != null) policyHandlers.add(lh.handler());
        }
        RtsStorageTickService.INSTANCE.registerPlayer(player, policyHandlers);
    }

    // ======================================================================
    //  Fluid handler resolution
    // ======================================================================

    /**
     * 在物品端点旁边解析流体端点，以便仅提取链接不能接受存储的流体，
     * 同时仍允许提取。
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(
            EntityPlayerMP player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> out = new ArrayList<>();

        if (!session.linkedStorageInfo.getAll().isEmpty()) {
            for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
                if (ref == null || ref.pos() == null) {
                    continue;
                }
                BlockPos pos = ref.pos();
                WorldServer targetLevel = player.getServer() == null
                        ? null : player.getServer().getWorld(ref.dimension());
                if (!RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref)) {
                    continue;
                }
                IFluidHandler handler = RtsLinkedCapabilities.findFluidHandlerInLevel(targetLevel, pos);
                if (handler == null) {
                    continue;
                }
                String name = session.linkedStorageInfo.computeNameIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(targetLevel, pos));
                BooleanSupplier storePermission = () -> RtsLinkedStorageResolver.isStoreAllowed(session, ref);
                boolean allowStore = storePermission.getAsBoolean();
                out.add(new LinkedFluidHandler(ref, name, new LinkedFluidHandlerView(handler, storePermission), allowStore,
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
                    player.dimension,
                    new BlockPos(0, 0, 0));
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
        return toItemHandlers(orderHandlersForInsert(handlers), true);
    }

    public static List<IItemHandler> itemHandlersForExtract(List<LinkedHandler> handlers) {
        return toItemHandlers(orderHandlersForExtract(handlers), false);
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

    private static IItemHandler findMatchingBackpackBlockHandler(
            EntityPlayerMP player, WorldServer level, BlockPos pos, UUID expectedUuid, boolean sameDimension) {
        if (expectedUuid == null || !expectedUuid.equals(readBackpackUuid(level, pos))) {
            return null;
        }
        return resolveItemHandler(player, level, pos, sameDimension);
    }

    /** 异维只走原生 Capability；AE2/RS 适配器仍仅在本维且实际依赖存在时参与。 */
    private static IItemHandler resolveItemHandler(EntityPlayerMP player, WorldServer level,
            BlockPos pos, boolean sameDimension) {
        return sameDimension
                ? RtsLinkedCapabilities.findLinkedItemHandler(player, pos)
                : RtsLinkedCapabilities.findHandlerInLevel(level, pos);
    }

    private static UUID readBackpackUuid(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return null;
        }
        TileEntity blockEntity = level.getTileEntity(pos);
        return RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
    }

    private static List<LinkedHandler> orderedHandlers(List<LinkedHandler> handlers, Comparator<LinkedHandler> comparator) {
        if (handlers == null || handlers.size() <= 1) {
            return handlers == null ? java.util.Collections.<LinkedHandler>emptyList() : handlers;
        }
        List<LinkedHandler> ordered = new ArrayList<>(handlers);
        ordered.sort(comparator);
        return ordered;
    }

    private static List<IItemHandler> toItemHandlers(List<LinkedHandler> handlers, boolean requireStorePermission) {
        if (handlers == null || handlers.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<IItemHandler> out = new ArrayList<>(handlers.size());
        for (LinkedHandler linked : handlers) {
            if (linked == null || linked.handler() == null) continue;
            if (requireStorePermission && !linked.allowStore()) continue;
            out.add(linked.handler());
        }
        return out;
    }

    private static List<LinkedFluidHandler> orderedFluidHandlers(List<LinkedFluidHandler> handlers,
            Comparator<LinkedFluidHandler> comparator) {
        if (handlers == null || handlers.size() <= 1) {
            return handlers == null ? java.util.Collections.<LinkedFluidHandler>emptyList() : handlers;
        }
        List<LinkedFluidHandler> ordered = new ArrayList<>(handlers);
        ordered.sort(comparator);
        return ordered;
    }
}
