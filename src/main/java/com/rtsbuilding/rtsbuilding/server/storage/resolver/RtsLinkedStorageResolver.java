package com.rtsbuilding.rtsbuilding.server.storage.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the linked-storage edge of an {@link RtsStorageSession}.
 *
 * <p>This class is responsible for turning the session's linked refs into
 * item/fluid handlers, allow-store permissions, display names, and storage
 * summaries. It deliberately does not build pages, mutate inventories, craft,
 * transfer fluids, perform remote mining, read or write NBT, or send packets.
 * Those gameplay and transport flows remain owned by {@link RtsStorageManager}.
 *
 * <p>The resolver must preserve the existing AE2 network handler behavior,
 * normal block-container capability probing, and NeoForge capability lookup
 * order. It is also the dependency boundary future Transfer, Fluid, and Craft
 * extractions should call instead of reaching back into the full storage
 * manager.
 *
 * <p>Handler resolution and ordering have been extracted to
 * {@link RtsLinkedHandlerResolutionService}. Block-event lifecycle logic
 * has been extracted to {@link RtsLinkedStorageBlockEventHandler}.
 * This class retains access-check, summary-building, and link-mode
 * normalization logic.
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

    // ======================================================================
    //  Handler resolution (delegated to RtsLinkedHandlerResolutionService)
    // ======================================================================

    /**
     * Resolves every currently accessible item endpoint, including BD network
     * fallback, into handlers that already enforce extract-only store rules.
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedHandlers(player, session);
    }

    /**
     * Resolves fluid endpoints alongside item endpoints so extract-only links
     * cannot accept stored fluid while still allowing extraction.
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedFluidHandlers(player, session);
    }

    // ======================================================================
    //  Item-handler extraction helpers (Facade for high-volume callers)
    // ======================================================================

    /**
     * Convenience shortcut: resolves linked handlers and extracts only the
     * raw {@link IItemHandler} instances, ordered for insert (high priority
     * first).
     */
    public static List<IItemHandler> itemHandlersForInsert(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForInsert(handlers);
    }

    /**
     * Convenience shortcut: resolves linked handlers and extracts only the
     * raw {@link IItemHandler} instances, ordered for extract (low priority
     * first).
     */
    public static List<IItemHandler> itemHandlersForExtract(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForExtract(handlers);
    }

    // ======================================================================
    //  World access / availability / summary
    // ======================================================================

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
        if (!session.linkedStorageInfo.isEmpty()) {
            return true;
        }
        return session.sessionFlags.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player);
    }

    /**
     * The UI summary describes the currently resolvable linked-storage source,
     * so it stays paired with availability checks.
     */
    public static String buildAnyStorageSummary(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return "No Storage";
        }
        if (!session.linkedStorageInfo.isEmpty()) {
            return buildLinkedSummary(session);
        }
        if (session.sessionFlags.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player)) {
            return RtsBdCompat.getNetworkDisplayName(player);
        }
        return "No Storage";
    }

    /**
     * Summary text is presentation derived from linked refs and extract-only
     * modes, not page-building state.
     */
    public static String buildLinkedSummary(RtsStorageSession session) {
        int count = session.linkedStorageInfo.size();
        if (count <= 0) {
            return "No Storage";
        }
        if (count == 1) {
            LinkedStorageRef ref = session.linkedStorageInfo.get(0);
            String name = session.linkedStorageInfo.getNameOrDefault(ref, "Linked Storage");
            return isExtractOnlyLink(session, ref) ? name + " [Extract]" : name;
        }
        int extractOnly = 0;
        for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
            if (isExtractOnlyLink(session, ref)) {
                extractOnly++;
            }
        }
        if (extractOnly <= 0) {
            return count + " linked storages";
        }
        return count + " linked storages (" + extractOnly + " extract-only)";
    }

    // ======================================================================
    //  Session dimension / visibility / ordering
    // ======================================================================

    /**
     * Ref cleanup belongs to resolver so every lookup starts from the same
     * valid identity set without touching unrelated session state.
     */
    public static void sanitizeSessionDimension(ServerPlayer player, RtsStorageSession session) {
        if (session == null || session.linkedStorageInfo.isEmpty()) {
            return;
        }
        session.linkedStorageInfo.removeIf(ref -> ref == null || ref.dimension() == null || ref.pos() == null);
        session.linkedStorageInfo.cleanupOrphans();
    }

    public static boolean isLinkedRefWorldVisible(ServerPlayer player, RtsStorageSession session, LinkedStorageRef ref) {
        if (player == null || session == null || ref == null || ref.pos() == null
                || !player.serverLevel().dimension().equals(ref.dimension())
                || session.linkedStorageInfo.isDetached(ref)
                || !player.serverLevel().hasChunkAt(ref.pos())) {
            return false;
        }
        UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
        if (backpackUuid != null) {
            return backpackUuid.equals(RtsLinkedStorageBlockEventHandler.readBackpackUuid(player.serverLevel(), ref.pos()));
        }
        return !player.serverLevel().getBlockState(ref.pos()).isAir();
    }

    // ======================================================================
    //  Link mode normalization
    // ======================================================================

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
                && sanitizeLinkMode(session.linkedStorageInfo.getMode(ref)) == LINK_MODE_EXTRACT_ONLY;
    }

    public static int sanitizeLinkedStoragePriority(int priority) {
        return net.minecraft.util.Mth.clamp(priority, -9999, 9999);
    }

}
