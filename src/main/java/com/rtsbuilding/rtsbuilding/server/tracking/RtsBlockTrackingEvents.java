package com.rtsbuilding.rtsbuilding.server.tracking;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import java.util.Collection;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 方块放置/破坏追踪入口。放置成功由 BlockItem Mixin 提供精确变化集合。 */
public final class RtsBlockTrackingEvents {
    private static boolean initialized;

    private RtsBlockTrackingEvents() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                onBroken(serverPlayer, serverLevel, pos);
            }
        });
    }

    public static void onPlaced(
            ServerPlayer player, ServerLevel level, Collection<BlockPos> changedPositions) {
        if (player == null || level == null || changedPositions == null || changedPositions.isEmpty()) {
            return;
        }
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        for (BlockPos pos : changedPositions) {
            if (pos == null || level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            tracker.mark(immutable);
            level.getServer().execute(() ->
                    RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(level, immutable));
        }
        refreshProgress(player);
    }

    private static void onBroken(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PlacedBlockTrackerData.get(level).clear(pos);
        RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockBroken(level, pos);
        refreshProgress(player);
    }

    private static void refreshProgress(ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            RtsProgressRefresher.refreshWorkflowProgress(player, session);
        }
    }
}
