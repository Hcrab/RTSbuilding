package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
 * Resolves the linked-storage edge of an {@link RtsStorageSession}.
 *
 * <p>This class is responsible for turning the session's linked refs into
 * item/fluid handlers, allow-store permissions, display names, and storage
 * summaries. It deliberately does not build pages, mutate inventories, craft,
 * transfer fluids, perform remote mining, read or write NBT, or send packets.
 * Those gameplay and transport flows remain owned by the service/API layer.
 *
 * <p>The resolver must preserve the existing AE2 network handler behavior,
 * normal block-container capability probing, and NeoForge capability lookup
 * order. It is also the dependency boundary future Transfer, Fluid, and Craft
 * extractions should call instead of reaching back into the full storage
 * manager.
 */
public final class RtsLinkedStorageResolver {
    public static final byte LINK_MODE_BIDIRECTIONAL = C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;

    private RtsLinkedStorageResolver() {
    }

    /**
     * Linked labels are cached presentation for refs, so resolver owns the
     * fallback block-name lookup used by summaries and UI payloads.
     */
    public static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    /**
     * Resolves every currently accessible item endpoint, including BD network
     * fallback, into handlers that already enforce extract-only store rules.
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        sanitizeSessionDimension(player, session);
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
                String name = session.linkedNames.computeIfAbsent(ref, ignored -> resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !isExtractOnlyLink(session, ref);
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
                        // Handler exists but stale ??refresh its internal cache
                        // in-place to avoid an unmount/mount cycle.
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

    /**
     * Resolves fluid endpoints alongside item endpoints so extract-only links
     * cannot accept stored fluid while still allowing extraction.
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        sanitizeSessionDimension(player, session);
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
                String name = session.linkedNames.computeIfAbsent(ref, ignored -> resolveDisplayName(player.serverLevel(), pos));
                boolean allowStore = !isExtractOnlyLink(session, ref);
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
            String bdName = session.cachedBdName != null ? session.cachedBdName : RtsBdCompat.getNetworkDisplayName(player);
            LinkedStorageRef bdRef = new LinkedStorageRef(
                    player.serverLevel().dimension(),
                    BlockPos.ZERO);
            out.add(new LinkedFluidHandler(bdRef, bdName, session.cachedBdFluidHandler, true, 0));
        }

        return out;
    }

    /**
     * Linked refs are world targets, so resolver owns the shared camera, chunk,
     * interaction, and home-radius gate used before resolving them.
     */
    public static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!RtsCameraManager.isWithinActionRange(player, pos)) {
            return false;
        }
        return RtsProgressionManager.canAccessHomeRadius(player, pos);
    }

    /**
     * Storage availability includes normal linked refs and the BD network
     * fallback because both resolve through this boundary.
     */
    public static boolean hasAnyStorage(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return false;
        }
        if (!session.linkedStorages.isEmpty()) {
            return true;
        }
        return session.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player);
    }

    /**
     * The UI summary describes the currently resolvable linked-storage source,
     * so it stays paired with availability checks.
     */
    public static String buildAnyStorageSummary(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return "No Storage";
        }
        if (!session.linkedStorages.isEmpty()) {
            return buildLinkedSummary(session);
        }
        if (session.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player)) {
            return RtsBdCompat.getNetworkDisplayName(player);
        }
        return "No Storage";
    }

    /**
     * Ref cleanup belongs to resolver so every lookup starts from the same
     * valid identity set without touching unrelated session state.
     */
    public static void sanitizeSessionDimension(ServerPlayer player, RtsStorageSession session) {
        if (session == null || session.linkedStorages.isEmpty()) {
            return;
        }
        session.linkedStorages.removeIf(ref -> ref == null || ref.dimension() == null || ref.pos() == null);
        cleanupOrphanRefs(session);
    }

    public static boolean isLinkedRefWorldVisible(ServerPlayer player, RtsStorageSession session, LinkedStorageRef ref) {
        if (player == null || session == null || ref == null || ref.pos() == null
                || !player.serverLevel().dimension().equals(ref.dimension())
                || session.detachedBackpackRefs.contains(ref)
                || !player.serverLevel().hasChunkAt(ref.pos())) {
            return false;
        }
        UUID backpackUuid = session.linkedBackpackUuids.get(ref);
        if (backpackUuid != null) {
            return backpackUuid.equals(readBackpackUuid(player.serverLevel(), ref.pos()));
        }
        return !player.serverLevel().getBlockState(ref.pos()).isAir();
    }

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

    /**
     * Summary text is presentation derived from linked refs and extract-only
     * modes, not page-building state.
     */
    public static String buildLinkedSummary(RtsStorageSession session) {
        int count = session.linkedStorages.size();
        if (count <= 0) {
            return "No Storage";
        }
        if (count == 1) {
            LinkedStorageRef ref = session.linkedStorages.get(0);
            String name = session.linkedNames.getOrDefault(ref, "Linked Storage");
            return isExtractOnlyLink(session, ref) ? name + " [Extract]" : name;
        }
        int extractOnly = 0;
        for (LinkedStorageRef ref : session.linkedStorages) {
            if (isExtractOnlyLink(session, ref)) {
                extractOnly++;
            }
        }
        if (extractOnly <= 0) {
            return count + " linked storages";
        }
        return count + " linked storages (" + extractOnly + " extract-only)";
    }

    /**
     * Link mode normalization is reused by persistence and resolver permission
     * checks so saved data and runtime handlers cannot disagree.
     */
    public static byte sanitizeLinkMode(byte linkMode) {
        return linkMode == LINK_MODE_EXTRACT_ONLY ? LINK_MODE_EXTRACT_ONLY : LINK_MODE_BIDIRECTIONAL;
    }

    /**
     * Extract-only is a linked-ref permission that directly controls the
     * resolver's handler views.
     */
    public static boolean isExtractOnlyLink(RtsStorageSession session, LinkedStorageRef ref) {
        return session != null
                && ref != null
                && sanitizeLinkMode(session.linkedModes.getOrDefault(ref, LINK_MODE_BIDIRECTIONAL)) == LINK_MODE_EXTRACT_ONLY;
    }

    public static int sanitizeLinkedStoragePriority(int priority) {
        return net.minecraft.util.Mth.clamp(priority, -9999, 9999);
    }

    /**
     * Called when a linked storage block is broken. Removes the reference
     * from all affected sessions and refreshes their storage page.
     */
    public static void onLinkedStorageBlockBroken(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (var entry : RtsSessionService.allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (markOrRemoveBrokenLinkedStorageRef(session, level, dimension, pos)) {
                RtsSessionService.saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }
    }

    /**
     * Called when a backpack storage block is placed. Updates all sessions
     * that own the backpack with the new position.
     */
    public static void onLinkedStorageBlockPlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null || !RtsBackpackCompat.isAvailable()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        UUID backpackUuid = RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
        if (backpackUuid == null) {
            return;
        }
        String backpackItemId = RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
        LinkedStorageRef newRef = new LinkedStorageRef(level.dimension(), pos.immutable());
        String displayName = resolveDisplayName(level, pos);
        for (var entry : RtsSessionService.allSessions().entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            if (moveBackpackLinkedStorageRef(session, backpackUuid, backpackItemId, newRef, displayName)) {
                RtsSessionService.saveToPlayerNbt(player, session);
                RtsStorageTickService.INSTANCE.forceRefresh(player);
                session.transfer.pageDataVersion.incrementAndGet();
                RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }
    }

    private static boolean markOrRemoveBrokenLinkedStorageRef(RtsStorageSession session, ServerLevel level,
            ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        if (!session.linkedStorages.contains(ref)) {
            return false;
        }
        UUID backpackUuid = session.linkedBackpackUuids.get(ref);
        if (backpackUuid != null) {
            UUID breakingUuid = level == null ? null : RtsBackpackCompat.getBackpackUuid(level.getBlockEntity(pos)).orElse(null);
            if (!backpackUuid.equals(breakingUuid)) {
                return false;
            }
            return session.detachedBackpackRefs.add(ref);
        }
        return removeLinkedStorageRef(session, dimension, pos);
    }

    private static boolean moveBackpackLinkedStorageRef(RtsStorageSession session, UUID backpackUuid,
            String backpackItemId, LinkedStorageRef newRef, String displayName) {
        if (session == null || backpackUuid == null || newRef == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (LinkedStorageRef oldRef : List.copyOf(session.linkedStorages)) {
            if (!backpackUuid.equals(session.linkedBackpackUuids.get(oldRef))) {
                continue;
            }
            if (oldRef.equals(newRef)) {
                session.detachedBackpackRefs.remove(oldRef);
                session.linkedNames.put(oldRef, displayName);
                if (backpackItemId != null && !backpackItemId.isBlank()) {
                    session.linkedBackpackItemIds.put(oldRef, backpackItemId);
                }
                changed = true;
                continue;
            }
            if (session.linkedStorages.contains(newRef)
                    && !backpackUuid.equals(session.linkedBackpackUuids.get(newRef))) {
                continue;
            }
            byte mode = session.linkedModes.getOrDefault(oldRef, LINK_MODE_BIDIRECTIONAL);
            int priority = session.linkedPriorities.getOrDefault(oldRef, 0);
            int index = session.linkedStorages.indexOf(oldRef);
            if (index < 0) {
                continue;
            }
            if (session.linkedStorages.contains(newRef)) {
                removeLinkedStorageRef(session, oldRef.dimension(), oldRef.pos());
            } else {
                session.linkedStorages.set(index, newRef);
                session.linkedNames.remove(oldRef);
                session.linkedModes.remove(oldRef);
                session.linkedPriorities.remove(oldRef);
                session.linkedBackpackUuids.remove(oldRef);
                session.linkedBackpackItemIds.remove(oldRef);
                session.detachedBackpackRefs.remove(oldRef);
            }
            session.linkedNames.put(newRef, displayName);
            session.linkedModes.put(newRef, mode);
            session.linkedPriorities.put(newRef, sanitizeLinkedStoragePriority(priority));
            session.linkedBackpackUuids.put(newRef, backpackUuid);
            if (backpackItemId != null && !backpackItemId.isBlank()) {
                session.linkedBackpackItemIds.put(newRef, backpackItemId);
            }
            session.detachedBackpackRefs.remove(newRef);
            changed = true;
        }
        return changed;
    }

    private static boolean removeLinkedStorageRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean removed = session.linkedStorages.removeIf(ref ->
                ref != null && dimension.equals(ref.dimension()) && pos.equals(ref.pos()));
        if (removed) {
            cleanupOrphanRefs(session);
        }
        return removed;
    }

    private static int linkedPriority(RtsStorageSession session, LinkedStorageRef ref) {
        return session == null || ref == null
                ? 0
                : sanitizeLinkedStoragePriority(session.linkedPriorities.getOrDefault(ref, 0));
    }

    private static IItemHandler findMatchingBackpackBlockHandler(ServerPlayer player, BlockPos pos, UUID expectedUuid) {
        if (expectedUuid == null || !expectedUuid.equals(readBackpackUuid(player.serverLevel(), pos))) {
            return null;
        }
        return RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
    }

    private static UUID readBackpackUuid(ServerLevel level, BlockPos pos) {
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

    /**
     * Removes orphaned metadata entries whose {@link LinkedStorageRef} is no
     * longer present in {@code session.linkedStorages}. Called after any
     * operation that removes refs from the list.
     */
    private static void cleanupOrphanRefs(RtsStorageSession session) {
        session.linkedNames.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedModes.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedPriorities.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedBackpackUuids.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedBackpackItemIds.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.detachedBackpackRefs.removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
    }
}
