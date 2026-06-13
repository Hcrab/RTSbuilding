package com.rtsbuilding.rtsbuilding.server.service.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolves linked-storage refs into live item/fluid handlers, and registers
 * them with the tick-service cache for fast slot-based reads.
 *
 * <p>Extracted from {@link RtsLinkedStorageResolver} to isolate handler
 * resolution and ordering from access-check and summary-building concerns.
 * This class handles BD network integration, backpack-capability matching,
 * and extract-only view wrapping.
 */
public final class RtsLinkedHandlerResolutionService {

    private RtsLinkedHandlerResolutionService() {
    }

    // ======================================================================
    //  Item handler resolution
    // ======================================================================

    /**
     * Resolves every currently accessible item endpoint, including BD network
     * fallback, into handlers that already enforce extract-only store rules.
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> out = new ArrayList<>();

        if (!session.linkedStorages.isEmpty()) {
            ResourceKey<Level> currentDimension = player.serverLevel().dimension();
            for (LinkedStorageRef ref : session.linkedStorages) {
                if (ref == null || ref.pos() == null) {
                    continue;
                }
                BlockPos pos = ref.pos();
                UUID backpackUuid = session.linkedBackpackUuids.get(ref);
                boolean backpackLink = backpackUuid != null;
                boolean sameDimension = currentDimension.equals(ref.dimension());
                IItemHandler handler = null;

                if (sameDimension && !session.detachedBackpackRefs.contains(ref)
                        && RtsProgressionManager.canAccessHomeRadius(player, pos)
                        && player.serverLevel().hasChunkAt(pos)) {
                    handler = backpackLink
                            ? findMatchingBackpackBlockHandler(player, pos, backpackUuid)
                            : RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
                }

                if (handler == null && backpackLink) {
                    handler = RtsBackpackCompat.openBackpack(backpackUuid, session.linkedBackpackItemIds.get(ref), player)
                            .orElse(null);
                }

                if (handler == null) {
                    continue;
                }
                String name = session.linkedNames.computeIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
                out.add(new LinkedHandler(ref, name, new LinkedItemHandlerView(handler, allowStore), allowStore,
                        linkedPriority(session, ref)));
            }
        }

        if (session.useBdNetwork) {
            if (session.bdHandlerStale || session.cachedBdHandler == null) {
                if (RtsBdCompat.hasPrimaryNetwork(player)) {
                    if (session.cachedBdHandler == null) {
                        session.cachedBdHandler = RtsBdCompat.createNetworkItemHandler(player);
                    } else {
                        RtsBdCompat.refreshNetworkHandler(session.cachedBdHandler);
                    }
                    session.cachedBdName = RtsBdCompat.getNetworkDisplayName(player);
                } else {
                    session.cachedBdHandler = null;
                    session.cachedBdFluidHandler = null;
                }
                session.bdHandlerStale = false;
            }
        }
        if (session.cachedBdHandler != null) {
            LinkedStorageRef bdRef = new LinkedStorageRef(
                    player.serverLevel().dimension(),
                    BlockPos.ZERO);
            out.add(new LinkedHandler(bdRef, session.cachedBdName, session.cachedBdHandler, true, 0));
        }

        return out;
    }

    /**
     * Registers the raw (unwrapped) item handlers from resolved linked handlers
     * with the {@link RtsStorageTickService} cache system, so that subsequent
     * page builds and transfer operations can read from the slot cache instead
     * of calling {@code getStackInSlot()} on every handler on every operation.
     *
     * <p>Call this after {@link #resolveLinkedHandlers(ServerPlayer, RtsStorageSession)}
     * to seed the per-player aggregate storage.
     */
    public static void registerStorageCaches(ServerPlayer player, List<LinkedHandler> handlers) {
        if (player == null || handlers == null || handlers.isEmpty()) {
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
     * Resolves fluid endpoints alongside item endpoints so extract-only links
     * cannot accept stored fluid while still allowing extraction.
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> out = new ArrayList<>();

        if (!session.linkedStorages.isEmpty()) {
            ResourceKey<Level> currentDimension = player.serverLevel().dimension();
            for (LinkedStorageRef ref : session.linkedStorages) {
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
                String name = session.linkedNames.computeIfAbsent(ref,
                        ignored -> RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
                out.add(new LinkedFluidHandler(ref, name, new LinkedFluidHandlerView(handler, allowStore), allowStore,
                        linkedPriority(session, ref)));
            }
        }

        if (session.useBdNetwork) {
            if (session.bdFluidHandlerStale || session.cachedBdFluidHandler == null) {
                if (RtsBdCompat.hasPrimaryNetwork(player)) {
                    session.cachedBdFluidHandler = RtsBdCompat.createNetworkFluidHandler(player);
                } else {
                    session.cachedBdFluidHandler = null;
                }
                session.bdFluidHandlerStale = false;
            }
        }
        if (session.cachedBdFluidHandler != null) {
            String bdName = session.cachedBdName != null
                    ? session.cachedBdName
                    : RtsBdCompat.getNetworkDisplayName(player);
            LinkedStorageRef bdRef = new LinkedStorageRef(
                    player.serverLevel().dimension(),
                    BlockPos.ZERO);
            out.add(new LinkedFluidHandler(bdRef, bdName, session.cachedBdFluidHandler, true, 0));
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
                        session.linkedPriorities.getOrDefault(ref, 0));
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
