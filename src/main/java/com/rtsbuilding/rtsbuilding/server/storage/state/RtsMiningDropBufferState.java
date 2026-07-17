package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自动存入的有界中间缓存。
 *
 * <p>同步生成的挖掘掉落先快速进入这里，再由 Tick 末限量写入 AE/RS 或普通库存。
 * 它不拥有挖掘工作流生命周期，但在物品写出前是唯一的短期所有者。</p>
 */
public final class RtsMiningDropBufferState {
    public static final int MAX_BUFFERED_ITEMS = RtsMiningDropBufferPolicy.MAX_BUFFERED_ITEMS;
    public static final int MAX_STACKS = RtsMiningDropBufferPolicy.MAX_STACKS;

    public final Deque<ItemStack> stacks = new ArrayDeque<>();
    public int bufferedItems;
    /** 连续一次真实储存写入零进度的起始 Tick；排队和正常写入时间不计入三秒回退。 */
    public long firstQueuedGameTime = -1L;
    public boolean fullNoticeSent;
    private long lastFallbackNoticeGameTime = -1L;
    public int remainingCapacity() {
        return RtsMiningDropBufferPolicy.remainingCapacity(bufferedItems);
    }

    public boolean isFull() {
        return RtsMiningDropBufferPolicy.isFull(bufferedItems, stacks.size());
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public void markStorageBlocked(long gameTime) {
        if (firstQueuedGameTime < 0L) firstQueuedGameTime = gameTime;
    }

    public void markStorageProgress() {
        firstQueuedGameTime = -1L;
    }

    public boolean fallbackEligible(long gameTime, long timeoutTicks) {
        return firstQueuedGameTime >= 0L && gameTime >= firstQueuedGameTime
                && gameTime - firstQueuedGameTime >= Math.max(0L, timeoutTicks);
    }

    /** 多个 durable 缓存任务同时回退时，每位玩家只显示一条合并提示。 */
    public boolean shouldSendFallbackNotice(long gameTime, long intervalTicks) {
        if (lastFallbackNoticeGameTime >= 0L && gameTime >= lastFallbackNoticeGameTime
                && gameTime - lastFallbackNoticeGameTime < intervalTicks) {
            return false;
        }
        lastFallbackNoticeGameTime = gameTime;
        return true;
    }

    public void clearTimingWhenEmpty() {
        if (stacks.isEmpty()) {
            bufferedItems = 0;
            firstQueuedGameTime = -1L;
            fullNoticeSent = false;
        }
    }
}
