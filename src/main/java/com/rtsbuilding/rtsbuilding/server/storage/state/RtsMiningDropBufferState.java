package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自动存入的有界中间缓存。
 *
 * <p>它接住真实掉落并保留完整 ItemStack 组件；不负责解析储存或发包。缓存故意不写入普通
 * Session NBT，退出服务器前必须由服务层回退到背包或世界，避免复制物品。</p>
 */
public final class RtsMiningDropBufferState {
    public static final int MAX_BUFFERED_ITEMS = 4096;
    public static final int MAX_STACKS = 128;

    public final Deque<ItemStack> stacks = new ArrayDeque<>();
    public int bufferedItems;
    public long firstQueuedGameTime = -1L;
    public boolean fullNoticeSent;

    public int remainingCapacity() {
        return Math.max(0, MAX_BUFFERED_ITEMS - bufferedItems);
    }

    public boolean isFull() {
        return bufferedItems >= MAX_BUFFERED_ITEMS || stacks.size() >= MAX_STACKS;
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public void clearTimingWhenEmpty() {
        if (stacks.isEmpty()) {
            bufferedItems = 0;
            firstQueuedGameTime = -1L;
            fullNoticeSent = false;
        }
    }
}
