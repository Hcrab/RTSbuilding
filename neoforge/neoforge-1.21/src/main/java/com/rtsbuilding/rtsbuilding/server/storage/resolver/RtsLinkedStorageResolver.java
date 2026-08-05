package com.rtsbuilding.rtsbuilding.server.storage.resolver;

import com.rtsbuilding.rtsbuilding.api.ProtectionRegistry;
import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.api.compat.RtsStorageNetworkProvider;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
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
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

import java.util.List;
import java.util.UUID;
import com.rtsbuilding.rtsbuilding.network.NetworkConstants;

/**
 * Resolves the linked storage edges of an {@link RtsStorageSession}.
 *
 * <p>This class is responsible for converting the session's linked references into item/fluid handlers,
 * allow-store permissions, display names, and storage summaries.
 * It deliberately does not build pages, modify inventories, craft, transfer fluids,
 * perform remote mining, read/write NBT, or send packets.
 * Those gameplay and transfer pipelines remain owned by {@link RtsStorageManager}.
 *
 * <p>The resolver must preserve existing AE2 network handler behavior,
 * normal block container capability probing, and NeoForge capability query order.
 * It is also the dependency boundary for future Transfer, Fluid, and Craft extractions —
 * these modules should call this resolver rather than accessing the full storage manager directly.
 *
 * <p>Handler resolution and sorting have been extracted to {@link RtsLinkedHandlerResolutionService}.
 * Block event lifecycle logic has been extracted to {@link RtsLinkedStorageBlockEventHandler}.
 * This class retains access checks, summary construction, and link mode normalization logic.
 */
public final class RtsLinkedStorageResolver {
    public static final byte LINK_MODE_BIDIRECTIONAL = NetworkConstants.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = NetworkConstants.MODE_EXTRACT_ONLY;

    private RtsLinkedStorageResolver() {
    }

    /**
     * The linked display label is a cached rendering of the reference, so the resolver owns
     * the fallback block name query used by summaries and UI packets.
     */
    public static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    // ======================================================================
    //  Handler resolution (delegated to RtsLinkedHandlerResolutionService)
    // ======================================================================

    /**
     * Resolves all currently accessible item endpoints (including BD network fallback)
     * into handlers with extract-only storage rules enforced.
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedHandlers(player, session);
    }

    /**
     * Resolves both fluid endpoints and item endpoints, ensuring extract-only links
     * cannot accept stored fluids while still allowing extraction.
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedFluidHandlers(player, session);
    }

    // ======================================================================
    //  Item handler extraction helpers (facade for high-frequency callers)
    // ======================================================================

    /**
     * Convenience shortcut: resolves linked handlers and extracts raw {@link IItemHandler} instances,
     * ordered for insertion (high priority first).
     */
    public static List<IItemHandler> itemHandlersForInsert(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForInsert(handlers);
    }

    /**
     * Convenience shortcut: resolves linked handlers and extracts raw {@link IItemHandler} instances,
     * ordered for extraction (low priority first).
     */
    public static List<IItemHandler> itemHandlersForExtract(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForExtract(handlers);
    }

    // ======================================================================
    //  World access / availability / summary
    // ======================================================================

    /**
     * Linked references are world targets, so the resolver owns the
     * shared camera, chunk, and interaction gating used before resolution.
     * <p>
     * Also enforces the bedrock-layer boundary: rejects any position at or below
     * the world's minimum build height (bedrock layer) to prevent RTS operations in the void.
     */
    public static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        // ── Third-party protection plugins (FTB Chunks, GriefPrevention, etc.) ──
        if (!ProtectionRegistry.canBreak(player, pos)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        // ── Bedrock-layer boundary: reject positions below the world floor ──
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!RtsCameraManager.isWithinActionRange(player, pos)) {
            return false;
        }
        return true;
    }

    /**
     * Storage availability includes normal linked references and the BD network fallback,
     * since both are resolved through this boundary.
     */
    public static boolean hasAnyStorage(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return false;
        }
        if (!session.linkedStorageInfo.isEmpty()) {
            return true;
        }
        return session.sessionFlags.useBdNetwork && hasNetworkProvider(player);
    }

    private static boolean hasNetworkProvider(ServerPlayer player) {
        for (var provider : RtsCompatRegistry.getStorageProviders()) {
            if (provider.isAvailable() && provider.getNetworkDisplayName(player) != null) return true;
        }
        return false;
    }

    /**
     * The UI summary describes the currently resolvable linked storage sources,
     * so it stays paired with the availability check.
     */
    public static String buildAnyStorageSummary(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return "No Storage";
        }
        if (!session.linkedStorageInfo.isEmpty()) {
            return buildLinkedSummary(session);
        }
        if (session.sessionFlags.useBdNetwork) {
            for (var provider : RtsCompatRegistry.getStorageProviders()) {
                if (provider.isAvailable()) {
                    String name = provider.getNetworkDisplayName(player);
                    if (name != null) return name;
                }
            }
        }
        return "No Storage";
    }

    /**
     * The summary text is a rendering derived from linked references and extract-only mode,
     * not page build state.
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
    //  Session dimension / visibility / sorting
    // ======================================================================

    /**
     * Reference sanitization belongs to the resolver so that every query starts from the same set of valid identities,
     * without touching unrelated session state.
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
     * Link mode normalization is reused by persistence and resolver permission checks,
     * ensuring saved data and runtime handlers do not become inconsistent.
     */
    public static byte sanitizeLinkMode(byte linkMode) {
        return linkMode == LINK_MODE_EXTRACT_ONLY ? LINK_MODE_EXTRACT_ONLY : LINK_MODE_BIDIRECTIONAL;
    }

    /**
     * Extract-only is a linked reference permission that directly controls the resolver's handler view.
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
