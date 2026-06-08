package com.rtsbuilding.rtsbuilding.server.tracking;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;

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
        serverLevel.getServer().execute(() -> RtsStorageManager.onLinkedStorageBlockPlaced(serverLevel, event.getPos()));
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
            serverLevel.getServer().execute(() -> RtsStorageManager.onLinkedStorageBlockPlaced(serverLevel, snapshot.getPos()));
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
        RtsStorageManager.onLinkedStorageBlockBroken(serverLevel, event.getPos());
    }
}

