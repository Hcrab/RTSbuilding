package com.rtsbuilding.rtsbuilding.server.tracking;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * 方块放置/破坏追踪事件处理器。<br>
 * 监听服务器的方块放置和破坏事件，同步更新 {@link PlacedBlockTrackerData} 中的追踪数据，<br>
 * 同时联动 {@link RtsLinkedStorageBlockEventHandler} 处理连锁存储容器的逻辑，<br>
 * 并刷新当前玩家的放置工作流进度（进度条显示和剩余方块数计算）。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsBlockTrackingEvents {
    private RtsBlockTrackingEvents() {
    }

    /**
     * 处理单个方块手动放置事件。<br>
     * 将放置位置标记为已放置，触发连锁存储容器的放置逻辑，<br>
     * 然后刷新当前玩家的放置工作流进度。
     *
     * @param event 方块放置事件
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedBlockTrackerData.get(serverLevel).mark(event.getPos());
        serverLevel.getServer().execute(() -> RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(serverLevel, event.getPos()));
        // 手动放置方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            RtsProgressRefresher.refreshWorkflowProgress(player, session);
        }
    }

    /**
     * 处理多方块（如树苗生长、门放置等）手动放置事件。<br>
     * 遍历所有被替换的方块快照，逐一标记已放置并触发连锁存储逻辑，<br>
     * 最后刷新当前玩家的放置工作流进度。
     *
     * @param event 多方块放置事件
     */
    @SubscribeEvent
    public static void onEntityMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
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
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            RtsProgressRefresher.refreshWorkflowProgress(player, session);
        }
    }

    /**
     * 处理手动破坏方块事件。<br>
     * 清除追踪数据中该位置的记录，触发连锁存储容器的破坏逻辑，<br>
     * 并刷新当前玩家的放置工作流进度。
     *
     * @param event 方块破坏事件
     */
    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
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
            RtsProgressRefresher.refreshWorkflowProgress((ServerPlayer) event.getPlayer(), session);
        }
    }
}

