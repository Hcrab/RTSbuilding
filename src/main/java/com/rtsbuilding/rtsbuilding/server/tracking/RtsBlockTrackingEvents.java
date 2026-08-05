package com.rtsbuilding.rtsbuilding.server.tracking;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 方块放置/破坏追踪事件处理器。<br>
 * 监听服务器的方块放置和破坏事件，同步更新 {@link PlacedBlockTrackerData} 中的追踪数据，<br>
 * 同时联动 {@link RtsLinkedStorageBlockEventHandler} 处理连锁存储容器的逻辑，<br>
 * 并刷新当前玩家的放置工作流进度（进度条显示和剩余方块数计算）。
 */
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
    public void onEntityPlace(BlockEvent.PlaceEvent event) {
        // MultiPlaceEvent 继承 PlaceEvent；交给下面的批量处理器，避免同一批方块重复刷新。
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        if (!(event.world instanceof WorldServer)) {
            return;
        }
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final WorldServer serverLevel = (WorldServer) event.world;
        final com.rtsbuilding.rtsbuilding.platform.math.BlockPos placedPos =
                new com.rtsbuilding.rtsbuilding.platform.math.BlockPos(event.x, event.y, event.z);
        PlacedBlockTrackerData.get(serverLevel).mark(placedPos);
        com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(
                () -> RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(serverLevel, placedPos));
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
    public void onEntityMultiPlace(BlockEvent.MultiPlaceEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        if (!(event.world instanceof WorldServer)) {
            return;
        }
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final WorldServer serverLevel = (WorldServer) event.world;
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(serverLevel);
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            final com.rtsbuilding.rtsbuilding.platform.math.BlockPos placedPos =
                    new com.rtsbuilding.rtsbuilding.platform.math.BlockPos(
                            snapshot.x, snapshot.y, snapshot.z);
            tracker.mark(placedPos);
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(
                    () -> RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockPlaced(serverLevel, placedPos));
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
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        if (!(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer serverLevel = (WorldServer) event.world;
        com.rtsbuilding.rtsbuilding.platform.math.BlockPos brokenPos =
                new com.rtsbuilding.rtsbuilding.platform.math.BlockPos(event.x, event.y, event.z);
        PlacedBlockTrackerData.get(serverLevel).clear(brokenPos);
        RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockBroken(serverLevel, brokenPos);
        // 手动破坏方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent((EntityPlayerMP) event.getPlayer());
        if (session != null) {
            RtsProgressRefresher.refreshWorkflowProgress((EntityPlayerMP) event.getPlayer(), session);
        }
    }
}

