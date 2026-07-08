package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 远程菜单与存储页面数据版本状态。
 *
 * <p>它不执行任何传输，只记录当前远程 GUI、任务检测时钟以及页面缓存是否
 * 需要失效。这样 transfer/page 相关状态不再散落在总 session 中。</p>
 */
public class RtsTransferState {

    /** 远程 GUI 菜单的 container id；-1 表示当前没有远程菜单。 */
    public int remoteMenuContainerId = -1;
    /** 远程 GUI 对应的方块坐标。 */
    public BlockPos remoteMenuPos;
    /** 下次检测 RTS 任务或进度的 tick 时间。 */
    public long nextQuestDetectTick;
    /** 客户端存储页是否已经与服务端内容不同步。 */
    public boolean storageViewDirty;
    /** 存储数据版本号；真实存储变动时递增，用于页面缓存失效。 */
    public final AtomicLong pageDataVersion = new AtomicLong();
}
