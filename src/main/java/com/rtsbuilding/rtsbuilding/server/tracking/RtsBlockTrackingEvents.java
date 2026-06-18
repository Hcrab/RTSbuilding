package com.rtsbuilding.rtsbuilding.server.tracking;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsBlockTrackingEvents {
    private RtsBlockTrackingEvents() {
    }

    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedBlockTrackerData.get(serverLevel).mark(event.getPos());
        serverLevel.getServer().execute(() -> RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(serverLevel, event.getPos()));
        // 手动放置方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        ServerPlayer player = (ServerPlayer) event.getEntity();
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            RtsPendingPlacementService.refreshWorkflowProgress(player, session);
        }
    }

    @SubscribeEvent
    public static void onEntityMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(serverLevel);
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            tracker.mark(snapshot.getPos());
            serverLevel.getServer().execute(() -> RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(serverLevel, snapshot.getPos()));
        }
        // 多方块放置后刷新放置工作流进度
        ServerPlayer player = (ServerPlayer) event.getEntity();
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            RtsPendingPlacementService.refreshWorkflowProgress(player, session);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedBlockTrackerData.get(serverLevel).clear(event.getPos());
        RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockBroken(serverLevel, event.getPos());
        // 手动破坏方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent((ServerPlayer) event.getPlayer());
        if (session != null) {
            RtsPendingPlacementService.refreshWorkflowProgress((ServerPlayer) event.getPlayer(), session);
        }
    }
}

